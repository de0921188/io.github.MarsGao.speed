## 1.2.39 獨立鎖屏、診斷與繁簡中文（目前版本）

各 App 可設定鎖屏繼承／停用／獨立分鐘數；抖音主版、Phoenix、Kylin 可選手動倍率策略與試驗性的播放進度限定計時。保留原倍率、原生長按暫時加速及全局鎖屏預設。

「診斷 · 暫停 · 睡眠定時器」可查看最近 Hook／設定／鎖屏回報、重新驗證 Root、匯出診斷、暫停無觸控鎖屏 30 分鐘或啟動獨立睡眠定時器。睡眠倒數不因觸控延長，只在支援且已注入的 App 前台執行；暫停及倒數綁定本次開機且不匯出備份。Root 守護使用 PID＋單調時間心跳，並限制日誌大小。

介面與通知自動跟隨系統的繁體／簡體中文，支援 Hant／Hans 字形及台灣、香港、中國、新加坡等語言選擇。設定頁新增橫屏雙欄。完整行為、驗證與限制見 [本次更新摘要](UPDATE_SUMMARY/UPDATE_SUMMARY_20261122_202154_APP_RULES_DIAGNOSTICS_LOCALES_1.2.39.md)。

## 1.2.38 長按倍率與鎖屏可靠性（歷史版本）

修正抖音、Kylin／Phoenix 共用適配器把「長按暫時 2x」誤認為永久手動倍率的問題。Kylin 實機約在按住 303～305ms 時發出 2x；現在依按住／放手事件判斷，移除 350ms 門檻與放手後一秒失效的限制。放手或按住時切背景會主動恢復所選倍率，保留真正原生選單選速。

倍速與無觸控鎖屏共用視窗輸入觀察入口；無法掛接的系統保留 Activity／Dialog 備援。只觀察觸控事件，不讀畫面內容、不攔截或修改事件。Reader 的加速狀態依播放器保存，換影片／釋放時清除。

鎖屏 Provider 與 Binder 備援改在背景執行；每次新請求攜帶可撤銷 Binder 憑證。重新觸控、切背景或重新進入會撤銷尚未執行的請求，接收端在 Root 指令前再次核對憑證與五秒時效。已送入系統執行的熄屏指令無法保證撤回。只有模組執行 Root，既有 UID／包名／設定檢查繼續保留。

離線回歸包含 466 個 JVM 檢查、12 個恢復 shell 行為檢查及 15 個支援包名對照檢查；真機播放與鎖屏的完整結果見本次 UPDATE_SUMMARY。

2026-11-22 真機補充驗收：抖音換片後橫屏長按放手恢復設定 1.23x，播放進度短樣本約 1.24x。只終止模組程序後，約 2.125 秒恢復前台服務，抖音原程序接續倒數成功鎖屏；測試後原設定已還原。這是一般程序終止的單次驗證，未重測重開機或其他廠牌。

## 1.2.32 程序恢復（歷史版本）

自動鎖屏啟用時會啟動獨立 Root 守護，約每 5 秒檢查 VideoSpeed 程序，並監測服務心跳。程序被殺後會重新建立前台鎖屏服務；不需要重新開啟影片 App。守護只作用於 VideoSpeed，影片 App 不取得 Root。

本機 Android 16 實測兩次約 4.29／4.35 秒恢復，且抖音原程序接續倒數後成功鎖屏。關閉功能會停止守護及前台服務；系統設定的明確「強制停止」也會讓守護退出，重新手動開啟並維持功能啟用後才重新運作。沒有修改全域開機腳本、HMA 或 LSP 作用域。

離線測試：`python scripts/test_idle_recovery.py`（Windows 使用 Git Bash）執行真正 shell 腳本，覆蓋失去程序、心跳過期、單一實例、失效 PID、停用與強制停止。開機同步沿用 BootReceiver，本次沒有重開機驗證。

<!-- Current lifecycle: internal same-app Activity transitions preserve the last touch timestamp; real background return starts a fresh interval. 114 idle checks + 299 prior checks pass. -->
## 1.2.31 Root 鎖屏（歷史版本）

目前改用 VideoSpeed 自己的 Root 權限執行 `input keyevent 223`（電源休眠鍵）。影片 App 不申請 Root；不依賴無障礙或裝置管理員，保留系統允許的生物辨識解鎖。首次儲存啟用設定會在背景執行 `su -c id -u` 驗證，只有確認 UID 0 才保存授權狀態；授權快取存於模組自己的私有 lock_runtime 設定，沒有寫入公開倍速設定。

Root 指令有逾時限制，鎖屏在工作執行緒執行，廣播先擷取系統 UID 再 goAsync，完成後回報結果。下方無障礙與裝置管理員說明為歷史版本。舊無障礙服務重新連接時會自行退出。

