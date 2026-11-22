# 1.2.40 前景服務競態修正、手機管家 ANR 診斷與防毒停用驗證

日期：2026-11-22（Asia/Taipei）。本摘要涵蓋相對上一版 1.2.39 的全部未提交修改，連同三份詳細更新摘要一併提交；以本檔全文作為 commit 說明。

## VideoSpeed 行為修正

- 修正 IdleLockService 的啟停競態：使用者關閉功能後，延遲的 Root 守護啟動請求可能與 stopService 交錯，觸發 ForegroundServiceDidNotStartInTimeException。20:27:14 的實機崩潰日誌呈現服務尚等待升前景時就被帶下。
- 集中通知建立與 startForeground 為 promoteToForeground，每次 onStartCommand 都先升前景，包含既有實例、sticky 重啟與停用後遲到的請求，再檢查設定決定是否停止。
- 關閉功能時，僅對已存在服務送出 startService，交由服務主執行緒根據最新設定處理；未啟動且已停用時不為了停止而建立新服務。
- 使用 stopSelfResult(startId) 保護較新的命令，成功結束 started lifetime 後才移除前景通知；移除 heartbeat callbacks，避免仍被綁定的停用實例留下通知。
- 保留 Root 鎖屏、Binder／廣播驗證、暫停／恢復、睡眠定時器、既有倍速 Hook 與 START_STICKY 恢復。
- 版本由 1.2.39 升至 1.2.40，versionCode 1002040。

## 回歸測試與診斷工具

- scripts/test_idle_service.py 直接執行正式 IdleLockService 類別，使用 Android API stub 驗證遲到／重複啟動、停用、睡眠模式、Root 授權失效、Android 26 外部啟動及通知收尾，共 40 項；已納入 scripts/run_checks.py。
- scripts/test_idle_lock.py 補齊 startService、stopSelfResult、stopForeground 等 API stub，保留舊驗收。
- scripts/diagnostics/ScanLockProbe.java 與 scripts/test_device_securitycenter_lock.py 新增原手機管家 APK 的獨立 ART 控制測試。必須指定手機，先核對固定 APK SHA256；只上傳自己的暫存 dex，finally 清理，不變更作用域、設定或真實掃描工作。
- 正常虛擬連線控制組 SDK prepare 153 ms、回呼代表執行緒 236 ms 結束；延遲連線組原 APK L8.k.x 與 release 同時 BLOCKED，SDK 等待 8001 ms、回呼等待 8002 ms。XposedBridge 不存在檢查通過。
- 隔離測試重現的是原 APK 的鎖行為，沒有把真實 JobService 放進逾時，也不代表單獨排除實際環境的所有第三方影響。

## 手機管家原因與處理

- 實機手機管家 12.8.5-260910.0.1／GuardProvider 3.0.1-20260708.1。原 APK 的 L8.k.b 在全域 monitor 中等待掃描引擎連線；主執行緒開始工作的 factory x 與停止工作的 release 都需要相同 monitor。
- 取得晚間 MIUIScout 訊息歷史：20:13 與 20:24 的 JobServiceEngine$JobHandler 分別執行 8023、8011 ms，在 ANR 擷取前後剛結束，解釋了擷取時 main 已在 nativePollOnce 的情況。與原 APK 的 8000 ms SDK timeout 高度吻合，但晚間未捕捉到阻塞期間的精確 Java 呼叫點，沒有把每次事件都斷言為同一 monitor。
- HyperCeiler 在 GuardProvider 的兩個相關 Hook 有 initDexKit failed 跳過紀錄；沒有僅根據作用域／設定啟用，就認定它已攔截或造成 ANR。未更動 HMA、HyperCeiler 或 LSPosed 作用域。
- 使用者後續明確授權停用防毒。初次於 21:32 同時停用工作元件與引擎，造成 21:33:34 ContentObserver 嘗試向已停用的 VirusScanJobService 排程而崩潰；此為本輪停用方式的缺漏，已說明並於 21:35:08 修正。
- 最終兩個手機管家工作元件均恢復原 DEFAULT（0）：VirusScanJobService、VirusAutoUpdateJobService。只將 GuardProvider 的 server.VirusScanService、manager.SecurityService 設為 DISABLED（2）。
- 防毒與共用引擎的惡意網址掃描停止。這是經授權停用引擎以避開 ANR 的裝置處理；沒有修改原系統 APK 的鎖程式，也未把此設定自動套用到其他手機。
- 已核對兩個引擎的 PackageManager 與 package-restrictions 持久化狀態，服務解析均 No services found，最終服務清單中無兩個引擎；套件本身仍啟用，其他手機管家元件保留。
- 先前待執行的防毒工作 99、210610、210620 已取消；其他儲存最佳化、連線與一般安全工作保留。防毒設定 UI 的未勾選狀態僅作觀察，最終停用以元件與實際工作證據為準，未猜測改寫未知設定 key。

