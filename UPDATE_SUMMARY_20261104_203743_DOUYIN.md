# 抖音下一支影片設定更新修復：1.2.23

時間：2026-11-04 20:37:43（Asia/Taipei）

## 結果

- VideoSpeed 1.2.23 / 1002023 已保留資料覆蓋安裝。
- 使用者已確認：不重啟抖音，在設定頁修改倍速後，回到抖音滑下一支能正常跟隨。
- 同一抖音 PID 31876 的日誌記錄不同新值被套用（例如 2.2、0.8、2.1；使用者自行測試其他數值）。本輪未操作或還原使用者的倍速設定。
- 未修改 HMA 規則，未清除裝置資料或日誌，未 commit / push。

## 根因與修正

- 原本抖音只在 prepare/resume 路徑刷新啟動時快取；設備抖音 33.9.0 切片主要觸發 setSpeed，故日誌持續使用舊 1.23x，即使設定頁已儲存新值。
- 新增 DouyinSpeedState，將全域配置與當前手動選速分開保存。setSpeed / prepare / resume 均讀取最新配置；全域值改變時套用新值，全域值不變時保留原生手動選擇。
- SpeedConfigBridge.hasSpeedChanged 改比較實際數值，避免 XSharedPreferences.reload 先消耗檔案變更訊號，讓其他既有快取分支漏掉更新。
- 保留原抖音手動判斷、原生 resume 探查與 TTPlayer legacy 回退；僅補相關堆疊索引範圍保護。此設備舊 TTPlayer 類不存在，主 SimPlayer 路徑已確認命中，未將 legacy 類不存在當作整體 Hook 成功證據。
- 兩個閱讀 App 的 Hook 檔案未改，Provider / 可讀偏好來源優先順序、速度範圍與原設定 UI 保留。

## 變更檔案

- app/src/main/java/io/github/MarsGao/speed/MainHook.java
- app/src/main/java/io/github/MarsGao/speed/DouyinSpeedState.java（新增）
- app/src/main/java/io/github/MarsGao/speed/SpeedConfigBridge.java
- gradle.properties
- scripts/test_douyin_speed_state.py（新增）、scripts/test_speed_config_bridge.py
- README.md、TECHNICAL_OVERVIEW.md、docs/DOUYIN_DEVICE_EVIDENCE_20261104.txt（新增）、本摘要。

## 驗證與界線

- Douyin 狀態回歸：10 項；配置與檔案相容性：23 項；兩個閱讀 App Hook：各 34 項。共 101 項 JVM 檢查通過。
- assembleDebug、lintDebug 通過；lint 0 errors / 20 warnings，未隱藏警告；git diff --check 通過。
- 設備：461aba0 / 2512BPNDAG，抖音 com.ss.android.ugc.aweme 33.9.0 / 330901。
- 已驗證同進程修改設定後換片，使用者確認實播。沒有另外對抖音做播放進度計時，原生手動 UI 的完整裝置回歸、其他 App 全量實機回歸、抖音其他變體與重開機尚未覆蓋。
- 本輪保留前次髒工作區與已確認正常的閱讀 App 實作；沒有刪除其他功能或調整其作用域。

## 交付

- artifacts/VideoSpeed_1.2.23.apk
- SHA256：C6CC23922F616A216E2483BD12EBEC67A55F9FEBC00EF48737C125D04873B449
- 先前 1.2.22 的閱讀 App 修正記錄保留於 UPDATE_SUMMARY_20261104_161718.md。
