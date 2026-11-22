# 手機管家 ANR 追查：原 APK 鎖等待重現與晚間耗時證據

日期：2026-11-22（Asia/Taipei）。本輪只新增診斷工具與文件，未再修改或安裝 VideoSpeed APK，既有 1.2.40 修改保留，未提交／推送。

## 結論與證據強度

已在手機上的原 SecurityCenter APK 類別中，於獨立 ART 程序重現 L8.k 的全域鎖阻塞：背景 prepare 等候連線時，release 與 factory x 皆為 BLOCKED，直到約 8 秒後才返回。正常連線控制組約 0.24 秒完成。此測試不載入 XposedBridge，不使用手機管家的私人資料，也不修改第三方模組作用域。

因此，手機管家 12.8.5-260910.0.1 的掃描橋接層本身確實具備可重現的長時間鎖競爭，已有比「看到某模組在作用域內」更直接的修正目標。此結果不是已完成系統修復，也不代表已排除實際 ANR 當下所有外部影響。

## 實際晚間 ANR 新證據

進一步讀取 PID 22039 的原有 logcat，取得 MIUIScout 保存的主執行緒訊息歷史：

| 事件 | JobHandler 計畫時間 | 排程遲到 | 執行耗時 | 推算回呼結束 | 系統 ANR |
|---|---|---|---|---|---|
| 20:13 | 20:13:42.243 | 2 ms | 8,023 ms | 約 20:13:50.268 | 20:13:50.245 |
| 20:24 | 20:24:30.554 | 2 ms | 8,011 ms | 約 20:24:38.567 | 20:24:38.561 |

兩次慢訊息均屬 android.app.job.JobServiceEngine$JobHandler，what=0。這補足了「擷取當下 main 已在 nativePollOnce」的解釋：實際長回呼已執行約 8 秒，在啟動 ANR 擷取前後剛結束，之後看到閒置堆疊不表示之前未阻塞。

時間對齊加上原 APK 控制重現，強力支持掃描初始化／鎖競爭是晚間 ANR 的主要調查方向；但晚間沒有捕捉到阻塞期間的精確 Java 呼叫點，不能將每一次事件都斷言為同一把 monitor。

## 已核對的原 APK 路徑

實機 SecurityCenter SHA256：bdf752602a0fdf8dd91be700164f913e8cdf2ed50b8a2f7475f477b67022516f。

1. VirusScanJobService.onStartJob 在主執行緒呼叫 L8.t.a。
2. L8.t.a → L8.k.x；x 為 static synchronized，同樣需要 L8.k.class monitor。
3. 原 APK L8.k.b(long) 持有 L8.k.class monitor 執行 delegate prepare。
4. N8.c.b(long) → SDK g.e(long) → g.k(long)，等待 ServiceConnection，原工作傳入 8000 ms。
5. SDK 的 wait 釋放自己的內層鎖，仍不釋放外層 L8.k.class monitor。
6. VirusScanJobService.onStopJob → r() → L8.k.release，也必須取得 L8.k.class monitor。
7. 連線回呼預設透過主執行緒派送；若該執行緒已卡在 x／release，連線完成通知也可能無法及時處理。這是暫時互相等待的條件，正值 timeout 仍會結束等待，不能稱為已證實的永久死鎖。

額外核對 SDK bind 使用 action com.miui.guardprovider.action.antivirusservice_NG、套件 com.miui.guardprovider。GuardProvider 的 server.VirusScanService.onCreate 建立 Binder，onBind 返回該 Binder。沒有足夠證據說引擎元件缺失或遭停用。

## HyperCeiler 相關檢查

目前仍有 HyperCeiler 作用於手機管家與 GuardProvider；本輪沒有更動。讀取實際程序對應 LSPosed 日誌，確認 GuardProvider 20:13:42／20:23:19 啟動時：

- DisableRootedCheck：Skip hook because initDexKit failed。
- DisableUploadAppListNew：Skip hook because initDexKit failed。

手機管家 PID 22039 的 ShowBatteryTemperatureNew 也有相同跳過紀錄。這表示先前不能僅根據功能設定／作用域，就說這幾個 Hook 已成功攔截。仍不能由個別跳過紀錄推導 HyperCeiler 其他功能完全不影響程序；本次未做取消作用域的 A/B，亦未把 HMA／Root／記憶體不足指定為根因。

## 原 APK 控制重現

新增的測試直接透過 PathClassLoader 載入手機 /product/priv-app/SecurityCenter/SecurityCenter.apk 的 L8.k、com.miui.guardprovider.sdk.g 及其介面。

