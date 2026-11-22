# 手機管家 ANR 分析（2026-11-22）

直接原因已由 12:42:51 trace 確認：com.miui.securitycenter.remote PID 21345 的主執行緒卡在 L8.k.release，等待 Class<L8.k> monitor 0x03c67576。持有者 pool-69-thread-1 / tid 120 在 VirusScanJobService 掃描工作內持有該鎖，並於 com.miui.guardprovider.sdk.g.k / g.e 的 Object.wait 等待引擎就緒。onStopJob 因此無法返回，後續 onStartJob 也堆積，觸發 JobScheduler ANR。

事件紀錄 12:41:20 No response to onStopJob；12:42:51 No response to onStartJob。11:45:49、11:46:13 也已有同類 ANR，早於本次新增 Root 守護（約 12:43）啟用；另退出紀錄 01:40 有同類 onStopJob ANR。不能僅依時間同時出現，就說是新的守護造成。

GuardProvider 3.0.1-20260708.1 套件狀態：installed=true、enabled=0（系統預設啟用）、hidden=false、suspended=false、stopped=false；VirusScanService 元件存在。12:42:51/12:43:02/12:43:10 日誌反覆出現 VirusScan service not ready 與重新綁定；12:43:10.241 回報 onScanCompleted code=0，之後正常解除綁定。不是引擎永久缺失／停用的證據。

當時排程掃描目標為 com.omarea.vtools。這只是被掃描套件，不代表病毒或該 App 導致 ANR。壓力快照沒有高記憶體／IO stall，且直接鎖擁有者明確，不能把本次直接原因簡化成記憶體不足。其他歷史 LOW_MEMORY 退出是不同事件。

上游推論：掃描引擎非同步綁定／就緒通知與資源釋放的時序有問題，背景等待時持有全域類別鎖，使主執行緒停止工作受阻。尚未反編譯確認 SDK g.k 的等待逾時與 ServiceConnection 執行緒，不能斷言完整循環死鎖，也沒有證據足以歸咎於 HMA、Root 或 VideoSpeed。

未清除手機管家資料、未終止其程序、未停用防毒工作／引擎。只曾選擇系統 ANR 對話框的「等待」。原始多程序 trace 只存 RAM Disk；永久保存的 trace 僅含手機管家 remote 程序與相關時間線。

Android 官方回呼說明：[JobService](https://developer.android.com/reference/android/app/job/JobService)（排程回呼在主執行緒執行，長時間阻塞可觸發 ANR）。
