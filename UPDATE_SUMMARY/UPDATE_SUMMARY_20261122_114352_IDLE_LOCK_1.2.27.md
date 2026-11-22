# 1.2.27 無觸控自動鎖屏更新摘要

日期：2026-11-22（Asia/Taipei）。本次在既有未提交的 1.2.25／1.2.26 工作之上新增功能，保留原有修改，未提交或推送。

## 使用者可見行為

- 首頁新增「無觸控自動鎖屏」設定按鈕，顯示關閉／分鐘數／等待授權狀態。
- 預設關閉，等待時間為 1～240 的整數分鐘，預設值 10 分鐘。
- 僅在支援且由 LSPosed 實際注入的 App 前台計時；與各 App 調速開關獨立。
- 觸控會重新計時；持續長按不會鎖屏，放開或取消觸控後開始完整等待時間。
- 直屏、橫屏 Activity 轉換、原生對話框納入處理；離開 App、失去焦點、系統視窗覆蓋或 Activity 銷毀會停止計時。
- 首次啟用需使用者在 Android 系統畫面確認裝置管理員，只申請 force-lock 權限；取消不啟用。設定過程的待存分鐘數可跨 Activity 重建保留。
- 裝置管理員鎖屏通常需要 PIN／圖形／密碼才能解鎖；未設安全鎖時只熄屏。說明已放入啟用對話框。
- 關閉功能不自行撤銷裝置管理員；提供系統安全性設定入口管理授權。
- 備份含已設定的 idle.enabled、idle.minutes。新版可讀舊備份；檔案缺少新鍵時保留現值。匯入啟用值但沒有管理員權限時保存為關閉。舊版無法匯入含新欄位的備份。

## 本次程式碼異動

新增：
- IdleTimeout.java：可測試的單調時鐘前台計時模型。
- IdleLockHook.java：Activity／Dialog 觸控及視窗生命週期 Hook；弱引用、單一 Handler 排程；失敗後等新觸控再重新計時。
- IdleLockAdmin.java：系統裝置管理員及啟用狀態判定。
- IdleLockProvider.java：獨立鎖屏 IPC，核對 Binder UID／實際套件／支援清單；確認功能、權限、螢幕及鎖定狀態後，以模組身分呼叫 lockNow；不提供資料寫入介面。
- res/xml/idle_lock_admin.xml：只聲明 force-lock 策略。
- scripts/test_idle_lock.py：實際模型、Hook、Provider、設定與備份的 JVM stub 回歸測試；工作檔使用獨立 RAM Disk 目錄。

修改：
- MainHook.java：支援包名過濾後、調速開關判斷前安裝 IdleLockHook。
- MainActivity.java：設定對話框、分鐘驗證、系統授權流程、狀態更新、備份匯入預覽與無授權保護。
- SpeedSettings.java：新增兩個 float 設定鍵、預設關閉與 1～240 分鐘驗證。
- SpeedBackup.java：嚴格解析、驗證與匯出可選鎖屏設定。
- AndroidManifest.xml：獨立 Provider 及受 BIND_DEVICE_ADMIN 保護的管理員接收器。
- activity_main.xml、strings.xml：新增入口及授權／使用說明。
- gradle.properties：1.2.27 / versionCode 1002027。
- README.md、TECHNICAL_OVERVIEW.md：功能、架構、使用步驟及測試界線。

既有倍速 Hook、各 App 設定、手動倍速保護及既有測試皆保留。本次未更動 LSPosed 作用域、HMA 規則、手機資料或系統螢幕逾時。

## 驗證

- scripts/test_idle_lock.py：85 項通過。含期限前／到期、長按、觸控重置、背景取消、橫屏 Activity 交替、對話框焦點、設定更新、失敗恢復、呼叫者偽造、未授權與已鎖屏等情境。
- 原有 7 支 Python／JVM 測試：299 項通過；另 15 個支援包名的 UI 標籤與 Manifest 映射檢查通過。
- 合計 384 項 JVM 檢查；stub 不等於 Android 真實 IPC 或影片倍速實測。
- JDK 21 + Gradle 8.9，:app:assembleDebug、:app:lintDebug 成功。Lint 0 errors / 19 warnings（既有 18 項，加上新跨 App Provider 的 exported 警告；新 Provider 有執行期 UID／套件驗證）。
- ADB 僅讀取：手機 461aba0 / 2512BPNDAG 連線，仍安裝 1.2.26。本次沒有安裝、啟用管理員、修改設定或觸發鎖屏。

## 交付

- artifacts/VideoSpeed_1.2.27.apk
- SHA256：A1FE443D90456020EA686FAA7E823E89CCF439B10133AEE3CF366D996AF42FD6
- 此摘要及原始碼存放於專案永久路徑；APK 依既有規則不加入 Git。

## 尚待真機驗證

更新安裝後重啟目標 App，由使用者在模組設定啟用並確認系統管理員授權，再以抖音、Phoenix、Kylin 測試 1 分鐘無觸控、觸控後重算、背景不觸發、橫屏及對話框。實際 IPC 元件可見性、廠商鎖屏行為和解鎖限制仍待驗證；若存取受限只記錄錯誤，不變更 HMA 或其他系統規則。
