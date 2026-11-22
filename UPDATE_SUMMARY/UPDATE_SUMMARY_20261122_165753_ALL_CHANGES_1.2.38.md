feat: 完成播放設定、長按倍率與 Root 自動鎖屏優化（1.2.38）

## 提交範圍

本次將目前全部未提交的程式、資源、測試、文件及歷次更新摘要合併提交。基準為 master 的 7b33019；涵蓋 1.2.25～1.2.38 累積工作，最終 appVersionName 為 1.2.38、versionCode 為 1002038。既有 1.2.24 的每 App 獨立倍率功能延續保留，這次擴充設定操作與可靠性。

提交前共 57 個待提交檔案；新增本彙整摘要及守護腳本換行屬性規則後共 59 個檔案（18 個修改、41 個新增）。摘要全文同時作為本次 commit 說明。

## 設定介面、資料與備份

- 擴充既有應用清單：搜尋搭配「全部支援／已安裝／獨立設定」篩選，顯示清單及覆寫計數，還原畫面狀態後重新套用篩選。
- 全域與每 App 編輯框提供 1.0／1.5／2.0／2.5／3.0x 快捷填入；填入後仍須儲存。
- 新增確認後批次恢復跟隨全域，只刪除支援 App 的獨立倍率鍵，保留全域倍率、啟用開關及其他資料。
- 新增每 App 調速啟用旗標 enabled.app.*，停用時保留倍率；旗標變更需重新啟動目標 App 才切換 Hook 安裝，舊資料預設啟用。
- SharedPreferences、檔案可讀性及備份 I/O 透過單一背景佇列執行，避免主執行緒保存與重複送出；保存完成才回報成功，已結束畫面不更新舊 UI。
- 以系統文件介面匯出／匯入 UTF-8 version=1 備份，支援全域倍率、獨立倍率、啟用旗標與可選鎖屏設定，不新增檔案存取權限。
- 匯入先完整驗證及預覽，提供合併／替換；拒絕未知鍵或包名、重複鍵、非法值與超過 64KiB 的資料，取消不寫入。Root 授權狀態不從公開備份匯入。
- SpeedSettings 增加型別錯誤回退，有效獨立值直接返回，包名正則預先編譯；沿用原 speed.xml、speed、speed.app.*、0.25～4.0x 範圍及 1.5x fallback，無需清除或遷移使用者資料。

## 播放器生命週期與設定跟隨

- 抖音使用當次 resume 實例；父層回呼只解析唯一所屬播放器，忽略 static、支援繼承欄位及同一實例的別名，歧義或無目標時記錄並跳過，避免抓住首次 prepare 或預載播放器。
- 新增 CurrentPlayer 與 TaskGeneration，延遲任務使用弱引用及代次；新手勢、換來源、Surface detach／重綁、release 或頁面離開使舊任務失效。
- Bilibili 依 resume 與 float setter 特徵辨識播放器，不再固定第一欄位；設速例外保留診斷並避免主動中斷宿主播放。
- 微信播放器手動調速保護改用弱鍵與 elapsedRealtime，保留三秒期限；移除無用途的速度強引用表，Finder 延遲補設隨 Activity 生命週期失效。
- 設定讀取以單次快照比較新倍率，保留原生手動值與下一片讀新設定的既有策略；沒有增加延遲設定生效的時間快取。
- Reader 適配保留影片 Surface 判斷，避免將純聽書音訊誤當影片，並保留準備、渲染回呼與 Surface 延遲補設。

## 抖音、Kylin／Phoenix 長按放手修復

- 真機日誌顯示 Kylin 約在按住 303～305ms 發出 2x，原先 350ms 門檻會誤認為永久手動選速；抖音原先也可能將放手回到 1x 記成永久手動值。
- LongPressSpeedState 將按住期間的原生 2x 與一般選單選速分開，不再依賴 350ms 門檻；移除放手後僅一秒的保護限制，涵蓋延遲原生重設。
- WindowInputHook 共用視窗輸入入口，觀察觸控、不改動或保存事件；觀察者錯誤互相隔離，忽略按鍵、懸停與外部事件。框架入口不可用時保留 Activity／Dialog 備援。
- LongPressGestureHook 補上自訂 Activity 方法、事件時間去重及按住時切背景的恢復處理。
- 抖音補上 SimplifyPlayerImpl／SimplifyAsyncPlayer 設速入口，放手主動恢復選定倍率；Reader 以弱播放器鍵隔離暫時狀態，換媒體與釋放時清理，保留真正手動倍率。
- DouyinPlaybackProbe 與 Reader 採真實播放進度／單調時間作短樣本診斷，不以「有呼叫設速」代替實際播放驗證。

## 無觸控自動鎖屏與程序恢復

