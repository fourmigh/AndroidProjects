# 影视浏览器（WebFilm）

一个只用来浏览 **茶杯狐（1cbh.cc）** 的 Android 网页壳：原生底部导航（栏目从网页读取）+ WebView，
隐藏原站顶部导航、公告、广告、页脚和下载推广，只保留影片列表与播放器。

> 仅建议自用安装，不建议上架应用商店。App 不存储、不下载、不修改任何视频资源，
> 视频内容由站点提供，版权归其合法持有人所有。

## 功能

- **底部栏目动态读取**：从原站导航解析「首页 / 电影 / 电视剧 / 综艺 / 动漫 / 短剧…」，
  按栏目名自动匹配图标
- **可配置底部栏**：在「我的 → 栏目」勾选要显示的栏目、拖动右侧手柄调整顺序、拖动透明度；
  显示数量按屏幕宽度自动计算（每个栏目至少 50dp），默认只勾选能放下的数量；
  启动和返回都会使用排序后的第一个勾选栏目
- **精简页面**：隐藏原站 header、公告条、页脚、广告位、下载 App 推广
- **半透明底部栏**：页面延伸到导航栏后面，底部自动留出等高空间，滚到底时内容不被遮挡、可点击
- **广告拦截**：隐藏广告位、拦截 `ads.min.css` 广告样式（不拦 `/iyads/js` 等播放器依赖脚本）
- **全屏横屏**：站内播放器全屏时自动转横屏，退出恢复竖屏
- **观看历史**：每部剧一条记录，内部保存每一集的进度；历史里显示最后看到的集数和
  `00:30 / 02:00` 进度条；记录会记住上次使用的播放器，点击直接用它续播，长按可切换播放器；
  同一集换线路不会丢进度
- **流量统计**：在「我的 → 流量」查看今日 / 本周 / 本月 WiFi 与移动数据用量；
  系统提供本 App 的每日 bucket 时额外显示月历，否则只显示汇总；当前网络显示 WiFi / 4G / 5G
  （需要系统「使用情况访问」和电话权限，4G/5G 历史用量仍合并统计）
- **站外拦截**：非当前站点的主框架跳转、弹窗、APK 下载全部拦截
- **外部播放器 / 投屏**：从播放页解析真实 m3u8，可交给 MX Player、VLC 等外部应用
- **播放方式菜单**：点线路里的某一集，可选「App 内播放（WebView）/ Media3 / 外部播放器 / 复制链接」
- **Media3 播放器**：内置 ExoPlayer，支持进度条、快退/快进 10 秒、倍速、全屏
- **默认播放器接管**：默认播放器为 Media3 / 外部播放器时，网页里点线路、集数、立即播放会直接交给对应播放器；
  选「App 内」时仍用网页播放器
- **可改域名**：站点换域名时在「我的 → 设置」里修改，无需重新打包

## 工程结构

```
WebFilm/
├── app/        # 主 App：WebView、底部栏目、设置
├── traffic/    # 可复用流量统计模块：查询、月历 UI、权限、文案
└── gradle/     # 版本目录
```

播放器来自同级的独立工程 `../PlayerKit/player`，通过 `projectDir` 引用：

```
PlayerKit/
└── player/     # Media3、默认播放器、播放方式菜单、观看历史
```

控件来自同级的独立工程 `../ComposeWidgets/`，通过 `projectDir` 引用：

- `widgets-theme`：集中维护控件配色，`WidgetTheme` / `LocalWidgetColors`；
- `widgets-segmented`：N 选一控件 `SegmentedSelector`；
- `widgets-bottom-nav`：图标 + 文字的底部导航 `BottomNavBar`。

`traffic/` 对外提供两层：

- `TrafficStore`：流量查询、权限检查、网络监听、格式化（无 UI）；
- `TrafficPanel()`：默认界面（汇总、月历、单日详情、授权引导），用宿主的 `MaterialTheme`。

`player/` 对外提供：

- `Media3PlayerActivity` / `PlayerLauncher`：内置 ExoPlayer 播放；
- `PlayerPreferences` + `DefaultPlayerSetting()`：默认播放器设置；
- `PlaybackMethodDialog()`：播放方式菜单；
- `PlayerProgressBus`：把播放进度回传给宿主的观看历史；
- `history/`：`HistoryEntry` / `HistoryStore` / `HistoryPanel`，历史存储、每集进度和列表 UI。

模块的 `AndroidManifest.xml` 声明了 `ACCESS_NETWORK_STATE`、`READ_PHONE_STATE`、
`PACKAGE_USAGE_STATS`，构建时自动合并到主 App；主 App 只保留 `INTERNET`。
播放器模块本身不需要额外权限（网络播放共用 `INTERNET`，外部播放器走 Intent）。
界面文案都在各模块的 `res/values/strings.xml`。

## 构建与安装

```powershell
# 在仓库根目录
.\install.bat WebFilm            # debug
.\install.bat WebFilm release    # release
```

或单独构建：

```powershell
cd WebFilm
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

- minSdk 26，targetSdk 36
- 复用仓库共享签名 `keystore/shared.p12`
- 包名 `org.caojun.webfilm`

## 站点适配说明

站点是 Maccms 风格的定制模板，页面结构改版时主要改这几个地方：

### 1. 页面精简：`web/PageInjector.kt`

- `CSS_RULES`：隐藏原站顶部导航 `.myci-header__top`、页脚、`iyads` 广告、PC 侧栏
- `INJECT_JS`：按文本隐藏「公告通知」「下载 App」等推广，并把 `target="_blank"`
  改成当前页打开（否则点击影片没反应）
- 选择器失效时，用 Chrome 打开 `chrome://inspect` 调试 WebView，找到新的 class 后替换

