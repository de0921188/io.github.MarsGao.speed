# 手機管家：停用防毒引擎、修正排程元件停用造成的崩潰並完成真機測試

日期：2026-11-22（Asia/Taipei）。使用者明確要求「停用防毒功能，修好後直接在手機上測試」。本輪未修改系統 APK 或 VideoSpeed 業務碼，採用可還原的 Android 元件設定處理；未提交／推送 Git。

## 最終狀態

| 套件／元件 | 原狀態 | 最終狀態 |
|---|---|---|
| com.miui.securitycenter/com.miui.antivirus.service.VirusScanJobService | DEFAULT（0） | DEFAULT（0），保留註冊 |
| com.miui.securitycenter/com.miui.antivirus.service.VirusAutoUpdateJobService | DEFAULT（0） | DEFAULT（0），保留註冊 |
| com.miui.guardprovider/com.miui.guardprovider.server.VirusScanService | DEFAULT（0） | DISABLED（2） |
| com.miui.guardprovider/com.miui.guardprovider.manager.SecurityService | DEFAULT（0） | DISABLED（2） |

最終只停用兩個防毒引擎服務。掃描／更新工作元件必須仍存在，讓手機管家的既有呼叫不會因排程目標消失而崩潰。無可用引擎時，實際掃描工作快速結束，避開先前等待約八秒的引擎路徑。

兩個引擎的停用狀態已同時在 PackageManager API 與 user 0 的 package-restrictions 持久化資料確認。兩個套件本身仍啟用，未整包停用手機管家或 GuardProvider。舊 SecurityService 同時提供 URL 掃描，因此惡意網址掃描也停止。這是使用者授權的「停用防毒」處理，不是修正原 APK 的全域鎖程式；重新啟用引擎後，原本的鎖競爭問題仍可能出現。

## 本輪發生的錯誤與修正

21:31:11 原有手機管家 No response to onStartJob 再現；早於 21:32:13 套用本輪停用設定。

初始方案同時停用兩個排程服務與兩個引擎服務。21:33:34 使用者提供的新崩潰證實此方案不完整：q1.c 的無障礙設定 ContentObserver 仍呼叫 VirusScanJobService.h 建立工作，JobScheduler 因元件被停用而拋出 IllegalArgumentException: Tried to schedule job for non-existent component。這是本輪停用方式造成的新問題，已向使用者說明並修正。

21:35:08 已將兩個排程服務恢復為原始 DEFAULT 狀態，僅保留兩個引擎停用。透過 PackageManager 的 DONT_KILL_APP 旗標套用元件設定，未自行強殺整個手機管家。系統因前述錯誤造成的程序退出記錄保留，沒有清除日誌或隱藏錯誤。

## 真機驗證

實機為 Xiaomi 2512BPNDAG／Android 16，手機管家 12.8.5-260910.0.1，GuardProvider 3.0.1-20260708.1。

1. 用原本的 VirusScanJobService、job ID 210600 及有效的 VideoSpeed APK 路徑，經 UID 1000 正常向 JobScheduler 提交工作。這是原系統服務的實際工作，不是先前隔離鎖測試。
2. 共執行 7 次：4 次一般工作及 3 次與原程式相同的 expedited 工作。每次 schedule 返回 RESULT_SUCCESS，工作啟動後由服務呼叫 jobFinished，沒有以取消／強殺／假回報結果取代完成。
3. JobScheduler 的 START／STOP 時間差為 12、7、8、8、7、8、7 毫秒。因引擎不可用，這表示工作快速結束，並不代表完成了防毒掃描。
4. 主要測試過程 com.miui.securitycenter.remote PID 17989 持續存活，沒有新增與這些工作對應的程序崩潰。
5. 兩個防毒 action 的 query-services 均回覆 No services found；最終服務清單也沒有新舊引擎服務。
6. 實際開啟並讀取 UI：手機管家首頁、省電與電池、應用程式管理，三頁皆正常載入，沒有出現沒有回應對話框。這是頁面啟動驗證，不代表已逐一測完這三頁所有功能。
7. 補充建立一個無額外權限的臨時測試 App，對原 enabled_accessibility_services URI 發送四次 notifyChange，未改動設定數值；其中三輪有逐次比對設定內容不變。當前 ContentObserver 清單未見手機管家對此 URI 的註冊，因此這組通知僅列為冒煙測試，不宣稱已強制執行原 ContentObserver 的每個分支；原排程路徑由上述 7 次實際工作另行覆蓋。
8. 截至收尾，手機管家最後一筆崩潰仍是已修正前的 21:33:34；最後一筆 ANR 仍是 21:31:11。21:35 修正之後至 21:52 收尾未新增同類崩潰／ANR。未做重開機或長期日常使用驗證。

## 保留與收尾

- 原元件狀態、相關設定 XML、JobScheduler／崩潰證據已保存在 work\feature-phone-antivirus-20261122-212611（Git 排除）。
- 原有共用偏好設定備份與讀回未發現差異；未猜測或直接改寫未知的防毒設定 key。防毒設定 UI 顯示雲端掃描與引擎自動更新未勾選，但最終停用是否成功以引擎元件與實際工作結果為準。
- 先前待執行的防毒工作 99、210610、210620 已取消；取消 210600、210601 時沒有原有工作。其他手機管家工作（儲存最佳化、連線變更、一般安全檢查）保留。
- 保留支付／輸入安全相關的 GuardService、遊戲工具、電池、網路助手、應用管理等元件；未改 HMA／HyperCeiler／LSPosed 作用域，也未清除使用者資料。
- 臨時測試 APK 已卸載，自己上傳的所有測試 dex 與 UI 暫存檔已移除。
- UI 測試期間將螢幕逾時從 60000 暫設 600000 ms，收尾已核對恢復為 60000 ms。未要求使用者提供解鎖密碼或繞過鎖屏。
- app_process 直接呼叫 ContentService 的測試工具曾因未登記 PID 被拒絕；改用正常註冊的無權限臨時 App 驗證，未繞過該檢查。這些暫時測試程序的錯誤不算作手機管家崩潰。

## 新增檔案與還原方式

- 本更新摘要。
- scripts/restore_securitycenter_antivirus.ps1：針對本次已核對的原 APK，必須明確指定手機序號，先檢查版本雜湊；預設只預覽。加上 -Apply 才將兩個引擎元件恢復原始 DEFAULT 狀態，不改其他元件或偏好設定。

```powershell
# 預覽；不會啟用防毒
.\scripts\restore_securitycenter_antivirus.ps1 -Serial <手機序號> -AdbPath <adb.exe 完整路徑>
# 需要重新啟用防毒時，才使用 -Apply
.\scripts\restore_securitycenter_antivirus.ps1 -Serial <手機序號> -AdbPath <adb.exe 完整路徑> -Apply
```

還原腳本已在此手機通過版本／連線檢查與預覽，沒有執行 -Apply，以保留使用者指定的停用狀態。先前取消工作的原始待執行時刻不會由腳本還原；還原後需重新啟動並驗證手機管家是否重建週期工作。重新啟用防毒可能再次遇到原 APK 的鎖問題。

本次為裝置設定變更，沒有新 APK 建置／安裝 VideoSpeed；既有未提交模組修正及更新摘要全部保留。