## 裝置驗證與收尾

- 1.2.40 最終 APK 曾實測：停用狀態連續六次 Root 前景服務啟動均能退出；啟用 240 分鐘測試設定後，PID 18944 被殺，4.401 秒後 PID 19548 恢復前景服務。這不單獨區分 Android sticky／綁定與 Root 守護對恢復的貢獻。
- 兩份原 speed.xml 與 lock_runtime 保留／還原逐位元組內容，原自動鎖屏為關閉；守護測試標記移除、服務停止。
- 手機管家最終處理後，透過原 VirusScanJobService 與 job ID 210600 實際執行七次：四次一般工作、三次與原程式相同的 expedited 工作，全部 RESULT_SUCCESS，啟動至 jobFinished 為 12、7、8、8、7、8、7 ms。工作快速結束，不代表完成防毒掃描；未取消／強殺代替完成。
- 主要工作測試 remote PID 17989 保持存活；首頁、省電與電池、應用管理頁面正常載入。只驗證頁面啟動，不等於逐一驗完所有功能。
- 無權限臨時 App 對原設定 URI 發出 notifyChange，設定內容未改；當前未見手機管家對該 URI 的 observer 註冊，因此此部分列為冒煙測試，原工作由上述七次實際排程另行覆蓋。
- 21:35 修正後至 21:52 收尾未新增手機管家崩潰或 ANR；保留 21:33 崩潰與 21:31 舊 ANR 紀錄，未清日誌。未做重開機或長期使用驗證。
- 臨時測試 App 已卸載，所有自有手機 dex／UI 暫存檔移除；螢幕逾時恢復為原 60000 ms。未清除使用者資料。

## 還原工具與交付邊界

scripts/restore_securitycenter_antivirus.ps1 必須指定手機序號，核對原 APK SHA256，預設僅預覽；加 -Apply 才將兩個引擎恢復原 DEFAULT 狀態。已通過實機預覽，未執行實際還原以保留使用者要求的停用。先前工作的原待執行時間不會由腳本還原，重新啟動後須驗證手機管家是否重建週期工作；重新啟用引擎可能再次遇到原鎖問題。

正式 1.2.40 APK 留於本機 artifacts/VideoSpeed_1.2.40.apk（Git 排除），SHA256 ad0c055a9958ed34efd4a8ad505bf52f72e6677d2a375116558d8ff8b0c45d1b，先前已與安裝檔核對一致。手機私人備份、截圖、日誌、系統 APK、反編譯與暫存 dex 均依既有規則不加入版本控制。

## 本次提交前檢查

- 重新執行 python -B scripts/run_checks.py：680 項 JVM 行為檢查、17 個守護 shell 情境、615 項中文／橫屏檢查與 15 個套件標籤／可見性對照全部通過。
- JDK 21／Gradle 8.9，:app:assembleDebug :app:lintDebug 成功；43 項任務中 42 項 UP-TO-DATE，沒有將快取任務描述成重新編譯。既有 Lint 結果為 0 errors／34 warnings。
- 本次 Git 收尾沒有重新操作手機、重跑真機排程或重新啟用防毒；以上裝置證據來自本次修改過程已完成的驗證。
- 已確認 master 與 origin/master 在提交前一致；推送目標為使用者遠端 origin，無 force push。

## 全部變更檔案（12 個）

- UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_205222_FGS_START_RACE_SECURITYCENTER_DIAGNOSIS_1.2.40.md
- UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_211534_SECURITYCENTER_LOCK_REPRODUCTION.md
- UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_215219_SECURITYCENTER_ANTIVIRUS_DISABLED_DEVICE_TEST.md
- UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_220344_FULL_CHANGE_COMMIT_1.2.40.md
- app/src/main/java/io/github/MarsGao/speed/IdleLockService.java
- gradle.properties
- scripts/diagnostics/ScanLockProbe.java
- scripts/restore_securitycenter_antivirus.ps1
- scripts/run_checks.py
- scripts/test_device_securitycenter_lock.py
- scripts/test_idle_lock.py
- scripts/test_idle_service.py