- 新增全域 1～240 整數分鐘的無觸控鎖屏，預設關閉。只在實際被 LSP 注入的支援 App 前台計時；觸控後重算、按住期間不鎖屏、離開停止，同 App 內部 Activity 切換保留最後觸控時間。
- 調速開關與無觸控功能分開；停用單一 App 調速不會隱含停用已啟用的鎖屏。
- 最終採 VideoSpeed 自己的 Root 權限執行固定 input keyevent 223；影片 App 不申請 Root。授權以背景 id -u 核對，結果只放在模組私有設定，Root 指令有逾時。
- 鎖屏通道涵蓋具發送者身分的有序廣播、Provider、綁定服務與 Activity 備援；核對 UID、實際包名、支援清單、開關、Root 狀態及請求時效。
- IdleLockRequest 帶可撤銷 Binder 憑證與五秒時效。重新觸控、切背景或重新進入會撤銷尚未執行的請求；Provider、同步 Binder IPC 與備援 Root 工作移至背景，接收端於 Root 指令前再次核對。
- 前台服務使用單一背景佇列處理廣播工作；保留舊注入程序相容路徑。已送進系統執行的熄屏按鍵無法保證撤回。
- 啟用鎖屏時才啟動模組私有目錄中的獨立 Root 守護：每五秒檢查程序，服務每十秒寫心跳，心跳超過二十秒也會要求恢復。
- 守護以 PID 鎖避免重複實例，清理失效 PID；關閉功能、資料目錄移除或明確系統強制停止時退出。沿用 BootReceiver 同步，不新增全域開機腳本，不改 HMA／LSP 作用域。
- 保留歷史裝置管理員／無障礙元件供相容處理；舊無障礙服務連接時會自行停用並撤銷本模組舊管理員，最終鎖屏路徑不以它們作備援。

## 本次提交前重新驗證

- scripts/test_douyin_speed_state.py：24 個 JVM 檢查通過。
- scripts/test_idle_lock.py：124 個 JVM 檢查通過，包含請求撤銷、過期、排隊時觸控取消及非同步備援。
- scripts/test_lifecycle_and_backup.py：44 個 JVM 檢查通過。
- scripts/test_manual_speed_cooldown.py：12 個 JVM 檢查通過。
- scripts/test_reader_video_hook.py：Kylin、Phoenix 各 47 個 JVM 檢查通過。
- scripts/test_speed_config_bridge.py：45 個 JVM 檢查通過。
- scripts/test_speed_config_provider.py：29 個 JVM 檢查通過。
- scripts/test_speed_settings.py：87 個 JVM 檢查，以及 15 個支援包名／標籤／可見性對照通過。
- scripts/test_window_input.py：7 個 JVM 檢查通過。
- scripts/test_idle_recovery.py：12 個真實 shell 行為檢查通過。
- 合計 466 個 JVM 檢查、12 個 shell 檢查及 15 個包名對照檢查。JVM 的 Android／Xposed／Root 基礎環境及 shell 的 Android 指令使用 stub，與下面真機結果分開。
- JDK 21、Gradle 8.9 執行 :app:assembleDebug :app:lintDebug 成功；43 個任務中 42 個沿用快取，Lint 為 0 errors／24 warnings。SDK 維持 compile／target 33、min 26。
- git diff --check 通過；額外核對 core.autocrlf=true 的 checkout 過濾輸出，確認守護腳本仍為 LF。沒有建置簽署 Release APK，也沒有重複操作手機。

## 已完成的真機驗收

- Xiaomi 17 Ultra／Android 16，最終手機版本 1.2.38／1002038；抖音 33.9.0／330901。USB 不穩時沿用先前配對的 TLS ADB。
- 抖音換片、進原生 LandscapeFeedActivity、長按 2x 放手後恢復設定 1.23x；後續播放進度短樣本約 1.2415x／1.2425x，未回落到 1x。
- Kylin 橫屏長按放手恢復 1.36x；35 秒後再次觸控會重新等待完整一分鐘，原到期點後仍亮屏，再於新到期點鎖屏。
- 模組程序先核對 UID 10318 與 cmdline，再終止 PID 20403；約 2.125 秒內以 PID 12513 恢復前台服務，守護恢復記錄增加。
- 抖音 PID 全程維持 20402，未手動重啟目標 App；16:29:32.991 由新模組程序回報鎖屏成功，手機實際進入 Dozing。
- 測試後已恢復全域 1.23x、Kylin 1.36x、自動鎖屏關閉，前台服務及守護停止。兩份原 speed.xml 已校準 su 終端換行轉換後，以 base64 讀回實體位元組核對還原。
- 完整分階段驗收與證據索引保留於既有 UPDATE_SUMMARY；這些紀錄反映各版本當時結果，不將歷史測試當作所有 App 均已在最終版重測。

## 限制與尚未完成項目

