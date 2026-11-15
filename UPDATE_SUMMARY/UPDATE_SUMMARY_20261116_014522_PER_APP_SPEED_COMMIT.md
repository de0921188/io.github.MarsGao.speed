feat: 新增每個應用獨立播放速度（1.2.24）

## 本次整理日期與範圍

整理時間：2026-11-16 01:45:22（Asia/Taipei）。
本次提交基準：3132fe1；版本由 1.2.23 升至 1.2.24（versionCode 1002024）。
功能程式碼是 2026-11-05 前次工作留下的未提交內容；2026-11-16 本次工作核對差異、重新執行測試及建置、新增本時間戳摘要，並依使用者要求提交與推送。沒有將既有功能描述為今天新實作。

## 功能與具體程式碼變更

| 檔案 | 變更與作用 |
| --- | --- |
| app/src/main/java/io/github/MarsGao/speed/SpeedSettings.java（新增） | 統一速度解析：speed.app.<packageName> 有效獨立值 → 原 speed 全域值 → 預設 1.5x。校驗包名與 0.25～4.0x 範圍；顯式獨立值即使等於全域仍保持獨立。 |
| app/src/main/java/io/github/MarsGao/speed/MainActivity.java | 保留全域設定，新增 15 個已支援包名清單、名稱／包名搜尋、有效速度與來源顯示、獨立值編輯與跟隨全域重置。保存只寫入選中包名鍵，重置只刪除該鍵。已安裝應用優先顯示系統名稱，否則使用內建標籤。 |
| app/src/main/java/io/github/MarsGao/speed/MainHook.java | 在閱讀 App 與其他支援 App 的入口綁定 lpparam.packageName，以應用真實包名選取設定，避免子進程名稱造成分裂。 |
| app/src/main/java/io/github/MarsGao/speed/SpeedConfigBridge.java | 增加目標包名與切換時的來源快取重置；XSharedPreferences 使用共用解析，Provider 請求附包名；變更偵測繼續比較本 App 實際速度，避免其他 App 設定觸發誤判。 |
| app/src/main/java/io/github/MarsGao/speed/SpeedConfigProvider.java | 支援 /speed?package=<packageName> 並校驗參數；不帶參數的 /speed 仍回傳全域值，speed 欄位及只讀契約不變。 |
| app/src/main/res/layout/activity_main.xml | 將置中單一輸入畫面改為頂部全域設定、下方搜尋與應用清單。 |
| app/src/main/res/layout/item_app_speed.xml（新增） | 應用名稱、包名與獨立／跟隨全域速度按鈕的清單列。 |
| app/src/main/res/values/arrays.xml、strings.xml | 新增 15 個支援包名對應名稱、全域與獨立設定文案、驗證及保存提示。未擴增播放器支援範圍。 |
| gradle.properties | appVersionName 更新為 1.2.24。 |
| scripts/test_speed_settings.py（新增） | 速度隔離、繼承、重置、範圍／包名校驗與舊資料相容性；另核對 15 個 UI 標籤及 Manifest queries。 |
| scripts/test_speed_config_bridge.py | 擴充包名路由、各 App 值隔離、獨立值變更與舊全域契約測試。 |
| README.md、TECHNICAL_OVERVIEW.md | 補充操作方式、設定鍵、跨進程契約、版本與驗證限制。 |
| UPDATE_SUMMARY_20261105_012523_PER_APP_SPEED.md | 納入前次尚未提交的原始功能摘要，保留當時歷史狀態。 |
| UPDATE_SUMMARY/本檔 | 新增本次整理、重新驗證與提交說明，並附錄原摘要全文。 |

## 保留、替換、移除與尚未驗證

- 保留：原 speed.xml 與全域鍵，不需資料遷移；LSPosed 設定檔可讀性處理；閱讀 App 偏好優先與其他 App Provider 優先；既有播放器、換片與手動選速邏輯。
- 替換：設定畫面的單一全域編輯流程，擴充為全域加應用覆寫；共用讀取從只讀全域改為依包名解析。
- 移除：重複的橋接速度 sanitize helper 由 SpeedSettings 共用解析取代；沒有移除支援包名或播放器 Hook。
- 尚未驗證：1.2.24 新畫面、對話框、Provider 真機查詢與各 App 獨立倍速實播。使用者前次選擇先交付 APK、稍後再測；今天未操作或安裝手機。
- 沒有修改 HMA 規則、清除手機資料或新增權限、服務、接收器。

## 本次重新驗證

