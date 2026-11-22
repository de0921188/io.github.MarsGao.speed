# 長按 2x 放手後倍速失效：修正與驗證進度

## 已確認原因
- 抖音原日誌把長按 2x、放手回到 1x 記成持續性的原生手動選擇，因而覆蓋模組設定 1.23x。
- Kylin 共用 ReaderVideoHook 也把暫時 2x 寫入手動覆蓋狀態，放手回到 1x 時跳過預設倍率 1.36x。
- 本次讀取的有效設定與 Provider 值一致，沒有更動 HMA 或 LSP 作用域。

## 程式變更
- LongPressSpeedState：分離長按中的 2x 與一般手動選速，放手／取消後還原目前選定倍率，處理重複非同步重設。
- LongPressGestureHook：觀察 Activity 以及實際頁面繼承鏈的 dispatchTouchEvent；補足自行消耗觸控、不呼叫基底方法的頁面，並避免重複 Hook。
- MainHook：接入抖音長按狀態，補上新版 SimplifyPlayerImpl／SimplifyAsyncPlayer 設速入口。
- ReaderVideoHook：Kylin／Phoenix 分離真正手動倍率與暫時加速，來源切換、釋放及設定變更同步清理；放手後採播放進度／單調時間比較驗證。
- 回歸測試：新增長按、放手、取消、重複重設、原生手動選速與全屏頁面自行消耗觸控情境。

## 已完成驗證
- 1.2.34 assembleDebug 與 lintDebug 成功。
- 全部腳本通過：442 個 JVM 檢查、12 個實際 shell 行為檢查，另 15 個支援套件對照檢查。
- 診斷版 1.2.35 assembleDebug 成功並安裝到手機；增加未辨識長按時的狀態診斷，保留原設定。
- 真機確認 Kylin 的 AbsActivity 與 ShortSeriesActivity 觸控方法已 Hook；一般播放進度約 1.36x。

## 尚未完成，不能當作修復驗收
- 1.2.34 Kylin 橫屏仍有 preserving app speed=2.0，尚需確認觸控狀態與原生回呼時序。
- 1.2.35 尚未取得有效的長按 → 放手完整真機證據；測試中 ADB 在 USB／無線間多次切換，最後裝置清單已無連線。
- 抖音滑下一支、橫屏、長按放手完整流程仍待重測；Phoenix 新版長按真機尚未重測。
- 手機管家測試期間再次出現 ANR，只選擇等待；原先 SDK 等待／類別鎖問題未實作修補，不能宣稱已修好。
- 未提交或推送 Git；既有尚未提交的來源與使用者資料保留。
