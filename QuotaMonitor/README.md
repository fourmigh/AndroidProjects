# QuotaMonitor（套餐限额）

手机端查看 AI 套餐限额的 Android 应用。**内置 OpenCode Console 与火山方舟 Coding Plan，
新增其他 AI 套餐只需在配置向导里填写 URL，无需改代码。**

## 工作原理

控制台都是需要登录的 SPA，因此采用 **「内置 WebView 复用登录态 + 配置驱动的规则引擎 + 原生仪表盘」**：

- **登录态复用**：首次在内置网页里登录一次（Cookie / localStorage 持久化在本机），
  之后打开 App 静默刷新即可。
- **双通道采集**：页面文档创建时注入 fetch / XHR 拦截器回传疑似额度 JSON；
  页面加载完成后扫描 DOM（进度条 + 文本行），SPA 渲染慢时自动重扫多轮。
- **配置驱动的规则引擎**（`QuotaRules`）：按 `PlatformConfig` 中声明的指标规则提取，
  页面不含任何已配置指标时返回 null → 判定「需登录」，绝不盲猜数字。
- **自动识别向导**：新平台由 App 自动推导规则（见下），用户无需编写正则。
- **秒开缓存**：快照按配置 id 写入 SharedPreferences，启动即展示上次数据。

## 新增一个 AI 套餐（无需改代码）

仪表盘右上角 **设置 → 新增平台**：

1. 填写 URL（显示名称可选）→ **打开页面并登录**
2. 额度卡片显示后点 **自动识别**
3. App 扫描页面，识别出疑似额度指标（同行百分比 / 下方大号数字 / 已用总量），
   自动推导正则与重置倒计时
4. 勾选正确项、按需修改显示名 → **保存平台**

保存前自动校验（URL、至少一个指标、正则可编译），保存后立即刷新验证。

设置页还支持：启用开关、编辑（含高级规则）、删除、恢复内置默认、导出（复制到剪贴板）、导入。

### 自动识别覆盖的页面形态

| 形态 | 例子 |
|---|---|
| 同行百分比 | `5-hour window … 29% used` |
| 指标下方独立数字 | `近一周用量` → `13.51` / `13.51 %` |
| 已用 / 总量 | `45,300 / 90,600`、`1234 of 5000` |
| 进度条元素 | `role=progressbar` 等 + 邻近标签 |

识别不到时会提示确认「已登录且额度卡片已显示」；可退回页面继续操作再识别。

## 展示内容

- **OpenCode**：套餐名（Go 等）、`5-hour window` / `Weekly` / `Monthly`
  三项进度条 + 各自的 `Resets in …` 重置倒计时 + `n% used`（与网页小数位一致）。
- **火山方舟**：`Coding Plan Lite 套餐`、`剩余 28天 · 结束于 2026-12-20 23:59`，
  `近5小时用量` / `近一周用量` / `近一月用量` 三项进度条 + `…后重置` 倒计时 + 百分比。
- 说明文字固定在屏幕底部，与底边留一个字的高度。

## 界面

- **仪表盘**：按配置列表渲染卡片 —— 状态徽标（未同步 / 同步中 / 需登录 / 已同步 / 失败）、
  套餐名、每项额度进度条（<70% 绿、70~90% 橙、≥90% 红）、重置时间、更新时间与数据来源。
- **配置向导 / 设置**：新增平台、管理配置（见上）。
- **内置控制台网页**：与仪表盘共享同一 WebView 和登录态；顶栏提供「刷新」与
  「桌面版 / 移动版」UA 切换（云控制台在移动 UA 下经常排版不全）。

## 架构

```
app/src/main/
├── assets/default_platforms.json     # 内置默认平台配置（含提取规则）
├── java/org/caojun/quotamonitor/
│   ├── MainActivity.kt               # 导航：仪表盘 / 设置 / 编辑 / 向导 / 控制台
│   ├── model/
│   │   ├── PlatformConfig.kt         # PlatformConfig / MetricRule / DetectedMetric
│   │   └── QuotaModels.kt            # QuotaItem / QuotaSnapshot / PlatformState
│   ├── data/
│   │   ├── JsScripts.kt              # 注入脚本：fetch+XHR 钩子、DOM 扫描
│   │   ├── QuotaRules.kt             # 配置驱动的规则引擎（提取 / 合并 / 登录判定）
│   │   ├── QuotaAutoDetector.kt      # 自动识别：候选指标 + 规则自动推导
│   │   ├── PlatformStore.kt          # 配置持久化、校验、导入导出、恢复默认
│   │   └── QuotaCache.kt             # 快照缓存（key = 配置 id）
│   ├── viewmodel/QuotaController.kt  # WebView 生命周期、配置 CRUD、提取流水线
│   └── ui/
│       ├── DashboardScreen.kt        # 仪表盘（固定底部说明）
│       ├── AddPlatformWizardScreen.kt# 配置向导（URL → 登录 → 自动识别 → 保存）
│       ├── PlatformEditScreen.kt     # 配置编辑（指标规则 + 高级规则）
│       ├── SettingsScreen.kt         # 平台列表与导入导出
│       ├── ConsoleWebScreen.kt       # 内置控制台网页
│       └── theme/Theme.kt
└── res/values/strings.xml            # 全部界面文案
```

要点：

- 每个平台一个**常驻 WebView**，平时挂在全尺寸、不可见、不拦截触摸的 `HiddenWebFrame` 中；
  打开控制台网页时把同一 WebView 挂到前台（登录态天然共享），关闭后移回。
- 状态机：`IDLE → LOADING → OK / NEED_LOGIN / ERROR`，单轮刷新 30 s 超时。
- **所有界面文案在 `strings.xml`**；规则与 JSON 字段名属于解析数据，留在代码/配置文件中。

## 构建与测试

```powershell
cd QuotaMonitor
.\gradlew.bat assembleDebug          # 调试包（~11 MB）
.\gradlew.bat testDebugUnitTest      # 规则引擎 / 自动识别 / 校验 / 格式化（18 个用例）
# 或在工作区根目录：
.\install.bat QuotaMonitor           # 构建 + 安装 + 启动（需连接设备）
```

签名使用工作区统一的 `keystore/shared.p12`（相对路径 `../keystore/keystore.properties`）。

测试用例直接使用 `assets/default_platforms.json`（通过 `sourceSets.test.resources` 引入），
默认配置改动会立即反映在回归测试里。

## 说明与限制

- 自动识别依赖页面已渲染且已登录；极端结构的页面可能需要退回页面重新识别，
  或在编辑页微调规则。
- 控制台改版后若规则失效，可用「重新自动识别」覆盖规则，无需改代码。
- 登录状态（Cookie / localStorage）仅保存在本机 WebView 数据目录，不上传。
- APK 仅带 arm64-v8a（与工作区其他工程一致），x86 模拟器无法运行。