### 2. 底部栏目：`PageInjector.EXTRACT_NAV_JS`

- 扫描 `.myci-header__menu a`、`.cms-seo006-h5-header a` 等原站导航链接
- 按路径去重，只保留站内链接，回传给 `BrowserController.onNav`
- 栏目名按关键词映射图标（首页 / 电影 / 电视剧 / 综艺 / 动漫 / 短剧 / 资讯 / 专题）
- 结果缓存在 `SharedPreferences`，启动时先用缓存、首页加载后再刷新
- 站点改导航结构时，调整这段选择器即可

### 3. 视频地址解析：`PageInjector.EXTRACT_LINES_JS`

播放页把线路放在全局变量 `temLineList` 中，`file` 字段格式为：

```
3 位随机前缀 + Base64(URL 编码后的地址)
58jaHR0cHM6...  ->  aHR0cHM6...  ->  https%3A%2F%2F...  ->  https://...
```

脚本也支持从 `temPlayRenderCode` 的 iframe `url=` 参数兜底。
站点若更换播放器模板，需要同步调整这段解析逻辑。

## 已知限制

- **外部播放器可能 403**：部分 CDN 校验 `Referer`，外部播放器不带站点 Referer 时无法播放；
  此时请继续用站内播放，或复制链接到支持自定义请求头的播放器。
- **投屏依赖外部应用**：App 自身不实现 DLNA，通过系统选择支持投屏的播放器完成。
- **iframe 内广告无法完全隐藏**：跨域 iframe 不受页面 CSS 影响，只能靠禁止新窗口。
  另外不能拦截 `/iyads/js` 下的脚本——播放器模板依赖其中的通用库，拦了会黑屏。
- **跨域 iframe 线路无法记录进度**：部分线路把 DPlayer 放在 `dxfbk.com` 的 iframe 里，
  主文档读不到 `<video>`，这类线路只记录集数；主文档播放的线路和 Media3 播放可以记录进度和续播。
- **域名可改但路径规则固定**：分类路径来自网页导航，换到不同模板的站点时会重新读取；
  但读取失败时只保留「首页」，不再展示可能过期的内置分类。
- **底部栏显示数量随屏幕变化**：勾选超过当前屏幕能放下的数量时，底部只显示排序靠前的部分，
  「栏目」页会显示当前上限。
- **流量统计的 4G/5G 合并**：Android 系统只按 WiFi / 移动数据统计，无法拆分 4G 和 5G 的历史用量；
  当前网络制式通过 `READ_PHONE_STATE` 读取，双卡、VPN、部分厂商 ROM 的统计可能偏差，页面标注「仅供参考」。
- **没有每日数据时只显示汇总**：部分 ROM 只保留当天或按大桶聚合，App 会隐藏月历，
  只显示今日 / 本周 / 本月，避免展示错误的每日数据。
- **站点改版会失效**：所有适配都集中在 `PageInjector.kt`，便于快速修复。

## 技术要点

- 单 Activity + Compose + 常驻 WebView
- 开放 JS / DOM Storage / 第三方 Cookie / 混合内容，保证第三方解析播放器能工作
- `onShowCustomView` 实现播放器全屏与横屏
- `JavascriptInterface` 回传页面标题、m3u8 线路和网页导航
- 底部栏目顺序、勾选、透明度存 `SharedPreferences`，数量按屏幕宽度计算
- 栏目排序用 `sh.calvin.reorderable` 的拖拽手柄，松手后保存
- 启动与返回目标取排序后的第一个勾选栏目，不特殊对待「首页」
- 可选播放器用 Media3 / ExoPlayer（`media3-exoplayer` + HLS + UI），请求头带 Referer 和移动端 UA
- 观看进度：WebView 播放时读取主文档 `<video>`，Media3 播放时读取 `ExoPlayer.currentPosition`，
  都写入同一份历史；同一集不同线路共用进度
- 每条历史记录 `lastPlayer`（web / media3 / external）和当前线路 m3u8：
  点击历史直接用上次的播放器；长按历史弹出播放方式菜单
- 播放器模块 `:player` 自带 Media3 依赖，主 App 只依赖模块；播放进度通过 `PlayerProgressBus` 回传
- 控件配色集中在 `ComposeWidgets/theme` 的 `WidgetTheme`，默认跟随宿主 `MaterialTheme`；
  `ComposeWidgets/segmented` 的 `SegmentedSelector` 用于「我的」顶部页签和默认播放器，
  `ComposeWidgets/bottom-nav` 的 `BottomNavBar` 用于底部导航
- 页面延伸到底部栏后面，通过注入 `body { padding-bottom }` 让内容能滚到导航栏上方
- 流量按天查询、按 UID 统计，整月结果缓存，避免部分 ROM 的跨天 bucket 把整月流量堆到 1 号
- 先用 `queryDetailsForUid` 探测本 App 最近 14 天是否有跨天 bucket：
  有则显示月历，没有则只显示今日 / 本周 / 本月汇总
- 当前网络类型用 `ConnectivityManager` + `TelephonyManager.dataNetworkType` 判断，需要 `READ_PHONE_STATE`
- 历史记录用 `SharedPreferences` + JSON，最多 100 条