Root 模式仍需鎖屏服務接收請求；不能保證所有廠商後台清理後都會恢復。一般程序終止與系統「強制停止」不同，恢復能力需看真機紀錄，不應由 START_STICKY 推定已通過。

## 1.2.29 無觸控鎖屏（歷史版本）

使用無障礙服務的系統鎖屏動作，保留系統允許的指紋／臉部解鎖。Android 9 以上需由使用者在無障礙設定啟用「無觸控自動鎖屏」。服務不要求讀取畫面內容、手勢或任何事件訂閱；觸控仍只由 LSP 作用域內的 Hook 計時。重開機或系統安全政策本來要求密碼時仍需密碼。

此版本不再呼叫裝置管理員 lockNow；新服務連接時撤銷本模組舊管理員。下方 1.2.27 的裝置管理員說明僅為歷史紀錄。Android 8／8.1 不支援本鎖屏方式，不會退回強制密碼鎖屏。

Android 14 以上啟用功能會維持鎖屏前台服務，關閉功能即停止。使用限定本套件的有序廣播，明確分享發送者 UID；接收端核對系統 UID、實際包名、支援清單及 5 秒時效，再檢查功能與授權，避免 Provider／服務查詢受限。廣播回覆代表鎖屏動作執行結果。舊系統保留具相同驗證的元件 IPC 路徑，但不使用裝置管理員鎖屏。

# 视频调速 VideoSpeed

