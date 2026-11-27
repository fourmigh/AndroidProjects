# 茶杯狐（CupFox）

一个只用来浏览 **茶杯狐（1cbh.cc）** 的 Android 网页壳：原生底部导航（栏目从网页读取）+ WebView，
隐藏原站顶部导航、公告、广告、页脚和下载推广，只保留影片列表与播放器。

> 仅建议自用安装，不建议上架应用商店。App 不存储、不下载、不修改任何视频资源，
> 视频内容由站点提供，版权归其合法持有人所有。

## 功能

- **底部栏目动态读取**：从原站导航解析「首页 / 电影 / 电视剧 / 综艺 / 动漫 / 短剧…」，
  按栏目名自动匹配图标
- **可配置底部栏**：在「我的 → 栏目」勾选要显示的栏目、用箭头调整顺序、拖动透明度；
  显示数量按屏幕宽度自动计算（每个栏目至少 50dp），默认只勾选能放下的数量
- **精简页面**：隐藏原站 header、公告条、页脚、广告位、下载 App 推广
- **半透明底部栏**：页面延伸到导航栏后面，底部自动留出等高空间，滚到底时内容不被遮挡、可点击
- **广告拦截**：隐藏广告位、拦截 `ads.min.css` 广告样式（不拦 `/iyads/js` 等播放器依赖脚本）
- **全屏横屏**：站内播放器全屏时自动转横屏，退出恢复竖屏
- **观看历史**：自动记录播放页，点击继续观看，支持单条删除 / 清空
- **流量统计**：在「我的 → 流量」查看今日 / 本月 WiFi 与移动数据用量，月历显示每天的流量，
  当前网络显示 WiFi / 4G / 5G；需要系统「使用情况访问」和电话权限（4G/5G 历史用量仍合并统计）
- **站外拦截**：非当前站点的主框架跳转、弹窗、APK 下载全部拦截
- **外部播放器 / 投屏**：从播放页解析真实 m3u8，交给 MX Player、VLC 等外部应用
- **可改域名**：站点换域名时在「我的 → 设置」里修改，无需重新打包

## 构建与安装

```powershell
# 在仓库根目录
.\install.bat CupFox            # debug
.\install.bat CupFox release    # release
```

或单独构建：

```powershell
cd CupFox
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

- minSdk 26，targetSdk 36
- 复用仓库共享签名 `keystore/shared.p12`
- 包名 `org.caojun.cupfox`

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
- **域名可改但路径规则固定**：分类路径来自网页导航，换到不同模板的站点时会重新读取；
  但读取失败时只保留「首页」，不再展示可能过期的内置分类。
- **底部栏显示数量随屏幕变化**：勾选超过当前屏幕能放下的数量时，底部只显示排序靠前的部分，
  「栏目」页会显示当前上限。
- **流量统计的 4G/5G 合并**：Android 系统只按 WiFi / 移动数据统计，无法拆分 4G 和 5G 的历史用量；
  当前网络制式通过 `READ_PHONE_STATE` 读取，双卡、VPN、部分厂商 ROM 的统计可能偏差，页面标注「仅供参考」。
- **站点改版会失效**：所有适配都集中在 `PageInjector.kt`，便于快速修复。

## 技术要点

- 单 Activity + Compose + 常驻 WebView
- 开放 JS / DOM Storage / 第三方 Cookie / 混合内容，保证第三方解析播放器能工作
- `onShowCustomView` 实现播放器全屏与横屏
- `JavascriptInterface` 回传页面标题、m3u8 线路和网页导航
- 底部栏目顺序、勾选、透明度存 `SharedPreferences`，数量按屏幕宽度计算
- 页面延伸到底部栏后面，通过注入 `body { padding-bottom }` 让内容能滚到导航栏上方
- 流量按天查询、按 UID 统计，整月结果缓存，避免部分 ROM 的跨天 bucket 把整月流量堆到 1 号
- 当前网络类型用 `ConnectivityManager` + `TelephonyManager.dataNetworkType` 判断，需要 `READ_PHONE_STATE`
- 历史记录用 `SharedPreferences` + JSON，最多 100 条
