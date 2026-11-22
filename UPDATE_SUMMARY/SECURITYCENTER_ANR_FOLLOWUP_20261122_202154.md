# 手機管家 ANR 本次補充（2026-11-22 晚間）

已確認這次也是防毒掃描排程的啟動／停止回呼未及時回應，但沒有足夠證據證明它與中午抓到的 SDK monitor 阻塞完全相同。沒有進行清資料、停用防毒、強殺手機管家或變更 HMA／LSP 的操作。

## 系統事件

- 19:32:40：com.miui.securitycenter.remote PID 21345，No response to onStopJob；19:33:22 系統退出歷史記錄該程序因 ANR 結束。
- 19:35:01：新程序 PID 22039 再次出現 No response to onStopJob。
- 20:13:42：ActivityManager 為手機管家啟動 com.miui.guardprovider/.server.VirusScanService。
- 20:13:50.245：JobServiceContext 記錄 #1000/210600 VirusScanJobService No response to onStartJob。
- 20:13:50.267：相同掃描服務的另一工作內容記錄 No response to onStopJob。
- 20:13:50.270：am_anr 記錄 PID 22039 No response to onStartJob。
- 20:13:50 前後 GuardProvider 解除綁定，20:18:04 它才因 LOW_MEMORY 被系統回收；此退出發生在 ANR 之後，不能作為本次直接起因。

## 執行緒證據的邊界

中午的 trace 明確看到主執行緒在 L8.k.release 等待背景掃描持有的 Class monitor，背景又在掃描 SDK 等待引擎就緒，見 SECURITYCENTER_ANR_ANALYSIS_20261122_125538.md。

晚間 19:35:01 與 20:13:50 temp_anr 的 Java trace，主執行緒在 MessageQueue.nativePollOnce，已沒有呈現上述鎖鏈。完整 ANR 後續轉成 native backtrace，因此應優先保留同一時刻的 temp_anr 作為 Java 證據。這次只能確認 JobScheduler 沒按時收到掃描服務回應；可能是較早的阻塞、服務／回呼時序或系統資源影響，尚不能指定其中一項為已證實根因。

手機管家當前仍有持久化防毒排程，包含要求空閒／非計量網路的 12 小時及 24 小時工作；排程反覆執行時能再次碰到問題，並非畫面上按「等待」就代表已修復。系統回收其他手機管家程序的 LOW_MEMORY 記錄需與 remote 的 ANR 分開分析。

Android JobService 回呼在主執行緒執行，過久不回應可觸發 ANR。[官方 JobService 說明](https://developer.android.com/reference/android/app/job/JobService)。nativePollOnce 表示擷取時主執行緒正在等訊息，本身不足以指出之前卡在哪裡。[官方 ANR 診斷說明](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)。

## 與 VideoSpeed 測試的關係

本次沒有證據能歸因於 VideoSpeed、HMA 或單純記憶體不足；也沒有宣稱已排除所有第三方模組影響。ANR 對話框會取得焦點，因此 VideoSpeed 依前台焦點保護暫停倒數。選擇「等待」並回到抖音後，20:16:17.475 仍成功鎖屏。

模組倍速與鎖屏優化不等於修好系統手機管家的排程問題。真正修復仍需更接近發生前的追蹤、手機管家／GuardProvider 版本與系統修正的對照；本輪未擅自重啟或更動安全功能。

## 證據

私人手機證據保存在 Git 排除的 work/feature-phone-20261122/：anr-events.txt、securitycenter-exits.txt、latest-anr-thread-excerpts.txt、scan-job-timeline.txt、securitycenter-scan-jobs.txt。跨程序完整 dump 僅放任務暫存區；永久證據限手機管家相關片段。
