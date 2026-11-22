# 1.2.40 前景服務啟停競態修正與手機管家 ANR 深入診斷

日期：2026-11-22（Asia/Taipei）。本輪尚未提交／推送。

## 結果與修復邊界

- 已修正 VideoSpeed IdleLockService 的前景服務啟停競態，建置並安裝 1.2.40（1002040），完成手機上的服務恢復與關閉狀態啟動測試。
- 手機管家的 ANR 仍未宣稱修復。已從手機上實際 APK 確認中午 trace 對應的鎖等待程式路徑；最新晚間 trace 不足以證明每次皆為同一原因。
- 未更換、清除或停用手機管家／GuardProvider；未修改 HMA、HyperCeiler 或 LSPosed 作用域。已提出僅針對 HyperCeiler 兩個相關作用域的暫時對照測試，尚待使用者回覆，不以取消防毒或隱藏 ANR 作為修復。

## VideoSpeed 崩潰證據

使用者提供的 20:27:14.346 堆疊與實機 logcat 一致：ForegroundServiceDidNotStartInTimeException，服務 IdleLockService，caller com.android.shell。

- 20:27:13.008：舊程序 PID 1771 停止。
- 20:27:13.864：Root／shell 送出 start-foreground-service。
- 20:27:13.893：新程序 PID 18281 啟動。
- 20:27:14.280：前景狀態變更。
- 20:27:14.287：ActivityManager 記錄 Bringing down service while still waiting for start foreground。
- 20:27:14.299：AndroidRuntime 拋出例外。

這不是單純等待完整逾時區間後崩潰；日誌顯示啟動尚未完成時服務即被帶下。程式中的 sync(false) 原先直接 stopService，可能與延遲的守護 startForegroundService 交錯；且原實作只在 onCreate 升前景，既有實例的下一次啟動未再次處理前景承諾。以上構成需修正的競態；無法從單一堆疊還原每一個 callback 的確切交錯。

## 程式碼修改

- app/src/main/java/io/github/MarsGao/speed/IdleLockService.java：將通知／升前景集中為 promoteToForeground。每次 onStartCommand（含停用後遲到請求、既有實例與 sticky 重啟）都先升前景，再讀設定或停止。
- 關閉功能時不再直接 stopService；僅對已存在服務送出 startService，由主執行緒的 onStartCommand 根據最新設定結束。未啟動且已停用時不會為了關閉而建立服務。
- 關閉時移除 heartbeat callbacks，使用 stopSelfResult(startId) 保護較新命令；成功結束 started lifetime 後才移除前景通知，處理仍被客戶端綁定的實例。
- 保留 Root 執行鎖屏、Binder／廣播請求驗證、通知暫停／恢復、睡眠定時器及 START_STICKY 恢復。
- scripts/test_idle_service.py：新增執行實際服務類別的 JVM Android stub 測試，涵蓋遲到啟動、連續啟動、停用關閉路徑、睡眠模式、Root 授權失效、Android 26 外部啟動、綁定實例的前景通知收尾，共 40 項。
- scripts/test_idle_lock.py：補齊新增 Android API 的舊測試 stub。
- scripts/run_checks.py：納入服務生命週期回歸測試。
- gradle.properties：版本 1.2.39 → 1.2.40。

## 手機管家原因調查

實機版本：手機管家 12.8.5-260910.0.1（40001285）；GuardProvider 3.0.1-20260708.1（301）；Android 16／API 36。

直接從已安裝路徑讀取 APK，使用官方 jadx 1.5.3 僅反編譯三個診斷類別至任務 RAM 暫存區，不修改 APK：

1. com.miui.antivirus.service.VirusScanJobService.onStopJob 在主執行緒直接呼叫 r() 做清理；r() 呼叫掃描服務 release。
2. L8.k.release 需要取得 k.class 的全域 monitor。
3. L8.k.b(long) 在同一個 k.class monitor 裡呼叫 delegate prepare，長時間等待時沒有釋放這把外層鎖。
4. VirusScanJobService.i 呼叫 prepare 時傳入 8000 毫秒。
5. com.miui.guardprovider.sdk.g.e／k 等待非同步 bindService 的 ServiceConnection。SDK 的 k 對自己的內層鎖呼叫 wait，但不會釋放外層 L8.k.class monitor。
6. 因此主執行緒停止回呼可被背景 prepare 的引擎等待阻塞。中午 12:42:51 trace 中主執行緒 L8.k.release 與背景 SDK g.k 的堆疊，與此程式路徑吻合。

