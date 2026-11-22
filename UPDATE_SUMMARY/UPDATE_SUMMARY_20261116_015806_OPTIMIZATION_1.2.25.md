# 1.2.25 專案分析與優化更新摘要

時間：2026-11-16 01:58:06（Asia/Taipei）
基準：acce1d4（1.2.24）；本輪開始工作目錄乾淨。

## 分析依據與實作

1. 設定讀取穩定性：SpeedSettings 原先對全域與獨立鍵直接呼叫 getFloat，設定備份／手動編輯若造成 string 或 int 型別，ClassCastException 可中斷設定頁或 Hook 呼叫。本輪加入僅針對型別不符的安全回退；獨立值異常時繼承全域，全域也無效時使用既有 fallback。未吞掉所有 RuntimeException，也未改寫或遷移裝置資料。
2. 頻繁讀取成本：原先即使有獨立值仍先讀全域，且每次包名驗證用 String.matches 重編正則。改為有效獨立值立即返回，包名 Pattern 編譯一次。這是減少固定工作量，沒有量測或宣稱整體播放效能百分比。
3. 微信播放器生命週期：MainHook 的 lastSpeedMap 只有 put，唯一 get 的區域變數未被使用，因此移除表與所有寫入。原靜態 ConcurrentHashMap 會強引用播放器；剩餘手動調速時間改由同步 WeakHashMap 保存 Long，值不持有播放器，過期會移除。每播放器 3 秒保護用 elapsedRealtime，避免手動更改系統時間干擾。Finder 路徑另有全域保護，本輪保留原邏輯。
4. 設定操作：新增全部支持／已安裝／獨立設定篩選，與搜尋組合使用，顯示篩選數量及獨立數量；Activity view state 恢復後重套篩選。已安裝判斷以模組 PackageManager 可見結果為準，仍可從全部清單預設未安裝項目。
5. 新增常用倍速：全域與每 App 編輯框提供 1.0、1.5、2.0、2.5、3.0x 快捷填入；按鈕只填文字，仍須保存。
6. 新增批次恢復跟隨全域：確認對話框顯示影響數量與已保存全域速度，明示包含篩選外的 App。確認後只刪除 15 個已支援包名的獨立鍵，不使用 clear，不改全域；取消不寫入。
7. 移除全域輸入框與 labelFor 重複的 hint，修正一項可及性 lint 警告。

## 檔案變更

- MainActivity.java：篩選、計數、快捷填入、批次重置確認與重新整理。
- activity_main.xml、arrays.xml、strings.xml：新增對應介面、篩選選項及提示文案。
- SpeedSettings.java：型別錯誤回退、先讀有效獨立值、預編譯包名 Pattern。
- MainHook.java：移除無用途的速度強引用表，以 ManualSpeedCooldown 管理原每實例手動保護。
- ManualSpeedCooldown.java（新增）：同步弱鍵表、記錄／查詢／過期移除，無 Android 依賴。
- scripts/test_speed_settings.py：型別異常、NaN fallback 哨兵、有效覆寫短路與非預期例外不被吞掉。
- scripts/test_speed_config_bridge.py：異常型別下的 reader → Provider → fallback 路徑。
- scripts/test_manual_speed_cooldown.py（新增）：每播放器隔離、期限邊界、更新期限、倒退時間、空播放器與參數檢查。
- gradle.properties：1.2.25 / versionCode 1002025。
- README.md、TECHNICAL_OVERVIEW.md：操作、架構調整及驗證範圍。
- 本摘要：本次分析與交付證據。

## 功能對照

- 保留：15 個已支援包名，原 speed 全域鍵、speed.app.<packageName>、原 speed.xml 及 0.25～4.0x 範圍，舊資料無需遷移。
- 保留：閱讀 App 偏好優先、其他 App Provider 優先，NaN 缺失哨兵及設定即時讀取。
- 保留：抖音手動選速與下一片讀新設定；兩閱讀 App 的 Surface 防音訊誤攔、準備／換片／回呼重設；微信 Finder 定位與成功／跳過／失敗日誌。
- 替換：微信每播放器手動時間從強引用表與牆上時間，改為弱鍵狀態與單調時間，期限仍是 3 秒。
- 移除：沒有消費者的 lastSpeedMap 與未使用的 lastSpeed 區域變數；沒有移除任何播放器適配。
- 新增：篩選、快捷填入與確認後的批次重置。這些操作不新增服務、接收器、權限或任意新播放器支援。

## 驗證結果

- python -B scripts/test_speed_settings.py：87 項 JVM 檢查通過；另確認 15 個支援包名、UI 標籤及 Manifest queries 一致。
- python -B scripts/test_speed_config_bridge.py：37 項 JVM stub 檢查通過。
- python -B scripts/test_manual_speed_cooldown.py：12 項 JVM 檢查通過。
- python -B scripts/test_douyin_speed_state.py：10 項 JVM 檢查通過。
- python -B scripts/test_reader_video_hook.py：兩閱讀包各 34 項 JVM stub 檢查通過。
- 合計 214 項。新橋接測試最初受前一情境 Provider 值影響，清理 fixture 後通過；初次編譯發現 Finder 共用冷卻常數仍需要，已保留，最終編譯成功。
- :app:assembleDebug :app:lintDebug：BUILD SUCCESSFUL，0 errors / 19 warnings（前版 20 warnings）。
- git diff --check 通過。JDK 21，Gradle 8.9，原 compile/target SDK 33 不變。
- ADB 目前可見 nabu 平板，模組查詢未見版本；沒有在該裝置安裝、清除資料、調整設定或操作目標 App。
- 未驗證：實際新畫面、對話框及橫直向／大字排版；旋轉後篩選與清單恢復；批次重置在裝置上的結果；微信實播及播放器垃圾回收。JVM stub 與編譯不能替代上述驗收。

## 交付與後續建議

- APK：artifacts/VideoSpeed_1.2.25.apk。
- SHA256：3135BE84659F5A2CD0D851FB625C6A1E27E94CC81EE99ECB2BEDFCA52E636325。
- 安裝後重新啟動目標 App 一次，載入新版 Hook；原設定保留。本輪未安裝到裝置、未提交或推送，也未建 Release／tag。
- 後續設備檢查：保持兩 App 不同速度，快捷填入後先取消核對不變；保存後核對下一片；篩選獨立項目，先取消批次重置、再確認重置，核對全域不變；微信連續換片及原生手動速度保護。
- 評估但未採用：Provider 時間快取／失敗退避會延遲設定更新，可能重現下一片不生效，故本輪不加入。可再評估具有通知失效機制的快取，但需跨程序實機證據。
- 後續維護可再拆分 MainHook 的各播放器適配、加入設定匯出／匯入；前者需各 App 的 Hook 測試矩陣，後者需資料格式及匯入預覽驗證，本輪先完成以上具體改善。