- 手機管家 ANR 已有獨立分析：VirusScanJobService 主執行緒等待類別鎖，持鎖者在 GuardProvider SDK 等引擎；沒有證據能歸咎於此次守護。本次沒有修補該系統 App、清除資料或停用防毒。
- 最終版的單次程序終止恢復不等同於所有廠牌、重開機或完整系統強制停止驗收；未重測這些情境。
- 私有視窗輸入入口仍需各 Android／廠商版本驗證；播放進度短樣本受緩衝、切速過渡、跳轉及排程影響，不能宣稱精確到毫秒或長時間效能已量測。
- 本輪未重新驗證指紋／臉部解鎖、微信與 Bilibili 實播、全部原生選速選單、所有尺寸與大字版面。
- 未新增每 App 鎖屏時間、診斷頁或額外選速策略選項。

## 文件與正式輸出

- README.md、TECHNICAL_OVERVIEW.md 更新架構、操作方式、歷史路徑、驗收證據與限制。
- 納入全部十份既有未提交摘要／分析，再加入本彙整摘要；保留歷史失敗、進度及後續補充驗收的脈絡。
- .gitattributes 限定 Android 的 idle_recovery.sh 使用 LF，避免 Windows checkout 轉成 CRLF 後破壞打包的 Root 守護腳本；其他檔案換行策略不變。
- .gitignore 新增 work/idle-lock-phone-*/，手機原始設定備份與除錯證據留本機。沿用既有規則忽略 APK、Gradle／建置快取與日誌，沒有強制加入產物或憑證。
- 本機最終 APK：artifacts/VideoSpeed_1.2.38.apk；SHA256：BD83F5F036146AD6A57DB798A92E6D54F418EA42A588A2861A217D1DDB4D7F04。
- 推送目的為 origin 的 master：https://github.com/de0921188/io.github.MarsGao.speed.git；不推 upstream，不建立 tag 或 GitHub Release。

## 完整檔案清單

- 新增：`.gitattributes`
- 修改：`.gitignore`
- 修改：`README.md`
- 修改：`TECHNICAL_OVERVIEW.md`
- 新增：`UPDATE_SUMMARY/SECURITYCENTER_ANR_ANALYSIS_20261122_125538.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261116_015806_OPTIMIZATION_1.2.25.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261119_023902_LIFECYCLE_BACKUP_1.2.26.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261119_031309_PHONE_TEST_1.2.26.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_114352_IDLE_LOCK_1.2.27.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_122918_ROOT_IDLE_LOCK_1.2.31.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_125538_ROOT_RECOVERY_1.2.32.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_141332_LONG_PRESS_IN_PROGRESS.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_155120_GESTURE_IDLE_RELIABILITY_1.2.38.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_163211_DEVICE_VALIDATION_1.2.38.md`
- 新增：`UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_165753_ALL_CHANGES_1.2.38.md`
- 修改：`app/src/main/AndroidManifest.xml`
- 新增：`app/src/main/java/io/github/MarsGao/speed/CurrentPlayer.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/DouyinPlaybackProbe.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockAccessibility.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockActivity.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockAdmin.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockBootReceiver.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockChannel.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockHook.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockProvider.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockRecovery.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockRequest.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockRoot.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleLockService.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/IdleTimeout.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/LongPressGestureHook.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/LongPressSpeedState.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/MainActivity.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/MainHook.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/ManualSpeedCooldown.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/ReaderVideoHook.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/SpeedBackup.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/SpeedConfigBridge.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/SpeedConfigProvider.java`
- 修改：`app/src/main/java/io/github/MarsGao/speed/SpeedSettings.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/TaskGeneration.java`
- 新增：`app/src/main/java/io/github/MarsGao/speed/WindowInputHook.java`
- 修改：`app/src/main/res/layout/activity_main.xml`
- 新增：`app/src/main/res/raw/idle_recovery.sh`
- 修改：`app/src/main/res/values/arrays.xml`
- 修改：`app/src/main/res/values/strings.xml`
- 新增：`app/src/main/res/xml/idle_lock_accessibility.xml`
- 新增：`app/src/main/res/xml/idle_lock_admin.xml`
- 修改：`gradle.properties`
- 修改：`scripts/test_douyin_speed_state.py`
- 新增：`scripts/test_idle_lock.py`
- 新增：`scripts/test_idle_recovery.py`
- 新增：`scripts/test_lifecycle_and_backup.py`
- 新增：`scripts/test_manual_speed_cooldown.py`
- 修改：`scripts/test_reader_video_hook.py`
- 修改：`scripts/test_speed_config_bridge.py`
- 新增：`scripts/test_speed_config_provider.py`
- 修改：`scripts/test_speed_settings.py`
- 新增：`scripts/test_window_input.py`
