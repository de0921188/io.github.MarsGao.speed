# VideoSpeed 1.2.38 真機補充驗收

日期：2026-11-22（Asia/Taipei）。延續下午中斷的抖音與程序恢復驗證；本輪未改動程式碼、未重建或重新安裝 APK，沿用手機上的 1.2.38／1002038。抖音為 33.9.0／330901，裝置 Xiaomi 17 Ultra／Android 16；ADB 實際使用先前配對的 TLS 連線。

## 抖音長按放手與換片
- 在原生 LandscapeFeedActivity 執行長按 2x，放手日誌恢復 1.23x。
- 首次放手 16:24:56，兩秒播放進度比值約 1.277x。
- 返回直屏 DetailActivity，滑到另一支影片，再進原生橫屏；後續放手樣本約 1.2415x／1.2425x，對應設定 1.23x，沒有掉回 1x。
- 測試播放器程序 PID 20402。證據含原生頁面截圖、temporary requested=2.0、release restored=1.23 與 mediaMs／wallMs 日誌。
- 短樣本可能包含播放器非同步切速過渡、緩衝與排程誤差；此驗收證明放手恢復並持續加速，不能宣稱毫秒級精確或長時間效能已驗證。

## 模組被殺後恢復與實際鎖屏
- 暫時經設定畫面啟用一分鐘無觸控鎖屏，確認前台服務與守護存在。
- 終止前核對模組 cmdline 完全等於 io.github.MarsGao.speed，Uid 為 10318；只對 PID 20403 執行 kill -9，未強制停止抖音。
- 新 PID 12513 約 2.125 秒內恢復前台鎖屏服務。
- 守護的 recover service 記錄由 5 次增加至 6 次，佐證此次為守護恢復。
- 抖音 PID 全程維持 20402，沒有重新開啟影片 App 或手動重啟模組。
- 16:29:32.991，新模組程序回報 Idle lock requested by com.ss.android.ugc.aweme、locked=true；手機實際進入 Dozing。
- 從讀到前台狀態的檢查點至熄屏約 59.09 秒；該檢查點在 App 恢復前台約一秒後，與設定一分鐘的倒數相符。
- 此次只驗證一次一般程序終止後的恢復，不等同於系統「強制停止」、重開機或其他廠牌的行為。

## 收尾與資料保留
- 恢復原始全域 1.23x、Kylin 1.36x，自動鎖屏原為關閉，已還原為關閉。
- su 的終端輸出會為 LF 多加入一個 CR；用獨立測試檔校準後，將備份還原成原始位元組，再用 base64 讀回實體檔核對。兩個既有 speed.xml 均已確認原始內容一致。
- 前台鎖屏服務已停止；測試守護啟用旗標與 guard.lock 均不存在，守護已退出。
- 保留本輪前設定備份、測試 JSON、相關模組日誌與截圖於 work/idle-lock-phone-resume-20261122-162327/；該除錯路徑沿用 Git 忽略規則。
- 未修改 HMA、LSP 作用域、其他應用權限；未執行私訊、分享或按讚操作。
- 本輪未要求或驗證指紋／臉部解鎖；手機管家 ANR 也未在本輪修補。
- 未提交或推送 Git，既有未提交工作保留。

## 證據
- baseline.json：實際裝置、版本、備份路徑與原畫面狀態。
- douyin-land-release.log、douyin-next-land-release.log：抖音長按及放手的實際倍率樣本。
- recovery-lock.json：UID 核對、前後 PID、恢復時間、鎖屏結果與還原核對。
- guard-recovery.log、recovery-lock-logcat.log、after-recovery-lock.log：守護恢復與新程序真正鎖屏的證據。
