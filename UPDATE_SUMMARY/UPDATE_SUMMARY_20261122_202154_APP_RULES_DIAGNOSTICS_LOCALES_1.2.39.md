# 1.2.39 診斷、獨立鎖屏、播放策略與繁簡中文

日期：2026-11-22（Asia/Taipei）。基於 46c6337，保留既有功能與使用者設定；本份摘要作為 1.2.39 整批修改的完整 commit 說明。

## 新增功能

- 各支援 App 的無觸控鎖屏可選跟隨全局、停用或指定 1～240 分鐘；全局關閉仍優先停用無觸控鎖屏。倍速開關與鎖屏規則各自獨立。
- 抖音主版、Phoenix、Kylin 新增原生手動選速策略：沿用現有規則、下一支恢復模組倍率、固定模組倍率。預設仍沿用原行為；長按暫時 2x 仍可使用，放手恢復所選倍率。Reader 原本已會在換媒體時清除手動倍率，因此「下一支恢復」在這兩包不額外改變原換集策略。
- 診斷與定時工具頁顯示 Root 授權快取、前台鎖屏服務狀態、程序心跳、恢復嘗試次數、App 版本與各類最近回報；可重新驗證 Root、收合明細、透過系統檔案選擇器匯出。
- 診斷明確區分設定讀取、Hook 套用要求及播放進度樣本；沒有回報不推定 LSP 作用域未勾選，指令成功也不當成實際播放已驗證。回報限長、限頻並驗證系統 UID 與包名；不記錄影片內容、媒體 ID、帳號或 Root 憑證。
- 新增暫停無觸控鎖屏 30 分鐘／恢復；工具頁與前台通知都可操作。暫停截止後重新等待完整無觸控間隔。
- 新增 1～240 分鐘睡眠定時器：觸控不延長，與無觸控開關及暫停分開。只在已注入 Hook 的支援 App 前台執行；離開後到期，回到該 App 前台時執行，不在桌面或其他 App 強制鎖屏。
- 暫停及睡眠截止使用本次開機的單調時間；跨重開機自動失效，不備份這些臨時倒數。新開機操作任何控制不會重新啟用上一輪的睡眠截止。
- 抖音主版、Phoenix、Kylin 新增預設關閉的「僅影片進度增加時計時」試驗選項。弱引用追蹤播放器，每兩秒在前台取樣；未知或停止播放時凍結倒數，恢復播放接續剩餘時間，實際觸控仍重算。它偵測進度變化，不保證區分每一種 seek、預載或浮動播放場景。

## 設定與可靠性

- 保留原 speed、speed.app.*、enabled.app.*、idle.enabled、idle.minutes 鍵及 0.25～4.0x 倍速範圍。
- 新增 idle.app.<package>（0／不存在繼承，-1 停用，1～240 指定分鐘）、policy.app.<package>（0／1／2）、idle.playing.<package>（0／1）。可備份匯入、預覽及驗證；替換匯入清除未列出的各 App 新規則。原「重置獨立速度」仍只重置倍速。
- 暫存控制為 timer.boot、timer.pauseUntil、timer.sleepUntil；Root 授權仍在私有 lock_runtime，診斷在獨立偏好；設定檔可讀性規則未擴大。
- 抖音成功讀到 Provider 後，熱路徑使用短期記憶體快照、背景刷新及 ContentObserver 失效通知；prepare 入口也失效快取。首讀與失敗備援仍可能同步查詢，沒有宣稱完全消除 IPC。Reader 仍優先 XSharedPreferences，其他 App 原讀取優先順序保留。
- Root 守護改為 PID＋elapsedRealtime 心跳，與 shell 的 /proc/uptime 對照；心跳以暫存檔原子替換。即使診斷 IPC 先喚醒模組程序，也能辨識心跳仍屬舊 PID 並恢復服務。
- 守護 protocol=3，舊版本藉停用旗標退出後再切換；不依檔案中 PID 直接發送訊號。保留強制停止退出、停用退出、單一實例及 stale PID 回收。
- 守護日誌約 64 KiB 輪替，保留一份舊檔；恢復命令採模組 UID 推算的 Android user，shell 測試涵蓋非 0 使用者，但未在真實分身驗證。
- 設定頁保留搜尋、篩選、快捷倍率、獨立開關、備份匯入；新增各 App 規則摘要。橫屏使用雙欄，常用控制可捲動，不壓縮應用清單。

