# QuotaMonitor（套餐限额）

手机端查看 **OpenCode Console** 与 **火山引擎 Coding Plan** 套餐限额的 Android 应用。

## 工作原理

两个控制台都是需要登录的 SPA 且没有公开的免登录查询 API，因此本应用采用
**「内置 WebView 复用登录态 + 平台专属精准解析 + 原生仪表盘展示」** 方案：

- **登录态复用**：首次在内置网页里登录一次（Cookie / localStorage 持久化在本机），
  之后打开 App 静默刷新即可。
- **双通道采集**：
  - 页面文档创建时注入 fetch / XHR 拦截器，把疑似额度 JSON 响应回传；
  - 页面加载完成后扫描 DOM（进度条元素 + 全部文本行），SPA 渲染慢时自动重扫多轮。
- **平台专属精准解析**（关键：不做通用启发式猜测，杜绝把宣传页文案当额度）：
  - **OpenCode**：必须命中 `n% used` 模式，再定位
    `5-hour window` / `Weekly` / `Monthly` 三块，提取各自的 `Resets in …` 与百分比；
    宣传页（`Get started`、`60% off`、`Monthly usage $60 $120` 等）→ 判定「需登录」。
  - **火山引擎**：必须命中 `近5小时用量` / `近一周用量` / `近一月用量` 之一，
    提取 `…后重置` 倒计时与大号百分比（自动忽略 `0% 50% 100%` 刻度行），
    并解析左侧 `Coding Plan … 套餐`、`剩余时间`、`结束时间`；
    登录页 / 其他页面 → 判定「需登录」。
  - 网络 JSON 仅在 DOM 已确认额度页后作为补充，且指标名必须命中已知清单
    （`5-hour window`、`近一周用量` 等），否则一律丢弃。
- **需登录判定**：URL 含 `login/auth`、密码框、页面登录文案、营销页特征
  （`Get started` / `the cheapest inference` 等）任一命中即显示「需登录」。
- **秒开缓存**：成功快照写入 SharedPreferences（`quota_cache_v2`），
  下次启动立即展示上次数据与时间。

## 展示内容

与网页控制台一致：

- **OpenCode**：套餐名（Go 等）、`5-hour window` / `Weekly` / `Monthly`
  三项进度条 + 各自的 `Resets in …` 重置倒计时 + `n% used`。
- **火山引擎**：`Coding Plan Lite 套餐`、`剩余 28天 · 结束于 …`，
  `近5小时用量` / `近一周用量` / `近一月用量` 三项进度条 + `…后重置` 倒计时 + 百分比。

## 界面

- **仪表盘**（默认页）：每个平台一张卡片 —— 状态徽标（未同步 / 同步中 / 需登录 /
  已同步 / 失败）、套餐名、每项额度的进度条（<70% 绿、70~90% 橙、≥90% 红）、
  重置时间、更新时间与数据来源，底部「刷新 / 打开网页」按钮。
- **内置控制台网页**：与仪表盘共享同一个常驻 WebView 和登录态，
  用于首次登录、核对原始页面；顶栏提供「刷新」与「桌面版 / 移动版」UA 切换
  （默认桌面 UA，云控制台在移动 UA 下经常排版不全）。

## 架构

```
app/src/main/java/org/caojun/quotamonitor/
├── MainActivity.kt              # 入口：仪表盘 ↔ 控制台网页 两态切换
├── model/QuotaModels.kt         # Platform / QuotaItem / QuotaSnapshot / PlatformState
├── data/
│   ├── JsScripts.kt             # 注入脚本：fetch+XHR 钩子、DOM 扫描
│   ├── QuotaParser.kt           # 网络 JSON 与 DOM 结果 → 快照（启发式，双通道合并）
│   └── QuotaCache.kt            # SharedPreferences 快照缓存
├── viewmodel/QuotaController.kt # 常驻 WebView 生命周期、提取流水线、状态机
└── ui/
    ├── DashboardScreen.kt       # 原生仪表盘（卡片 + 进度条）
    ├── ConsoleWebScreen.kt      # 内置控制台网页
    └── theme/Theme.kt           # Material 3 主题与进度条配色
```

要点：

- 每个平台一个**常驻 WebView**，平时挂在全尺寸、不可见、不拦截触摸的
  `HiddenWebFrame` 中继续加载与提取；打开控制台网页时把同一个 WebView
  重新挂到前台容器（登录态天然共享），关闭后移回隐藏容器。
- 状态机：`IDLE → LOADING → OK / NEED_LOGIN / ERROR`，
  单轮刷新 30 s 超时；需登录判定结合 URL 特征、密码框与页面登录文案。
- 隐藏 WebView 置于不透明列表内容下层，用户完全无感知。

## 构建与测试

```powershell
cd QuotaMonitor
.\gradlew.bat assembleDebug          # 调试包（~11 MB）
.\gradlew.bat testDebugUnitTest      # 解析器单元测试（12 个）
# 或在工作区根目录：
.\install.bat QuotaMonitor           # 构建 + 安装 + 启动（需连接设备）
```

签名使用工作区统一的 `keystore/shared.p12`（相对路径 `../keystore/keystore.properties`）。

## 说明与限制

- 额度数据通过**平台专属规则**从控制台网页提取（特征不匹配时宁可显示「需登录」，
  也不展示猜测数据）；控制台改版后可能需要调整 `JsScripts.kt` / `QuotaParser.kt`。
- 登录状态（Cookie / localStorage）仅保存在本机 WebView 数据目录，不上传。
- APK 仅带 arm64-v8a（与工作区其他工程一致），x86 模拟器无法运行。