自訂 Context 僅攔下 bindService／unbindService，提供可控制的虛擬 ServiceConnection；不向系統要求綁定 GuardProvider，不掃描任何 App。測試執行緒代表開始／停止工作對相同 monitor 的競爭，並非把真實 JobService 放進逾時狀態。XposedBridge 類別不存在檢查通過。

| 控制情境 | SDK prepare | SDK 耗時 | 開始／停止代表執行緒 | 等待耗時 |
|---|---|---|---|---|
| 約 150 ms 提供連線回呼 | true | 153 ms | 取樣時均已結束 | 236 ms |
| 暫不提供連線回呼 | false | 8,001 ms | x 與 release 均 BLOCKED | 8,002 ms |

記錄的 BLOCKED frames 直接是原 APK 的 L8.k.x 與 L8.k.release。這證明原程式的鎖行為及其對連線時序的敏感度；沒有重現真實 GuardProvider 為何延遲，也沒有宣稱整個系統 ANR 已重現／修復。

## 程式碼與正式測試

- scripts/diagnostics/ScanLockProbe.java：上述反射診斷程式，無原 APK 程式碼拷貝；控制連線回呼，檢查 prepare 結果、阻塞狀態、耗時及結束狀態。
- scripts/test_device_securitycenter_lock.py：必須指定 --serial；核對固定 APK SHA256，版本不符直接中止。使用 JAVA_HOME／ANDROID_HOME 建置一次性 dex，在獨立 shell ART 執行兩組控制；以 finally 清理自己上傳的唯一 dex。
- 診斷工具只適用本次 APK，未加到無手機的自動檢查／CI；不因未連線而執行真機操作。
- 兩組已在實機完成，完整輸出保存於 work\feature-phone-security-20261122-211208/original-apk-probe.txt。
- 本輪不影響 APK 業務碼，不需重新安裝；未重跑與此工具無關的影片或自動鎖屏測試。

重跑命令（需設定 JDK／SDK，且手機管家 APK 雜湊仍相符）：

```powershell
python -B scripts/test_device_securitycenter_lock.py --serial <明確指定的手機序號> --output <結果檔案>
```

## 真正修正需滿足的條件

修正位置是手機管家的掃描橋接層及 JobService 工作生命週期，不能以調整 VideoSpeed 倍速來修復。

- 引擎連線等待不能包在 x／release 共用的全域 monitor 中；鎖只保護短時間的狀態更新，且必須保留併發 prepare 的引用計數正確性。
- onStartJob 取得服務實例的路徑與 onStopJob 釋放資源的路徑都要避免主執行緒阻塞，只把 onStopJob 搬到背景仍不足。
- 每一個 JobParameters 要持有自己的引擎／取消訊號／executor／完成狀態；原類別共用欄位，在工作重疊時有後續風險，不能只非同步呼叫原方法就宣稱安全。
- 保留掃描、取消、完成通知及需要重試時的排程行為，不能直接跳過回呼、假回報掃描完成、停用防毒或單純降低 timeout。

這些變更涉及系統 App；本輪未將尚未驗證的 Hook 塞進 VideoSpeed 或修改系統 APK。已有可重跑的對照基線，可供後續官方版本或獨立相容修補驗證；應以修補後 x／release 能及時返回、正常掃描仍完成、取消／重試正確及真實工作不再 ANR 作為驗收。

## 保留資料與參考

手機沒有被重啟或強殺；未清資料、未更新系統套件、未改防毒設定／LSPosed 作用域。兩組診斷 dex 已刪除。最新 /data/anr 檔案仍為 20:24 舊事件，這僅是本輪未新增 ANR 的觀察，不代表間歇問題消失。

私人證據保留於 work\feature-phone-security-20261122-211208（Git 排除），跨程序 dump、原 APK、反編譯及 LSPosed 設定快照只在本輪 RAM 暫存區，不提交。

- Android JobService 說明：https://developer.android.com/reference/android/app/job/JobService
- ANR 診斷與主執行緒鎖競爭：https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs

## 使用者授權停用防毒後的處理

後續已依新指示停用防毒引擎並完成真機測試，見 [UPDATE_SUMMARY_20261122_215219_SECURITYCENTER_ANTIVIRUS_DISABLED_DEVICE_TEST.md](UPDATE_SUMMARY_20261122_215219_SECURITYCENTER_ANTIVIRUS_DISABLED_DEVICE_TEST.md)。該紀錄也包含本輪錯誤停用排程元件造成的崩潰與修正，請以後續文件的最終狀態為準。