## 系統語言

- 設定頁、App 編輯器、備份預覽、通知、工具頁與診斷標籤改用字串資源，補齊 149 個字串。
- 簡體：values 預設、values-zh-rCN、values-b+zh+Hans；繁體：values-zh-rTW、values-b+zh+Hant。Android 按系統語言及字形選擇，沒有固定語言設定。其他語言無匹配資源時保留原簡體預設。
- 中文清單使用本模組翻譯的支援 App 名稱，避免目標 APK 只提供簡體而混入繁體頁；包名、安裝判斷與作用域不變。非中文介面保留查詢到的 App 標籤。
- 診斷回報改固定代碼，於顯示／匯出時套用目前語言；原始技術日誌的類別與方法名仍保留。

## 驗證

- 行為檢查執行入口：python -B scripts/run_checks.py，已接入 GitHub Actions APK 建置前。JVM 模擬測試、真正 shell 行為及 XML 語系檢查分開統計，不能代替真機 IPC 或實播。
- JDK 21／Gradle 8.9，assembleDebug、lintDebug 成功；640 項 JVM 檢查、17 個真正 shell 情境、615 項語系／橫屏 XML 檢查及 15 個包名對照通過。lint：0 errors／34 warnings（含既有公開 IPC、私有 API、圖示及新增 SetTextI18n 提示），未抑制警告。APK SHA256：45D75CFD073361B230BA397A522DC83935C45F5BD672915C7F7D89A438D3472E。
- 真機：Xiaomi 17 Ultra（2512BPNDAG／nezha）、Android 16。抖音 33.9.0（330901）、Phoenix 6.5.1.32（65132）、Kylin 7.2.5.32（72532）。1.2.39／1002039 已覆蓋安裝。
- 真機 Android 資源解析測試：zh-TW、zh-HK、zh-Hant、zh-CN、zh-SG、zh-Hans，149 × 6＝894 個字串與預期完全一致，未修改手機系統語言。
- 抖音原生 LandscapeFeedActivity：長按 2x 放手恢復 1.23x，短樣本約 1.23～1.26x；同 PID 修改為 1.8x、2.2x 後換片及放手皆跟隨，短樣本約 1.819x、2.25x。屬短樣本，不能推定所有影片或版本。
- Phoenix：1.75x 長按放手恢復，短樣本約 1.756x；換片後仍 1.75x。使用本次原生續播／全屏入口。
- Kylin：原生全集／ShortSeriesLandActivity 長按放手恢復 1.36x，短樣本約 1.356～1.360x；一分鐘無觸控及播放限定計時路徑曾成功鎖屏。
- Root 守護 protocol=3：核對模組 PID／UID／cmdline 後只終止該程序；3913 → 9716，約 2.539 秒恢復前台服務，抖音原 PID 30990 保持不變。接續測暫停及恢復後，20:16:17.475 抖音記錄 locked=true，手機進入 Dozing／Keyguard。
- 抖音暫停期間超過一分鐘仍播放且 Awake；恢復後鎖屏已確認。手機管家 ANR 曾奪取焦點，該期間倒數正確停止，沒有當成鎖屏失敗。
- Phoenix 續播全屏，指定兩分鐘（全局十分鐘），20:19:09 播放／20:21:09.482 locked=true，手機 Dozing／Keyguard；工具頁啟動一分鐘睡眠定時器與暫停、App 編輯器儲存固定倍率均已在真機操作。20:25:32 長按放手後，20:25:54.742 仍按原睡眠截止鎖屏，接收端紀錄 SLEEP_COMMAND_OK，sleepUntil 自動清為 0，無觸控暫停仍保留；未因觸控改為再等一分鐘。

