# 1.2.32 Root 鎖屏程序恢復與手機管家 ANR 分析

日期：2026-11-22。保留先前未提交工作；本次沒有提交或推送。

## 模組被殺後恢復

原版只依賴 START_STICKY，手機實測不會及時自動重啟。新增僅在自動鎖屏功能啟用時運作的獨立 Root 守護，約每 5 秒檢查模組程序；另外用每 10 秒更新的服務心跳偵測超過 20 秒未回應的服務。程序不存在或服務心跳過期時，由 Root 重啟自己的 IdleLockService。服務重新建立後恢復動態鎖屏接收器。

守護只操作 VideoSpeed 自己的資料目錄與服務，不授權影片 App Root、不寫全域開機腳本、不改 HMA 或 LSP 作用域。PID 鎖避免重複守護，舊失效 PID 可清除；關閉功能刪除啟用旗標，守護退出。明確 force-stop 會讀到 user 0 的 stopped 狀態並退出，不能清除該旗標擅自復活。解除安裝刪除資料目錄後也不繼續執行。開機同步沿用既有 BootReceiver；本次沒有重開機驗證。

## 具體檔案

- 新增 app/src/main/java/io/github/MarsGao/speed/IdleLockRecovery.java：私有守護資源、啟用旗標、心跳、背景 Root 啟動。
- 新增 app/src/main/res/raw/idle_recovery.sh：獨立程序監控、單一實例與 force-stop／停用退出。
- 修改 IdleLockService.java：同步守護、開始與取消週期心跳。
- 修改 IdleLockRoot.java：共用有逾時的 Root 指令執行；僅模組 UID 使用。
- 修改 strings.xml、gradle.properties、README.md、TECHNICAL_OVERVIEW.md，版本為 1.2.32 / 1002032。
- 新增 scripts/test_idle_recovery.py，修改 test_idle_lock.py 的基礎服務 stub。

## 手機實測

手機 461aba0 / 2512BPNDAG，原設定事先備份。

1. 只終止已確認 UID 10318 的模組程序 PID 32608，守護 PID 20345 保持存活，4.29 秒後模組 PID 21236 出現，前台服務已建立。
2. 抖音計時期間再次終止模組，4.35 秒後恢復為 PID 29834；原抖音程序不重啟，12:50:04 日誌確認 Idle channel locked=true。
3. 明確 am force-stop 模組後，12 秒觀察期間沒有程序復活；守護記錄 package force-stopped、guard exited，PID 鎖消失。
4. 手動重新開啟後守護恢復；從 App 設定關閉功能後，守護 PID 8957 消失、enabled/PID 鎖刪除，前台服務不存在。
5. 最後還原兩份原 speed.xml 並逐 byte 核對成功。全局 1.23x、Kylin 1.36x 保留，自動鎖屏恢復原關閉狀態；系統熄屏維持 60000ms。最後確認沒有殘留守護程序。

本機證據：R:/Android Studio/io.github.MarsGao.speed/work/idle-lock-phone-recovery-20261122-123800/final-report.json，以及 recovery-kill-result.json、recovery-while-douyin.json、recovered-lock-proof.log、force-stop-result.json、disabled-guard-result.json。此版本以抖音驗證共用恢復通道；未將它冒稱為三個 App 在本版各自重測。

## 建置與檢查

Debug APK 與 lint 通過；lint {'Warning': 23}。413 項 JVM 檢查、12 組實際 shell 行為檢查與 15 個包名映射通過。離線 shell 使用假 Android 指令；Root 程序存活及真正鎖屏以上述手機測試為證。

APK：artifacts/VideoSpeed_1.2.32.apk
SHA256：5680d47afb6374b6ee617c2df468765f1b4b86f85b54ba9995cfa3d6ea804574

## 手機管家 ANR 原因

已另存 SECURITYCENTER_ANR_ANALYSIS_20261122_125538.md。直接證據是 com.miui.securitycenter.remote 的 VirusScanJobService 在 onStopJob 主執行緒等待 L8.k 類別鎖，鎖持有者在 guardprovider SDK 等掃描引擎就緒，造成後續排程回呼不能及時返回。引擎套件沒有停用／凍結，12:43:10 後已完成掃描 code=0。同類事件於 01:40、11:45／11:46 就存在；沒有證據能歸咎於此次 Root 守護。更上游的綁定／回呼時序仍是推論，並未證明完整循環死鎖。

未清除或結束手機管家，未停用防毒排程／引擎；僅選擇系統對話框的等待。這是診斷完成，不能稱手機管家的 OEM 問題已修復。