- python -B scripts/test_speed_settings.py：79 項 JVM 檢查通過，15 個包名、標籤與可見性宣告一致。
- python -B scripts/test_speed_config_bridge.py：34 項 JVM stub 檢查通過。
- python -B scripts/test_douyin_speed_state.py：10 項檢查通過。
- python -B scripts/test_reader_video_hook.py：com.kylin.read、com.phoenix.read 各 34 項 JVM stub 檢查通過。
- 合計 191 項；這些不是手機播放驗收。
- :app:assembleDebug :app:lintDebug：BUILD SUCCESSFUL；Gradle 主要任務使用既有最新產物（UP-TO-DATE）。
- 前次 lint 紀錄為 0 errors / 20 warnings；本次再次執行 lintDebug 成功。
- 提交前執行 git diff --check。

## APK 與提交方式

- artifacts/VideoSpeed_1.2.24.apk；SHA256：C169B956C0C244DB2F8F38054B55CC3DF14E8B16AAA0BD80475B6CEE696847AB（本次重新核對一致）。
- APK 依既有 *.apk 忽略規則不納入 Git；本次推送程式碼、測試與文件，沒有另建 GitHub Release。
- 使用本摘要全文作為 Git commit message（comment），包含下方前次 UPDATE_SUMMARY 全文，再推送 origin/master。
- 本檔記錄提交內容與推送目標；推送是否完成及 commit hash 以執行結果與回覆為準。

## 附錄：前次 UPDATE_SUMMARY 原文

以下保留 2026-11-05 原始摘要；其中「未 commit 或 push」及裝置狀態是當時紀錄。
# 每個應用獨立播放速度：1.2.24

時間：2026-11-05 01:25:23（Asia/Taipei）

## 已完成

- 保留現有全域 speed 值，不清除資料、不重設任何原有速度。
- 新增 15 個已支援包名的獨立覆寫，鍵為 speed.app.<packageName>；未設定時跟隨全域，重置只刪除該應用鍵。
- 設定頁改為頂部全域預設、下方可搜尋的應用清單；顯示生效值與全域／獨立來源，點應用開啟編輯對話框。保存、取消、恢復跟隨全域分開，非法值留在對話框提示。
- 所有 Hook 入口用 lpparam.packageName 綁定實際目標，包括微信、抖音、兩個閱讀 App 與既有各變體；不依賴目標 Context 的包名或 processName 後綴。
- Provider 支援 /speed?package=<packageName>，無參數仍回傳全域值，欄位與只讀契約保留。
- XSharedPreferences 與 Provider 共用 SpeedSettings 解析，優先有效獨立值，其次全域，再回退原預設；範圍維持 0.25～4.0x。
- 明確獨立值即使等於當時全域，也不隨之後全域變更；其他應用設定變動不會誤觸目前 App 的變更判斷。

## 保留與影響範圍

- 原全域鍵、原設定檔、LSPosed 偏好可讀性、閱讀 App 偏好優先／其他 App Provider 優先策略、手動選速及換片 Hook 均保留。
- 不新增權限、服務、接收器或受支援以外的播放器適配；設定項仍在原有可讀 speed.xml 內。
- 沒有修改 HMA、手機資料、其他專案；未 commit 或 push。
- 安裝後需重新開啟目標 App 一次載入新 Hook，後續改設定仍於下一支影片生效。

## 檔案

- 新增：SpeedSettings.java、item_app_speed.xml、scripts/test_speed_settings.py。
- 更新：MainActivity.java、MainHook.java、SpeedConfigBridge.java、SpeedConfigProvider.java、activity_main.xml、strings.xml、arrays.xml、gradle.properties。
- 更新測試：scripts/test_speed_config_bridge.py。
- 文件：README.md、TECHNICAL_OVERVIEW.md、本摘要。

## 驗證

- SpeedSettings：79 項；配置橋接：34 項；Douyin 狀態：10 項；兩個閱讀 App：各 34 項。共 191 項 JVM 檢查通過。
- 額外核對 15 個支援包名的 UI 標籤、唯一性與 Manifest queries 對應。
- assembleDebug、lintDebug 通過，版本 1.2.24 / 1002024；lint 0 errors / 20 warnings；git diff --check 通過。
- 沒有連接的 ADB 裝置。嘗試既有 Pixel_10_Pro 的唯讀模擬器，未能啟動（QEMU loader 0xc0000135）；未改原 AVD 資料或安裝其他軟體。
- 使用者選擇「先提供 APK，稍後再測」。因此尚未驗證本版實際畫面、對話框操作、Provider 真機查詢或各 App 獨立倍速實播；先前版本的實播結果不作本版驗收。

## 交付

- artifacts/VideoSpeed_1.2.24.apk
- SHA256：C169B956C0C244DB2F8F38054B55CC3DF14E8B16AAA0BD80475B6CEE696847AB
- 後續重點：抖音與 Phoenix 設不同速度、保留第三 App 跟隨全域，切換全域後核對覆寫不變，再重置其中一 App 並測換片；同步確認重啟後設定保留。