## 手機管家 ANR 補充

- 這次系統事件為 com.miui.securitycenter.remote PID 22039：19:35:01 onStopJob 未回應，20:13:50 onStartJob 未回應。logcat 同一時刻亦記錄 VirusScanJobService #1000/210600 onStartJob 與 onStopJob 超時。
- 20:13:42 系統為手機管家啟動 com.miui.guardprovider/.server.VirusScanService；20:13:50 解除綁定。20:18:04 GuardProvider 的 LOW_MEMORY 退出在本次 ANR 之後，不能倒推為 ANR 的直接原因。
- 與中午已抓到的 L8.k.release／掃描 SDK 鎖等待不同：本次 temp_anr 的 Java 主執行緒已在 nativePollOnce 等訊息，沒有捕捉到相同 monitor 阻塞。只能確認排程回呼逾時，不能聲稱這次已證明同一死鎖或已修好。
- 系統有 12 小時／24 小時等持久化掃描排程；反覆啟動／停止的工作與上述 ANR 一致，但尚未定位完整回呼丟失原因。未停用防毒、未清資料、未改 HMA／LSP 作用域，也未以本模組修補系統手機管家。
- 官方參考：[JobService](https://developer.android.com/reference/android/app/job/JobService)、[ANR 診斷與 nativePollOnce](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)。

## 手機收尾與交付

- 原兩份 speed.xml 與 lock_runtime.xml 皆逐 bytes 核對一致：全局 1.23x、Kylin 獨立 1.36x，其餘原本跟隨全局；測試用 Phoenix 1.75x、固定策略及獨立鎖屏規則已移除。
- 自動鎖屏恢復原本關閉，暫停與睡眠倒數已移除；前台服務停止，Root guard 的 enabled／guard.lock 已不存在。
- 手機螢幕逾時由暫時測試值還原 60000ms；未變更系統語言。三個目標 App 只完整關閉程序以便下次載入原設定，沒有清資料。
- 最後一輪睡眠測試後手機可保持鎖定，無須再次解鎖才能收尾。私人證據保留於 Git 排除的 work/feature-phone-20261122/；APK 與正式摘要在永久目錄。
- 最終已安裝 APK SHA256 與 artifacts/VideoSpeed_1.2.39.apk 一致；本次整批提交涵蓋原始碼、測試、語系及文件；APK 與私人手機證據維持本機排除規則。

## 檔案範圍

- 播放／狀態：MainHook、DouyinSpeedState、ReaderVideoHook、SpeedConfigBridge、新增 PlaybackPresence。
- 設定／備份：MainActivity、SpeedSettings、SpeedBackup、SpeedConfigProvider、新增 Diagnostics、ModuleToolsActivity、TimerControls、TimerActionReceiver。
- 鎖屏／恢復：IdleTimeout、IdleLockHook、IdleLockProvider、IdleLockService、IdleLockRecovery、res/raw/idle_recovery.sh。
- 介面／語言：Manifest、activity_main、item_app_speed、新增 layout-land/activity_main、values 與四個中文語系的 strings／arrays。
- 檢查：run_checks、test_compat、test_feature_rules、test_localization、test_device_locales，並擴充既有 Bridge／Provider／Reader／Idle／Recovery 檢查；CI 增加檢查步驟。
- 文件：README、TECHNICAL_OVERVIEW、本份時間戳摘要、SECURITYCENTER_ANR_FOLLOWUP_20261122_202154.md；gradle.properties 升版。暫存與手機證據排除 Git，最終 APK 留 artifacts/VideoSpeed_1.2.39.apk。

## 保留與未驗證項目

- 保留 15 個支援包名、既有 LSP 作用域、其他 App 原 Hook、Reader 純音訊保護、長按事件觀察、生命週期取消、Root IPC UID 驗證及可撤銷憑證。
- 沒有更改開機全域腳本、Root 管理器政策、HMA、防毒設定、影片 App 資料或帳號；沒有強制關閉手機管家的保護功能。
- 未測其他廠牌、完整重開機、真實分身／工作設定檔、全部支援 App 實播；手機管家 ANR 尚未修復。最新測試生物辨識解鎖未取得明確確認，不以舊版本結果代替。新增手動策略的所有原生選單組合、全程 UI 匯出檔案及所有尺寸尚未逐項實機覆蓋；相關邏輯有 JVM 檢查。

## 本次提交清單

共 50 個檔案；包含全部 Git 可見的未提交修改與兩份更新／ANR 文件。既有排除規則中的 APK、建置產物、測試暫存及手機私人資料不加入 Git。

- `.github/workflows/android-build.yml`
- `.gitignore`
- `README.md`
- `TECHNICAL_OVERVIEW.md`
- `UPDATE_SUMMARY/SECURITYCENTER_ANR_FOLLOWUP_20261122_202154.md`
- `UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_202154_APP_RULES_DIAGNOSTICS_LOCALES_1.2.39.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/MarsGao/speed/Diagnostics.java`
- `app/src/main/java/io/github/MarsGao/speed/DouyinSpeedState.java`
- `app/src/main/java/io/github/MarsGao/speed/IdleLockHook.java`
- `app/src/main/java/io/github/MarsGao/speed/IdleLockProvider.java`
- `app/src/main/java/io/github/MarsGao/speed/IdleLockRecovery.java`
- `app/src/main/java/io/github/MarsGao/speed/IdleLockService.java`
- `app/src/main/java/io/github/MarsGao/speed/IdleTimeout.java`
- `app/src/main/java/io/github/MarsGao/speed/MainActivity.java`
- `app/src/main/java/io/github/MarsGao/speed/MainHook.java`
- `app/src/main/java/io/github/MarsGao/speed/ModuleToolsActivity.java`
- `app/src/main/java/io/github/MarsGao/speed/PlaybackPresence.java`
- `app/src/main/java/io/github/MarsGao/speed/ReaderVideoHook.java`
- `app/src/main/java/io/github/MarsGao/speed/SpeedBackup.java`
- `app/src/main/java/io/github/MarsGao/speed/SpeedConfigBridge.java`
- `app/src/main/java/io/github/MarsGao/speed/SpeedConfigProvider.java`
- `app/src/main/java/io/github/MarsGao/speed/SpeedSettings.java`
- `app/src/main/java/io/github/MarsGao/speed/TimerActionReceiver.java`
- `app/src/main/java/io/github/MarsGao/speed/TimerControls.java`
- `app/src/main/res/layout-land/activity_main.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/item_app_speed.xml`
- `app/src/main/res/raw/idle_recovery.sh`
- `app/src/main/res/values-b+zh+Hans/arrays.xml`
- `app/src/main/res/values-b+zh+Hans/strings.xml`
- `app/src/main/res/values-b+zh+Hant/arrays.xml`
- `app/src/main/res/values-b+zh+Hant/strings.xml`
- `app/src/main/res/values-zh-rCN/arrays.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/arrays.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values/arrays.xml`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`
- `scripts/run_checks.py`
- `scripts/test_compat.py`
- `scripts/test_device_locales.py`
- `scripts/test_feature_rules.py`
- `scripts/test_idle_lock.py`
- `scripts/test_idle_recovery.py`
- `scripts/test_localization.py`
- `scripts/test_reader_video_hook.py`
- `scripts/test_speed_config_bridge.py`
- `scripts/test_speed_config_provider.py`