[![GitHub stars](https://img.shields.io/github/stars/MarsGao/io.github.MarsGao.speed?style=social)](https://github.com/MarsGao/io.github.MarsGao.speed/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/MarsGao/io.github.MarsGao.speed?style=social)](https://github.com/MarsGao/io.github.MarsGao.speed/network/members)
[![GitHub release](https://img.shields.io/github/v/release/MarsGao/io.github.MarsGao.speed)](https://github.com/MarsGao/io.github.MarsGao.speed/releases)
[![License](https://img.shields.io/github/license/MarsGao/io.github.MarsGao.speed)](LICENSE.md)

视频默认播放速度调节 - 一个基于 Xposed 的 Android 模块，用于调节多个应用的视频播放速度。

> 🌟 **如果您觉得 VideoSpeed（视频调速）好用且解决了您的痛点，请在右上角帮作者点个 ⭐ Star 收藏支持一下！**
> 您的每一个 Star 都是作者持续跟进微信、B站、抖音、X/Twitter 等各主流应用新版本适配、长期无偿开源维护的最大动力！
> 🎉 作为非计算机专业学习者维护的开源项目，非常感谢开源社区与大家的鼓励和支持！

> 📘 想系统理解模块加载、跨进程配置、各应用 Hook 模式以及微信视频号主动注入原理，请阅读 [《VideoSpeed 技术全景与微信视频号适配指南》](TECHNICAL_OVERVIEW.md)。该文档也是版本适配和外部 AI 技术讨论的首要上下文。

> X 新版本上线前须执行 [X / Twitter 适配与上线验收](docs/X-RELEASE-ACCEPTANCE.md)。设备预检与 Hook 日志不能代替实际播放验收。

> X `12.28.0` / VideoSpeed `1.2.10` 真机已确认默认倍速实际生效，但原生按钮可能仍显示 `1x`；手动切到 `1.25x` 等非 `1x` 速度可生效，手动回到 `1x` 会被默认倍速覆盖。详见上述验收记录。

## ✨ 功能特性

- 🚀 支持多款主流应用：
  - 哔哩哔哩 (B站)
  - 微信视频号
  - 推特 Twitter/X
  - 抖音
  - 快手
  - 微博
  - 小红书
  - Instagram
  - Telegram

- ⚡ 智能速度调节
- 🎯 区分自动播放和手动设置
- 🔧 易于使用的设置界面

## 播放器生命周期与设置备份（1.2.26）

- 新增每 App 调速开关：关闭后，下次完整重启该 App 时跳过播放器 Hook，原独立速度仍保留。与设为 1.0x 不同；重新启用也需重启 App。启动优先读取原可读偏好，缺失时尝试 Provider，两者不可用时保留兼容的启用默认值。
- 点击「备份」可通过系统文件选择器导出 UTF-8 文本或导入。导入先验证并显示预览：「合并」保留未列出的 App 设置，「替换」清除所有支持包名的独立值及启用开关后写入；取消不修改。格式版本、包名、范围、开关值、重复键与大小限制均检查，不新增文件权限。
- 修正抖音类级回呼捕获第一次播放器：resume 使用本次实例，父层回呼仅对明确且唯一的所属播放器操作，无法解析时跳过；不使用「最近预载」播放器猜测当前目标。
- 微信触控延迟扫描采用页面任务代次与弱引用，较旧请求及离页任务失效；Finder 手动保护统一使用单调时钟。阅读 App 的 250/750ms 任务在换媒体、换 Surface 或 release 后失效。
- Bilibili 依 resume 与 float setter 特征找字段，避免固定第一字段；设速反射异常记录后不再主动重新抛出。四处旧适配路径改为单次配置快照，Telegram 日志复用同一次读取。
- 设置保存与文件读写移至单一后台队列，保存期间阻止重复提交；完成落盘及原可读性处理后才提示成功。保留原全局、独立速度、手动选择与读取优先顺序。
- 299 项 JVM 检查通过，assembleDebug / lintDebug 成功（0 errors / 18 warnings）。Android 36 模拟器验证保存/取消、开关字段、实际 Provider IPC、文件导出、导入预览/合并/替换及拒绝重复键。没有验证目标手机上的抖音/微信/阅读 App 实际播放，本轮未更新手机。

APK：`artifacts/VideoSpeed_1.2.26.apk`。本轮沿用尚未提交的 1.2.25 工作，未提交、推送或创建 Release。

## 设置操作与稳定性优化（1.2.25）

- 全局与应用编辑器新增 1.0 / 1.5 / 2.0 / 2.5 / 3.0x 快捷填入；仍需点击保存才生效。
- 应用清单支持「全部支持／已安装／独立设置」筛选，可与名称或包名搜索组合；显示当前数量及独立设置数量。「已安装」以模块可查询到的包为准，全部清单仍可预先配置。
- 「重置独立设置」先显示确认，清除全部已支持应用的独立值（含筛选外项目），保留已保存的全局速度。取消不会修改设置。
- 修复异常类型设置引发的 ClassCastException：独立值异常时继承全局，全局也异常时回退默认；有效独立值直接返回，不再读取全局。包名正则只编译一次。
- 移除微信只写未使用的播放器速度表；手动选择的 3 秒保护改用弱引用键与单调时钟，避免静态状态阻止已废弃播放器回收。
- 本版 214 项 JVM 检查通过，assembleDebug / lintDebug 通过（0 errors / 19 warnings）。新画面与实际播放尚待目标设备验收，未用编译结果代替实播。

APK：`artifacts/VideoSpeed_1.2.25.apk`。覆盖安装后重开目标应用载入新版 Hook，原全局及独立速度保留。

## 每个应用独立倍速（1.2.24）

设置页保留全局默认速度，并新增可搜索的应用清单。点击某个应用可保存专属倍速，选择「跟随全局」则移除该应用覆写；原有全局设置无须迁移，范围仍为 `0.25x ~ 4.0x`。15 个已支持包名（包括各版本变体）都能单独设置。

- 全局值继续使用 `speed` 键；独立值使用 `speed.app.<packageName>`，仍存放原 `speed.xml`。修改或重置一项不影响其他应用。
- 全部 Hook 在初始化时使用 Xposed 提供的真实包名选择设置，包含微信、抖音与两个阅读 App；子进程使用应用包名，不以进程后缀创建另一份设置。
- Provider 支持 `/speed?package=<packageName>`；原不带参数的 `/speed` 仍返回全局值。XSharedPreferences 与 Provider 都执行「有效独立值 → 全局值 → 原默认值」的解析。
- 当前已有的播放、换片、手动选速与设置文件可读性逻辑保留。修改其他应用的设置不会被误判为本应用速度改变。
- Debug 编译、lint（0 errors / 20 warnings）和 191 项 JVM 检查通过，另确认 15 个包名都有对应 UI 标签与可见性声明。本次没有连接手机，现有模拟器也无法启动，尚未完成新 UI 与逐 App 实播验收；使用者选择先取得 APK。

APK：`artifacts/VideoSpeed_1.2.24.apk`。覆盖安装后重开目标应用一次以载入新版 Hook，之后设置仍在下一个视频生效。页面展示框架已支持的应用，新增任意其他包名并不等同已适配其播放器。

## 抖音设置更新修复（1.2.23）

修复 `com.ss.android.ugc.aweme` 修改模块倍速后，下一支影片仍沿用旧值的问题。原实现只在 prepare/resume 刷新缓存，而当前抖音切片路径主要触发 setSpeed；现在每次设速都会检查配置值，模块默认值未变时仍保留原生临时手动选择。共用配置变更检测改为独立比较实际值，避免 reload 消耗文件变更标记。

2026-11-04 抖音 `33.9.0` / `330901`、VideoSpeed `1.2.23` 已由使用者确认：不重启抖音，修改设置后滑下一支会正常跟随；同 PID 日志也记录速度变化。Debug 编译、lint（0 errors / 20 warnings）与 101 项 JVM 检查通过。两个阅读 App 的 Hook 未改，68 项相关检查继续通过。证据见 `docs/DOUYIN_DEVICE_EVIDENCE_20261104.txt`。最新 APK：`artifacts/VideoSpeed_1.2.23.apk`。

## 阅读 App 视频倍速（1.2.22）

新增 `com.kylin.read`、`com.phoenix.read` 的 TTVideoEngine 适配、LSPosed 推荐作用域与包可见性声明，沿用全局 `0.25x ~ 4.0x` 设置。设备 APK 基线分别为 `7.2.5.32`、`6.5.1.32`。

- 覆盖首次播放、左下「继续观看」、预载下一集及连续换集：播放入口、Surface 建立后补设、影片来源更新、准备完成与首帧回调共同触发。换集初始化不再沿用上一集临时速度；开始渲染后保留当集临时非默认调速。
- 两包优先读取 LSPosed 的 XSharedPreferences。修正设置页只处理旧路径的问题：以框架支持的 `MODE_WORLD_READABLE` 打开单一 `speed` 偏好，并处理实际 `mFile` 指向的 `speed.xml`；非 LSPosed 平台拒绝此模式时回退私有模式。此文件只有倍速浮点数，其他 App 可读该数值；设备测试已取得使用者明确同意。
- 其他 App 保留原 Provider 优先及 XSharedPreferences 回退；未修改 HMA 规则。实验性广播接收器、私有 Provider 与固定 3x 对照代码均已移除。
- 只调整有视频 Surface 的实例，保留原参数与音高。每次首帧后最多一次 `rateProbe` 比对进度与实际时间，缓冲、暂停、seek 会影响样本；不会据此自动宣告通过。
- 2026-11-04 使用者确认 Phoenix 正常设置 `2x/3x` 均能跟随且连续换集正常，Kylin 第一支与下一支正常。日志有 Phoenix 约 `1.96x/2.01x/3.00x`、Kylin 约 `3.00x/2.98x` 的进度样本，也保留换集期间较低样本。
- Debug 编译、lint（0 errors / 20 warnings）、88 项 JVM 检查通过。未推送 GitHub，保留设备数据。跨手机、目标 App 后续版本、整机重启及纯音频听书仍需额外回归。

本机检查：设置 `JAVA_HOME` 后运行 `python -B scripts/test_reader_video_hook.py`、`python -B scripts/test_speed_config_bridge.py`。交付 APK 为 `artifacts/VideoSpeed_1.2.22.apk`，证据与完整边界见 `docs/READER_DEVICE_EVIDENCE_20261104.txt`、`UPDATE_SUMMARY_20261104_161718.md`。

## 📱 支持版本

`1.2.12` 补充作者视频列表页 `FinderProfileTimeLineUI` 的默认倍速触发，覆盖作者页选视频与上下滑动；作者视频页滑动后在 450ms、1200ms 两次尝试定位当前播放器，日志包含实际页面名。

已在 Mi 14 Pro、微信 `8.0.77 / 3160` 上覆盖安装并验证：从视频号首页进入作者页、打开作品、连续切换两条视频，均记录当前作者视频页调用 `setPlaySpeed(1.8)` 成功；debug 构建及 lint 通过。此结果确认调速触发和方法调用，尚未计时测量实际播放速度；配置文件与运行时速度读取差异仍待排查。OnePlus 13 的作者视频页路径尚未复测。

微信 `8.0.79 (3200)`：`1.2.11` 已覆盖安装到 OnePlus 13，修复主动注入的 `holder not found`，冷启动及滑动后均记录 `FinderThumbPlayerProxy.setPlaySpeed(1.75)` 成功返回。实际播放体验、连续切换稳定性及配置文件 `1.8` 与运行时 `1.75` 的差异仍待验收，暂不列入完整验证版本。

本项目基于原作者 [V-E-O](https://github.com/V-E-O) 的适配工作，支持以下应用版本：

| 应用 | 适配版本 | 兼容性 | 备注 |
|------|----------|--------|------|
| 哔哩哔哩 B站 | 7.25.0 / **3.20.4 (GP)** | ✅ 兼容新老版本 | 推荐 Google Play 版本 |
| 微信视频号 WeChat | **8.0.69 (3022 GP / 3040) ~ 8.0.77 (3160 大陆版)** | ✅ 已适配 | 视频号 feed 默认倍速主动注入，动态资源解析与通用 ViewHolder |
| 抖音 Douyin | 25.6.0 | ✅ 兼容新版本 | 含极速版 |
| 小红书 | 8.23.0.5 | ✅ 兼容新老版本 | |
| 推特 Twitter/X | **12.7.1-release.0 / Piko v3.4.0、12.27.0-prod.01** | ✅ 已适配 | 覆盖两代 Media3 混淆映射；推荐 [Piko](https://github.com/crimera/piko) |
| Instagram | 315.0.0.29.109 | ✅ 兼容新老版本 | 含 Instander |
| Telegram | - | ✅ 不上混淆兼容 | |
| 微博 Weibo | 14.6.0 | ✅ 理论兼容新老版本 | |

> 📝 **说明**: 本人主要使用的应用均来自 **Google Play**，因此主要考虑适配 Google Play 版本。如需其他版本适配，欢迎提 [Issue](https://github.com/MarsGao/io.github.MarsGao.speed/issues) 反馈！

> 💡 **推特用户推荐**: 如果你使用 Twitter/X，强烈推荐 [crimera/piko](https://github.com/crimera/piko) 项目，它提供了 X/Twitter 的 Piko patches。

## 🙏 致谢

### 原项目作者
特别感谢原项目作者 **[V-E-O](https://github.com/V-E-O)** 的 [biliSpeed](https://github.com/V-E-O/biliSpeed) 项目，为本项目提供了基础框架和灵感。

### AI 辅助开发
本项目在开发过程中得到了以下 AI 工具的大力支持：

- 🤖 **[Cursor](https://cursor.sh/)** - 智能代码编辑器
- 🧠 **[Claude Opus 4.5](https://www.anthropic.com/)** - Anthropic 的大语言模型

作为非计算机专业的学习者，正是这些优秀的 AI 工具让我有机会深入理解代码逻辑、学习 Android Hook 原理，并最终将这个项目落地。感谢 AI 时代为普通人打开的技术学习大门！

### 开源社区
感谢所有为 Xposed 生态做出贡献的开发者们！

## 📥 下载安装

### 方式一：LSPosed 模块仓库（推荐）

本模块已提交至 [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/)，审核通过后可直接在 LSPosed 管理器中搜索安装。

### 方式二：GitHub Releases

前往 [Releases](https://github.com/MarsGao/io.github.MarsGao.speed/releases) 页面下载最新版本的 APK。

### 方式三：自行构建

#### 🚀 使用 GitHub Actions 自动构建

1. **Fork 此项目** 到您的 GitHub 账户

2. **推送代码** 到 main 分支，或手动触发 Actions：
   - 访问您的仓库
   - 点击 "Actions" 标签
   - 点击 "Build Android APK" 工作流
   - 点击 "Run workflow" 按钮

3. **下载 APK**：
   - 工作流完成后，点击对应的运行
   - 在 "Artifacts" 部分下载 APK 文件

#### 🔐 设置稳定签名密钥（发布必需）

公开 Release 必须使用同一套稳定签名密钥；不要发布 CI 临时生成的 debug APK，否则已安装用户无法通过覆盖安装更新。

1. **生成密钥库**：
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```

2. **转换为 Base64**：
   ```bash
   base64 my-release-key.jks
   ```

3. **设置 GitHub Secrets**：
   - 访问您的仓库 Settings > Secrets and variables > Actions
   - 添加以下 secrets：
     - `SIGNING_KEYSTORE_BASE64`: 上面生成的 base64 字符串
     - `SIGNING_KEY_ALIAS`: 您的密钥别名
     - `SIGNING_KEY_PASSWORD`: 密钥密码
     - `SIGNING_STORE_PASSWORD`: 密钥库密码

## 💻 本地开发

### 环境要求

- Java JDK 17 或 21（CI 使用 17，当前本地发布验证使用 21）
- Android Studio Arctic Fox 或更高版本
- Android SDK API 33

### 本地构建

```bash
# 克隆项目
git clone https://github.com/MarsGao/io.github.MarsGao.speed.git
cd io.github.MarsGao.speed

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本（需要签名配置）
./gradlew assembleRelease
```

### 安装和测试

1. **启用 USB 调试**：
   - 手机设置 > 开发者选项 > USB 调试

2. **安装 APK**：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **激活模块**：
   - 打开 Xposed 管理器 (如 LSPosed)
   - 启用 "视频调速 VideoSpeed" 模块
   - 勾选需要 Hook 的目标应用
   - 重启目标应用

4. **设置速度**：
   - 打开 "视频调速 VideoSpeed" 应用
   - 输入 `0.25x` 到 `4.0x` 之间的期望速度（如 `1.5`）
   - 点击设置

5. **测试**：
   - 打开支持的应用播放视频
   - 观察播放速度是否生效

## 🔧 技术架构

- **框架**: Xposed Framework
- **语言**: Java
- **构建工具**: Gradle
- **CI/CD**: GitHub Actions

## 🧩 Xposed / LSPosed 说明

在 LSPosed 管理器中，这类应用一般称为 **模块**，英文是 **Module**；不是插件（Plugin）。本项目在 LSPosed 中显示为 **视频调速 VideoSpeed** 模块。

截至 2026-06-01，原版 LSPosed 最新正式版仍为 `1.9.2 (7024)`，但上游仓库已经归档。Android 15/16 设备后续如遇到 Xposed API 版本不足、模块提示需要 API 101、或系统服务 native hook 兼容问题，建议按维护文档迁移到仍活跃维护的 Vector / LSPosed 分支。

当前 Android 16 验证基线：

- OnePlus Ace 5: LSPosed `1.9.2 (7024) - Zygisk`, Xposed API `100`
- OnePlus 13 (`PJZ110`): Android 16、Vector/Zygisk，X `12.27.0-prod.01` 已验证 `1.0x → 1.75x` 的设速与 `prepare` 补设；微信 `8.0.72 (3085)` 已验证视频号 `setPlaySpeed(1.75)`
- Mi 14 Pro (`23116PN5BC`): Android 16、Vector/Zygisk，X `12.27.0-prod.01` 已验证 `1.0x → 2.2x` 的设速与 `prepare` 补设日志
- VideoSpeed: `1.2.10`
- Twitter/X: `12.7.1-release.0` / Piko `v3.4.0`、`12.27.0-prod.01`
- X `12.27.0-prod.01` 已在 Mi 14 Pro 真机触发 `d0.c(v0)` 设速与 `d0.b()` prepare 补设；后续上游播放器重构仍须按此流程复测
- `sing-box` 与 `OPCameraPro` 等模块提示需要 Xposed API `101` 时，按维护文档评估是否迁移 Vector，不建议在核心功能正常时盲目替换框架。

## 🎯 Hook 策略

项目采用多重 Hook 策略确保兼容性：

1. **通用播放器 Hook**: 动态查找播放器相关类
2. **腾讯视频SDK Hook**: 支持 TXVodPlayer、TXLivePlayer 等底层播放器
3. **方法签名 Hook**: 匹配所有可能的播放速度设置方法
4. **系统MediaPlayer Hook**: 作为兜底方案
5. **智能判断**: 通过调用栈分析区分自动播放和手动设置

## 📋 更新日志

### v1.2.10 (2026-09-22)

**🔧 X `12.27.0-prod.01` Media3 适配**

- ✅ 适配当前 Media3 混淆播放器 `androidx.media3.exoplayer.d0` 与播放参数 `androidx.media3.common.v0`
- ✅ Hook `c(v0)` 播放参数入口，并在 `b()` prepare 后补设默认速度
- ✅ 增加 `Application.attach` 后的 ClassLoader 重试和方法级去重，兼容播放器类延迟可见且避免重复 Hook
- ✅ Mi 14 Pro / Android 16 / Vector 真机验证 `1.0x → 2.2x` 与 prepare 补设均已命中
- ✅ 构建基线升级至 Java 11 字节码，并清理 AGP 8.7 的 Manifest namespace 警告
- ✅ 设置页与跨进程配置读取统一限制为 `0.25x ~ 4.0x`，拒绝非有限值和异常范围

### v1.2.9 (2026-08-30)

**🔧 微信 8.0.77 (3160 大陆版) 适配与动态资源架构重构**

- ✅ **动态资源 ID 解析**：彻底替换硬编码十进制 ID，改用 `Resources.getIdentifier(...)` 动态解析 `id/m6e` 与 `id/e_k`，并辅以 DecorView 递归扫描兜底，无缝自适应后续版本编译
- ✅ **通用 ViewHolder 与布局解耦**：彻底消除对混淆类名 `me5.s0` 的硬依赖，通用探查 `yr5.s0` 等各类 `RecyclerView.ViewHolder` 中的 `FinderVideoLayout` 及播放器
- ✅ **倍速菜单 UIC 支持**：适配微信 8.0.77 的 `FinderSpeedControlUIC` 点击拦截
- ✅ **构建系统升级**：升级 AGP 至 `8.7.0` 与 Gradle `8.9`，全面适配系统 JDK 21 环境
- ⭐ **社区支持**：欢迎各位 Xposed / LSPosed 社区开发者与使用者体验并给予 ⭐ **Star** 支持！

### v1.2.8 (2026-08-23)

**🔧 X `12.7.1-release.0` Media3 适配**

- ✅ 适配 X 随 APK 打包的 Media3 `1.9.3` 混淆播放器 `androidx.media3.exoplayer.i1`
- ✅ Hook 混淆后的 `prepare`、播放参数和直接设速入口，在 `prepare` 后补设模块配置的速度
- ✅ 保留旧版 X/Piko 与已知 Media3 类名作为回退路径
- ⚠️ 新版本 X 采用混淆实现；每次上游播放器重构后均需以真机日志和播放页复测为准

### v1.2.6 (2026-06-25)

**🔧 Provider 初始化时机修复**

- ✅ 当模块加载晚于目标进程 `Application.attach()` 时，使用 `ActivityThread.currentApplication()` 获取上下文，保证 Provider 桥接仍可用

### v1.2.5 (2026-06-25)

**🔧 Android 16 跨进程配置桥接修复**

- ✅ 使用只读 `ContentProvider` 向所有 Hook 目标进程提供倍速配置，不再依赖跨 UID 读取私有 `speed.xml`
- ✅ 微信视频号与其他受支持应用统一优先读取 Provider；旧 `XSharedPreferences` 仅作为兼容回退
- ✅ 微信读取失败时统一回退到产品默认的 `1.5x`，不再隐式使用 `2.0x`

### v1.2.4 (2026-06-19)

**🔧 跨进程速度配置可见性修复**

- ✅ 修正应用数据目录、`shared_prefs` 与 `speed.xml` 的最小读取/穿越权限
- ✅ 解决设置页已写入新速度，但微信进程的 `XSharedPreferences` 仍读取旧值或兜底值的问题
- ✅ 保持应用数据根目录不可被其他进程列出，避免过度放宽权限

### v1.2.3 (2026-06-19)

**🔧 微信视频号默认倍速稳定性修复**

- ✅ 适配微信 `8.0.69` / `versionCode 3040` 视频号 feed 流
- ✅ 进入视频号和滑到下一个视频后，主动对当前 `FinderThumbPlayerProxy` 调用 `setPlaySpeed`
- ✅ 支持 BiliSpeed 设置页调整默认倍速，已验证 `2.0x` 和 `1.5x` 均可生效
- ✅ 增加手动倍速菜单点击冷却，避免用户临时手动调速后被立即覆盖
- ✅ 修复设置页保存与 XSharedPreferences 可读性问题，提高跨进程读取稳定性

### v1.2.1 (2026-06-01)

**🔧 Twitter/X Piko 适配更新**

- 新增 Media3 / ExoPlayer 播放器 Hook，适配 X `11.81.0-release.0` / Piko `v3.4.0`
- 拦截 `setPlaybackSpeed(1.0f)` 和 `setPlaybackParameters(...)` 的自动重置，恢复模块默认倍速
- 在 `prepare`、`play`、`setPlayWhenReady(true)`、`setMediaItem(s)` 后补设播放速度
- 保留旧版 Twitter/X Hook 作为 legacy fallback，并增加异常隔离和日志前缀

### v1.2.0 (2025-12-01)

**🔄 重大更新：包名重构**

- **包名变更**: `com.veo.hook.bili.speed` → `io.github.MarsGao.speed`
  - 符合 [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/) 提交要求
  - 使用规范的命名空间避免冲突
- **项目名称统一**: "Bili调速" → "视频调速 VideoSpeed"
  - 更准确反映项目功能（支持多个视频应用，不限于B站）
- **APK 命名**: `biliSpeed_*.apk` → `VideoSpeed_*.apk`

> ⚠️ **注意**: 由于包名变更，升级前请先卸载旧版本，然后在 LSPosed 中重新激活模块。

### v1.1.9 (2025-11-30)

**🔧 微信视频号速度设置逻辑修复**

- **问题诊断**: 通过LSPosed日志分析，发现 `isManualSpeedChange()` 函数存在**误判**问题
  - Hook成功且被正确触发
  - 但 `dispatch` 等常见Android方法名被误判为用户手动操作
  - 导致自动速度设置被错误跳过

**修复内容**:
- ✅ **改进调用栈检测逻辑**: 
  - 移除 `dispatch` 等误导性关键词检测
  - 仅检测明确的用户交互方法: `onClick`, `onTouchEvent`, `performClick`
  - 添加调用栈日志输出，便于调试
- ✅ **新增速度设置缓存机制**:
  - 使用 `ConcurrentHashMap` 追踪每个播放器实例的速度状态
  - 新增冷却期机制 (3秒)，避免用户手动调整后被自动覆盖
  - 智能判断：目标速度为1.0时不做修改
- ✅ **Telegram Hook 兼容性修复**:
  - 新增多版本方法签名兼容
  - 支持 `VideoPlayer.setPlaybackSpeed` 备选方案
  - 修复 `NoSuchMethodError` 异常

### v1.2.2 (2026-06-04)

**🔧 微信视频号 8.0.69 GP 兼容性修复**

- ✅ 新增微信候选播放器类加载探针，记录 Finder、video、player、liteav、thumb、play 相关类命中
- ✅ 新增候选播放器类通用方法扫描，自动 Hook `setRate` / `setSpeed` / `setPlaySpeed` / `setPlaybackSpeed` 等 float 倍速方法
- ✅ 播放 `start` / `play` / `resume` / `prepare` 后主动补设目标倍速
- ✅ LiteAV 路径不再只依赖入参 `1.0f`，普通播放初始化速度会自动纠正为目标倍速
- ✅ 收紧手动倍速判断，只有明确点击、触摸或速度 UI 调用栈才视为用户手动修改

### v1.1.8 (2025-11-30)

**🔧 微信视频号兼容性修复**

- **问题诊断**: 通过日志分析发现 `FinderThumbPlayerProxy.setPlaySpeed` 虽然Hook成功，但从未被调用
- **根本原因**: 微信视频号使用腾讯视频SDK (liteav) 作为底层播放器

**新增功能**:
- ✅ 新增腾讯视频SDK (liteav) Hook支持
  - `TXVodPlayer.setRate` - 点播播放器
  - `TXLivePlayer.setRate` - 直播播放器
  - ExoPlayer 备用方案
- ✅ 新增播放开始时设置速度 (`startVodPlay`、`resume`、`start`、`play`)
- ✅ 新增系统 `MediaPlayer.setPlaybackParams` Hook作为兜底
- ✅ 扩展了播放器类列表，包括 Kinda 框架视频组件
- ✅ 增强日志输出，便于问题诊断

**支持的播放器类**:
- FinderThumbPlayerProxy, FinderVideoPlayer, FinderVideoCore
- SnsVideoPlayer, MMVideoPlayer, AppBrandVideoPlayer
- TXVodPlayer, TXLivePlayer (腾讯视频SDK)
- android.media.MediaPlayer (系统播放器)

### v1.1.7

- 初始微信视频号支持
- 多策略Hook架构

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

- 🐛 **发现 Bug？** 请提交 [Issue](https://github.com/MarsGao/io.github.MarsGao.speed/issues)
- 💡 **有新想法？** 欢迎讨论和建议
- 🔧 **想要贡献代码？** 欢迎提交 PR

## 📄 许可证

本项目采用 [GPL-3.0](LICENSE.md) 许可证。

## ⚠️ 免责声明

本项目仅用于学习和研究目的，请遵守相关法律法规。使用本模块造成的任何后果由使用者自行承担。

---

<p align="center">
  🌟 如果这个项目对你有帮助，请在 GitHub 右上角点一个 <b>⭐ Star</b> 鼓励作者！<br>
  Made with ❤️ by <a href="https://github.com/MarsGao">MarsGao</a>
</p>
# 1.2.27：無觸控自動鎖屏

設定首頁新增「無觸控自動鎖屏」，預設關閉，可設定 **1～240 分鐘（整數）**。
勾選啟用並儲存後，首次需在 Android 系統畫面確認裝置管理員授權；只要求鎖定螢幕權限。

- 僅在本專案支援、且 LSPosed 實際載入模組的 App 前台計時。首次更新後請完全重啟目標 App。
- 手指觸控會重算等待時間；長按期間不鎖屏，最後一指放開或觸控取消後重新計時。
- 支援直屏、橫屏與原生對話框；切換 Activity 會重新計時。離開 App、失去視窗焦點或被系統視窗覆蓋時停止。
- 與每個 App 的「啟用調速」開關獨立；即使停用調速，仍可使用鎖屏功能。
- 分鐘數調整後，切回目標 App 即載入新值。關閉功能不會自動撤銷系統授權，可從設定對話框的「管理鎖屏授權」進入系統安全性設定撤銷；移除 App 前請先停用裝置管理員。
- 裝置管理員鎖屏後通常需 PIN／圖形／密碼解鎖；沒有設定安全鎖時只會熄屏。Android 文件：[DevicePolicyManager.lockNow](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow())。

備份會包含已設定的 `idle.enabled`、`idle.minutes`。新版本可讀取舊備份；舊檔未列出鎖屏設定時，合併或替換都保留目前的鎖屏設定。缺少系統授權時，匯入啟用值仍保持關閉。含新欄位的備份應使用 1.2.27 或更新版本匯入。

鎖屏請求使用獨立 Provider；若系統限制跨 App 元件存取，會記錄失敗並停止該輪請求，下次觸控後重新等待完整時間，不會密集重試。系統原有的螢幕逾時仍依系統設定運作。

驗證：新增 `python scripts/test_idle_lock.py`，85 項 JVM stub 檢查涵蓋實際 Hook 回呼、前台計時、長按、橫屏 Activity 交替、對話框、背景取消、設定驗證、備份、呼叫者 UID 及鎖屏授權。這些檢查不代表真機跨 App IPC 或實際鎖屏已通過。
