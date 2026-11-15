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
