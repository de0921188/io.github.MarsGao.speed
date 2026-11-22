# 1.2.26 播放器生命週期與設定備份更新摘要

時間：2026-11-19 02:39:02（Asia/Taipei）
本輪在 1.2.25 未提交工作之上增量修改；未覆寫或重置前次功能，Git 基準仍為 acce1d4。

## 本輪完成

- 抖音：移除首次 prepare 播放器被類別 Hook 捕獲的問題。resume 使用當次實例；父層回呼依接收者非 static 實例欄位找唯一所屬播放器，支援父類欄位與同一實例的多個別名，歧義／無目標就跳過。沒有以「最近 prepare」猜測目前畫面，避免預載播放器混用。可選 resume Hook 失敗與其他適配路徑隔離。
- 微信：任務代次、弱 Activity 引用與 onPause 失效；新一輪觸控／resume 使舊組任務不再掃描。保留原必要延遲重試時機，仍在回呼執行時讀設定。Finder 全域手動保護改用 elapsedRealtime，保留三秒期限。
- 閱讀 App：250/750ms Surface 補設綁定 token；新來源、detach、重新綁定及 release 使舊 token 失效。rate probe、自訂速度與音訊 Surface 防護保留。
- Bilibili：依 resume/float setter 特徵找欄位，不再固定第一個欄位；找不到或歧義就記錄並跳過。反射設速例外不再主動重新拋出，其他日誌保留。
- 設定讀取：四處 check-then-get 改用單次設定快照，保留手動值；Telegram 兩處日誌重用已讀值。沒有增加設定時間快取、輪詢或延遲更新。
- 背景保存：單一工作佇列執行 commit、檔案可讀性與備份 I/O；保存期間拒絕重複送出，完成後才提示成功。Activity 已銷毀／結束時不再更新舊畫面。
- 每 App 停用調速：獨立啟用旗標 enabled.app.<packageName>（float 0/1），停用保留速度設定，與強制 1.0x 不同。需完整重啟目標 App 才切換 Hook 安裝；舊資料預設啟用。啟動先讀可讀偏好，必要時嘗試 Provider，兩者不可用時兼容為啟用。
- 備份：新增系統文件匯出／匯入，UTF-8 version=1 文本，含全域、獨立速度與啟用旗標；不新增檔案權限。
- 匯入先完整驗證並預覽；支援合併與替換。合併保留未列出項目，替換只移除已支援包名的獨立值與啟用旗標，不 clear 其他配置。取消不寫入。拒絕未知包名／鍵、錯誤版本、非法範圍／開關、重複鍵及超過64KiB資料。
- 編輯對話框新增 ScrollView。既有批次重置僅重置速度，維持全域與開關，已更新確認文案。

## 檔案清單

- 新增 CurrentPlayer.java、TaskGeneration.java、SpeedBackup.java。
- 更新 MainHook.java、ReaderVideoHook.java、SpeedConfigBridge.java、SpeedConfigProvider.java、SpeedSettings.java。
- 更新 MainActivity.java、activity_main.xml、strings.xml、gradle.properties（1.2.26 / 1002026）。
- 新增 scripts/test_lifecycle_and_backup.py、scripts/test_speed_config_provider.py。
- 擴充 scripts/test_speed_config_bridge.py、scripts/test_reader_video_hook.py。
- 更新 README.md、TECHNICAL_OVERVIEW.md、本摘要。
- 前次未提交的 ManualSpeedCooldown、篩選、快捷填入、型別錯誤回退及其測試保持；前次摘要保留。

## 保留／替換／移除／未驗證

- 保留：15 個支援包名、原 speed.xml、speed 全域與 speed.app.* 覆寫鍵、0.25～4.0x、1.5x fallback、閱讀偏好優先與其他 App Provider 優先、一般速度於下一片讀取、手動選速策略、閱讀音訊保護、微信 Finder 尋找與診斷日誌。
- 替換：舊抖音捕獲實例／固定第一欄位推測；延遲任務不再無條件作用到後續頁面或媒體；主執行緒同步保存改背景佇列。
- 移除：已無呼叫的 MainHook hasSpeedConfigChanged 包裝。橋接 hasSpeedChanged 相容 API 與測試仍保留。沒有移除支援包名或已知播放事件適配。
- 未實作：額外的手動速度策略選單、相容性／診斷頁、拆分全部 MainHook；本輪聚焦生命週期、停用開關及備份。
- 未驗證：手機實播、目標 App 實際 Hook 停用／重新啟用、異步重試對真實播放器影響、堆記憶體與效能量測、全尺寸／橫向／大字及旋轉中保存與匯入完整驗收。

## 本機驗證

- SpeedSettings 87、Bridge 45、ManualSpeedCooldown 12、Lifecycle/Backup 44、真實 Provider 類29、Douyin10、兩閱讀包各36，合計299項 JVM 檢查通過。
- Provider 測試執行正式 SpeedConfigProvider.java，Android API 使用 stub；不冒充手機 IPC。
- 新增測試涵蓋兩個所屬播放器交替、直接 resume 實例、歧義/static/繼承欄位；舊延遲失效；備份 round-trip、BOM/CRLF、格式與大小錯誤；啟用設定的隔離與回退。
- :app:assembleDebug :app:lintDebug 成功；lint 0 errors / 18 warnings（1.2.25 為19）。git diff --check 通過。
- 初次新增備份測試的嵌入字串跳脫、Provider fixture 的舊 Resolver 依賴已修正，最終用例通過。

## Android 36 模擬器驗證

明確使用 emulator-5554；該模擬器原未安裝模組，本輪安裝並覆蓋更新最終 APK。手機 nezha 雖有連線，本輪沒有更新或操作手機。

- 新主頁與 App 編輯對話框實際顯示，快捷填入、勾選框及保存可操作。
- 取消編輯：Provider 維持速度1.5、enabled1。
- 保存：Phoenix 3.0、enabled0，全域仍1.5。開關只驗證儲存與實際 Provider IPC，並未模擬 Xposed 安裝行為。
- 系統 ACTION_CREATE_DOCUMENT 匯出成功，檔案含全域1.5、Phoenix3.0、停用0。
- ACTION_OPEN_DOCUMENT 顯示匯入預覽，可取消。
- 合併樣本全域2.2、抖音1.8：Phoenix3.0與停用0保留。
- 同樣本替換：Phoenix變為全域2.2／啟用1；抖音1.8保留。
- 重複 speed 鍵樣本被拒絕，日誌為 Duplicate key: speed，原全域2.2與抖音1.8不變。
- 最終 APK 覆蓋安裝後模擬器原測試值保留；沒有清除其他 App 資料。

## 交付

- artifacts/VideoSpeed_1.2.26.apk
- SHA256：ADBD05DD44461B294E367B873AC0ECB52EC3B79C9B745A29A527FA1AB7D2B7B5
- 本輪未提交、推送、建 tag 或 Release；手機仍保留先前安裝版本，實際版本未於本輪讀取。
- 建議手機驗收：抖音連續換片及重建播放器；Phoenix續播／下一集；微信滑動後立即離頁；停用後完整重啟確認原生速度控制，再啟用恢復；備份前後比對各App獨立值。