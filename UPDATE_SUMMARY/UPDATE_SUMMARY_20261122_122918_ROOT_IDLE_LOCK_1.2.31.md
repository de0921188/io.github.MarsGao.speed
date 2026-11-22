# 1.2.31 Root 無觸控鎖屏與手機測試

日期：2026-11-22。保留既有未提交工作；本次未 commit / push。

## 最終機制

- 依使用者選擇改用 Root 模擬電源休眠鍵（input keyevent 223）。只有 VideoSpeed 自己呼叫 su，影片 App 不取得 Root。
- Root 授權以 id -u 驗證 UID 0，快取在模組私有 lock_runtime.xml；沒有把 Root 權限或憑證放進公開倍速設定。首次實機檢查直接通過，沒有額外確認畫面。
- 不依賴無障礙或裝置管理員。舊無障礙服務已自行退出，VideoSpeed 舊管理員已撤銷；其他 App 無障礙設定保留。
- 啟用時維持前台鎖屏通道，關閉即停止；具啟動與開機同步。API 34+ 使用限定本套件的有序廣播，核對系統提供的發送 UID、實際套件、支援清單與 5 秒時效。
- goAsync 前先保存系統 UID，Root 執行放在工作執行緒，完成後回報結果。Root 鎖屏有 5 秒逾時，授權檢查有 45 秒逾時，錯誤／非零結束碼均記錄。
- Provider、綁定 Service、來源驗證 Activity 的舊系統備援共用相同安全檢查；在此手機，這三條路徑無法從抖音呼叫，實際成功路徑是動態有序廣播。
- 依先前明確授權使用 --force-queryable 測試安裝；沒有更動 HMA 或 LSP 作用域。

1.2.31 另修正同一 App 自動切換 Activity 不得重置無觸控時間：以 started Activity 集合區分內部切頁與真正背景，延續最後觸控時間。新增兩項回歸檢查。

## 本次異動

新增 IdleLockRoot、IdleLockChannel、IdleLockService、IdleLockBootReceiver、IdleLockActivity，及過渡版 IdleLockAccessibility / XML。IdleLockAdmin 保留舊安裝權限清理用途；正式 gateway 不再呼叫 lockNow 或無障礙鎖屏。
修改 MainActivity 的 Root 授權驗證／設定與服務同步、IdleLockProvider 共用鎖屏 gateway、IdleLockHook 的授權通道／非同步回覆與生命週期保護、Manifest、strings、版本與文件。新增本機手機備份路徑的 Git 忽略規則。
原有每 App 倍速、Reader 與抖音行為及既有未提交改動保留。

## 建置與離線測試

- 1.2.31 / versionCode 1002031，Debug 建置與 lint 成功。
- 114 項 idle infrastructure stub 檢查 + 299 項原有 JVM 檢查通過，另 15 個支援包名映射檢查通過。
- lint：0 errors / 23 warnings。Root 指令本身與鎖屏效果以手機驗證，JVM harness 的 Root 系統執行 gateway 為 stub。
- APK：artifacts/VideoSpeed_1.2.31.apk
- SHA256：801B421ADBF1B255629FD9FDD4BB12A1CCB18A1CB0BA2E8D346CDB5699568CF4

## 實機與證據

手機：461aba0 / 2512BPNDAG，Android API 36。
抖音 33.9.0、Phoenix 6.5.1.32、Kylin 7.2.5.32。
本機證據與原設定備份位於 work/idle-lock-phone-20261122-114522/（已忽略，不應提交手機原始資料）。一次性截圖／工具存於獨立 RAM Disk 子目錄；交付證據須複製至永久路徑。

- Root 抖音：12:18:29 模組 gateway 與目標程序皆回報 locked=true，觀察到 Keyguard／熄屏。啟動途中有暫時視窗切換，計時以焦點恢復重新開始；不能用最初啟動時間直接認定一分鐘誤差。
- Root Phoenix：12:21:13 gateway 回報 locked=true，後續確認 mIsShowing=true；本次最終 Root 驗證為首頁前台，不能冒稱已驗證 Root 影片續播。較早無障礙版本曾驗證續播第 1→2 集，約 35 秒滑動後重新等待完整一分鐘才鎖屏，但該結果需與 Root 版分開。
- Root Kylin：1.2.30 已於 12:26:25 回報 locked=true。1.2.31 也於 12:29:01 gateway 回報 locked=true，觀察到 mIsShowing=true。
- 背景負向：已觀察超過 70 秒保持桌面未鎖屏；為排除系統原本的一分鐘熄屏，測試暫改系統逾時 180000ms，結束須還原 60000ms。
- 被殺恢復：只對已確認 UID 10318 的模組 PID 10248 做 kill -9，前 12 秒沒有重新出現程序，後續需手動開啟。自動恢復驗收未通過，不能宣稱 Root 版不會被殺後失效。與一般正常鎖屏通過是分開的結果。
- 生物辨識：使用者已確認無障礙版抖音鎖屏後可成功使用指紋／臉部。Root 版使用者也已回覆「可以，已解鎖」，確認生物辨識可用。

抖音與 Phoenix 的 Root 成功紀錄來自 1.2.30；1.2.31 更改生命週期計時並補測 Kylin，未把不同版本測試混稱為同一版全部通過。

## 收尾

待記錄 Kylin 最後結果、還原系統逾時並核對原倍速設定。自動鎖屏測試值為一分鐘，原始設定未啟用；未收到保留測試設定的選擇時，預設恢復原關閉狀態。

## 收尾狀態補記

最後要關閉測試開關時，ADB 回報 device not found；還原指令在開始前就失敗，沒有寫入原設定。因此不可宣稱已關閉測試值；一分鐘自動鎖屏仍可能啟用。已請使用者重新連線或手動關閉。系統熄屏 60000ms 已在先前確認還原，倍速設定先前逐項比對未變；仍待重新連線完成最後核對。Root 生物辨識已獲使用者回覆「可以，已解鎖」。

## 最後核對完成

使用者重新接上 USB 後，已停止三個測試 App 與模組服務，將兩份原始 speed.xml 逐 byte 核對還原；原全局 1.23x、Kylin 1.36x 保留，自動鎖屏恢復原關閉狀態（新鍵缺省）。系統 screen_off_timeout 已確認 60000ms。再開啟模組畫面供使用者檢視；Root 授權快取保留、無障礙不依賴、舊裝置管理員已撤銷。手機仍為 1.2.31。最終結果見 work/idle-lock-phone-20261122-114522/final-root-report.json。未提交或推送。