這確認了主執行緒阻塞的具體設計問題；尚未證明永久死鎖，也未證明引擎綁定延遲的唯一上游原因。ServiceConnection 預設主執行緒派送與 onStopJob 等鎖可能形成暫時互相等待，但 prepare 的正值 timeout 限制了單次等待。

晚間最新事件：20:24:38.561 No response to onStartJob、20:24:38.567 No response to onStopJob，均指向 VirusScanJobService／210600。擷取時主執行緒 nativePollOnce，無足夠證據將每次晚間 ANR 都歸為中午的 monitor 等待。

讀取 LSPosed 設定快照確認：只有 HyperCeiler 作用於 com.miui.securitycenter／com.miui.guardprovider；VideoSpeed 作用域不包含這兩包。HyperCeiler 版本 2.10.166-7266aaa0d-r4639，其 GuardProvider Root 檢測功能啟用。比對相同 commit 的官方原始碼，其 GuardProvider hook 是 Root 檢測結果調整；未發現足以直接證明此次掃描 SDK 鎖等待由此引起的證據。作用域存在不等於已證實因果。

20:45:03 透過系統 JobScheduler 手動執行現有 210610 防毒工作（保留原工作與設定），稍後回到 waiting，未看到新增手機管家 ANR。這僅為一次基線觀察，不能宣稱間歇問題消失；也不是 210600 的完整等價重現。

## 驗證

- python -B scripts/run_checks.py：全部通過，共 680 項 JVM 行為檢查（既有 640 + 本次 40）、17 個守護 shell 情境、615 項中文／橫屏資源檢查、15 個套件標籤／可見性對照。
- JDK 21，Gradle 8.9：:app:assembleDebug :app:lintDebug 成功，Lint 0 errors／34 warnings。
- 最初使用預設 Gradle home 時遇到既有 transforms metadata.bin 缺失；改用專案原有 .gradle/reader-home 建置成功，未刪除全域快取或變更全域環境變數。
- 最終 APK：artifacts/VideoSpeed_1.2.40.apk；SHA256 ad0c055a9958ed34efd4a8ad505bf52f72e6677d2a375116558d8ff8b0c45d1b。安裝後讀取 APK 雜湊一致。
- 最終 APK 在功能關閉時連續 6 次 root start-foreground-service，服務均退出，未新增同類崩潰；既有 20:05／20:27 歷史崩潰紀錄保留。
- 最終 APK 暫時啟用 240 分鐘測試設定（避免測試中鎖屏）：服務升前景，連續 4 次啟動命令；僅對已核對 cmdline 的 VideoSpeed PID 18944 發送 kill -9，4.401 秒後新 PID 19548 恢復為前景服務。這證明模組程序死亡後服務恢復，不代表單獨排除了 Android sticky／綁定機制對恢復的貢獻。
- 測試後恢復兩份原 speed.xml 的逐位元組內容及原 UID／GID／權限；lock_runtime.xml 原內容不變。idle 原設定關閉，測試標記移除，守護退出，服務停止。未更動倍速、手機語系、旋轉、螢幕逾時、目標 App 資料，也未在本輪重測影片播放或實際鎖屏。

## 參考與證據

- Android 前景服務診斷：https://developer.android.com/develop/background-work/services/fgs/troubleshooting
- JobService 回呼限制：https://developer.android.com/reference/android/app/job/JobService
- HyperCeiler 同版原始碼：https://github.com/ReChronoRain/HyperCeiler/tree/7266aaa0d
- 私人裝置證據與原設定備份：work\feature-phone-anr-20261122-204620（Git 排除）。多程序原始 trace、系統 APK、反編譯中間檔與 LSPosed 設定快照僅留任務 RAM 暫存區，不加入 Git。
- 本檔為永久更新摘要。尚待 HyperCeiler 局部作用域對照測試的選擇；若問題於乾淨作用域仍可重現，再針對手機管家版本／SDK 做修正方案，不能把 VideoSpeed 的服務修復等同手機管家已修復。

## 後續追查

已新增 [UPDATE_SUMMARY_20261122_211534_SECURITYCENTER_LOCK_REPRODUCTION.md](UPDATE_SUMMARY_20261122_211534_SECURITYCENTER_LOCK_REPRODUCTION.md)。原 APK 無 Xposed 隔離重現約八秒鎖等待，並補到兩次晚間 JobHandler 約八秒的實際執行歷史；目前無須把取消 HyperCeiler 作用域當作繼續調查的前置條件。手機管家修補尚未部署。
