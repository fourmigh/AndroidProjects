package org.caojun.cupfox.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.caojun.cupfox.data.HistoryStore
import org.caojun.cupfox.data.SettingsStore
import org.caojun.cupfox.model.HistoryEntry
import org.caojun.cupfox.model.NavItem
import org.caojun.cupfox.model.VideoLine
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * 单 WebView 浏览器控制器。
 *
 * 只加载用户指定的网站；站内链接放行，站外跳转、弹窗、下载全部拦截。
 * 页面里的广告/公告/推广由 [PageInjector] 隐藏。
 */
class BrowserController(private val activity: ComponentActivity) {

    val settings = SettingsStore(activity)
    private val historyStore = HistoryStore(activity)

    var history by mutableStateOf(historyStore.load())
        private set
    private val initialNav = loadNav()
    private val hadNavCache = settings.navCacheBase == settings.baseUrl &&
        settings.navCacheJson.isNotBlank() &&
        parseNav(settings.navCacheJson).size >= 3
    var navItems by mutableStateOf(initialNav)
        private set
    /** 导航是否已就绪；无缓存时先隐藏底部栏，读到或超时后再显示。 */
    var navReady by mutableStateOf(hadNavCache)
        private set
    var navAlpha by mutableStateOf(settings.navAlpha)
        private set
    var navOrder by mutableStateOf(loadNavOrder())
        private set
    var navSelected by mutableStateOf(loadNavSelected())
        private set
    var navSelectionTouched by mutableStateOf(settings.navSelectionTouched)
        private set
    var pageTitle by mutableStateOf("")
        private set
    var progress by mutableStateOf(0)
        private set
    var isPlayingPage by mutableStateOf(false)
        private set
    var lines by mutableStateOf<List<VideoLine>>(emptyList())
        private set
    var isFullscreen by mutableStateOf(false)
        private set

    private val allowedRoots = mutableSetOf("1cbh.cc")
    private var pendingHistoryClear = false
    private var contentBottomInsetDp = 0f

    private val navTimeoutTask = Runnable {
        if (!navReady) {
            if (navItems.isEmpty()) navItems = fallbackNav()
            navReady = true
        }
    }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    lateinit var webView: WebView
        private set

    init {
        allowedRoots.add(SettingsStore.rootDomain(settings.baseUrl))
        webView = createWebView()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        if (hadNavCache) normalizeNavConfig()
        if (!navReady) {
            webView.postDelayed(navTimeoutTask, NAV_TIMEOUT_MS)
        }
        loadTab(navItems.firstOrNull()?.path ?: "/index.html")
    }

    // ---------------------------------------------------------------- 公开操作

    /** 某个栏目对应的完整地址。 */
    fun tabUrl(path: String): String = settings.baseUrl.trimEnd('/') + path

    /** 切换栏目：加载分类页，并把该页作为新的返回栈起点。 */
    fun loadTab(path: String) {
        pendingHistoryClear = true
        webView.loadUrl(tabUrl(path))
    }

    fun load(url: String) {
        allowedRoots.add(SettingsStore.rootDomain(url))
        webView.loadUrl(url)
    }

    fun openHistory(entry: HistoryEntry) {
        load(entry.url)
    }

    fun reload() {
        webView.reload()
    }

    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun search(keyword: String) {
        val request = tabUrl("/search/index.html?keyword=" + Uri.encode(keyword))
        webView.loadUrl(request)
    }

    /** 修改网站域名并回到首页。 */
    fun changeBaseUrl(baseUrl: String) {
        settings.baseUrl = baseUrl
        allowedRoots.add(SettingsStore.rootDomain(baseUrl))
        settings.clearNav()
        webView.removeCallbacks(navTimeoutTask)
        navItems = fallbackNav()
        navOrder = emptyList()
        navSelected = emptySet()
        navSelectionTouched = false
        settings.navSelectionTouched = false
        navReady = false
        webView.postDelayed(navTimeoutTask, NAV_TIMEOUT_MS)
        loadTab("/index.html")
    }

    /** 调用系统里的外部播放器（MX Player / VLC 等），也用于投屏。 */
    fun openExternally(line: VideoLine) {
        val uri = Uri.parse(line.url)
        val mimeTypes = listOf(
            "application/x-mpegURL",
            "application/vnd.apple.mpegurl",
            "video/*",
        )
        for (mime in mimeTypes) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: ActivityNotFoundException) {
                // 尝试下一种 MIME，最后降级为复制链接
            } catch (e: Exception) {
                Log.w(TAG, "open externally failed", e)
            }
        }
        copyToClipboard(line.url)
        toast("没有找到能播放 m3u8 的应用，链接已复制")
    }

    fun copyLine(line: VideoLine) {
        copyToClipboard(line.url)
        toast("视频链接已复制")
    }

    fun removeHistory(key: String) {
        historyStore.remove(key)
        history = historyStore.load()
    }

    fun clearHistory() {
        historyStore.clear()
        history = historyStore.load()
    }

    fun exitFullscreen() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        isFullscreen = false
        runCatching {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 底部导航栏高度（dp）。页面整体延伸到导航栏后面，用 body 的 padding 让内容能滚上来。 */
    fun setContentBottomInset(dp: Float) {
        if (contentBottomInsetDp == dp) return
        contentBottomInsetDp = dp
        applyContentBottomInset()
    }

    private fun applyContentBottomInset(view: WebView = webView) {
        val value = contentBottomInsetDp
        val script = if (value > 0f) {
            "(function(){if(document.body){document.body.style.setProperty('padding-bottom','${value}px','important');}})();"
        } else {
            "(function(){if(document.body){document.body.style.removeProperty('padding-bottom');}})();"
        }
        view.evaluateJavascript(script, null)
    }

    fun toast(message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ WebView 装配

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.parseColor("#0E0E12"))

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            defaultTextEncodingName = "UTF-8"
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        wv.setDownloadListener { url, _, _, mimetype, _ ->
            Log.w(TAG, "blocked download: $url ($mimetype)")
            toast("已阻止下载文件")
        }

        wv.webViewClient = Client()
        wv.webChromeClient = Chrome()
        wv.addJavascriptInterface(Bridge(), "AndroidBridge")

        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        return wv
    }

    private fun isAllowedHost(host: String): Boolean {
        val h = host.lowercase()
        return allowedRoots.any { root ->
            root.isNotBlank() && (h == root || h.endsWith(".$root"))
        }
    }

    private fun emptyResponse(mime: String): WebResourceResponse =
        WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(ByteArray(0)))

    // ---------------------------------------------------------------- 页面回调

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val scheme = url.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                // intent:// market:// tel: 等一律不交给系统
                return true
            }
            // iframe / 播放器资源不拦截，只处理主框架导航
            if (!request.isForMainFrame) return false
            val host = url.host ?: return true
            if (isAllowedHost(host)) return false
            Log.i(TAG, "blocked external navigation: $url")
            toast("已拦截站外跳转")
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (!settings.adBlockEnabled) return null
            val url = request.url.toString().lowercase()
            return when {
                // 只拦广告样式。播放器模板也引用了 /iyads/js 下的通用库，
                // 直接拦脚本会导致播放器初始化失败。
                url.contains("ads.min.css") -> emptyResponse("text/css")
                else -> null
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progress = 0
            val playing = url?.contains("/p/") == true
            isPlayingPage = playing
            if (!playing) {
                lines = emptyList()
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            super.onPageCommitVisible(view, url)
            if (!url.isNullOrBlank()) {
                view.evaluateJavascript(PageInjector.INJECT_CSS_JS, null)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            if (url.isNullOrBlank()) return

            view.evaluateJavascript(PageInjector.INJECT_JS, null)
            applyContentBottomInset(view)

            if (pendingHistoryClear) {
                view.clearHistory()
                pendingHistoryClear = false
            }

            val playing = url.contains("/p/")
            isPlayingPage = playing
            if (!playing) {
                lines = emptyList()
                view.postDelayed({ extractNav(view) }, 600L)
                view.postDelayed({ extractNav(view) }, 2000L)
            } else {
                // 播放器脚本在页面末尾执行，稍等再取线路与标题
                view.postDelayed({ extractFromPage(view, url) }, 900L)
                view.postDelayed({ extractFromPage(view, url) }, 2500L)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                toast("页面加载失败：${error.description}")
            }
        }
    }

    private fun extractFromPage(view: WebView, expectedUrl: String) {
        val current = view.url ?: return
        if (current != expectedUrl || !current.contains("/p/")) return
        view.evaluateJavascript(PageInjector.EXTRACT_LINES_JS, null)
        view.evaluateJavascript(PageInjector.EXTRACT_META_JS, null)
    }

    private fun extractNav(view: WebView) {
        val current = view.url ?: return
        if (current.contains("/p/")) return
        val host = Uri.parse(current).host ?: return
        if (!isAllowedHost(host)) return
        view.evaluateJavascript(PageInjector.EXTRACT_NAV_JS, null)
    }

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
            if (newProgress == 100) {
                view.postDelayed({ extractNav(view) }, 300L)
            }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            pageTitle = cleanTitle(title)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            val decor = activity.window.decorView as? FrameLayout ?: return
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view.setBackgroundColor(Color.BLACK)
            decor.addView(view, params)
            customView = view
            customViewCallback = callback
            isFullscreen = true

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(activity.window, decor).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            toast("已拦截弹出窗口")
            return false
        }

        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            Log.d(TAG, "js alert: $message")
            result.confirm()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            result.confirm()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(TAG, "console: ${consoleMessage.message()} @${consoleMessage.lineNumber()}")
            return true
        }
    }

    /** JS -> Kotlin 桥。WebView 的脚本线程，所有 UI 更新都投递回主线程。 */
    private inner class Bridge {

        @JavascriptInterface
        fun onLines(json: String) {
            activity.runOnUiThread {
                if (!isPlayingPage) return@runOnUiThread
                lines = parseLines(json)
            }
        }

        @JavascriptInterface
        fun onPageMeta(json: String) {
            activity.runOnUiThread {
                val url = webView.url ?: return@runOnUiThread
                if (!url.contains("/p/")) return@runOnUiThread
                val title = runCatching {
                    JSONObject(json).optString("title")
                }.getOrDefault("")
                recordHistory(url, title)
            }
        }

        @JavascriptInterface
        fun onNav(json: String) {
            activity.runOnUiThread {
                val url = webView.url ?: return@runOnUiThread
                if (url.contains("/p/")) return@runOnUiThread
                applyNav(json)
            }
        }
    }

    // ------------------------------------------------------------------ 栏目

    /** 读取失败时的兜底：只保留「首页」和「我的」，不展示可能过期的写死栏目。 */
    private fun fallbackNav(): List<NavItem> = listOf(
        NavItem("首页", "/index.html", "home"),
    )

    private fun loadNav(): List<NavItem> {
        val cached = settings.navCacheJson
        if (settings.navCacheBase == settings.baseUrl && cached.isNotBlank()) {
            val items = parseNav(cached)
            if (items.size >= 3) return items
        }
        return fallbackNav()
    }

    private fun applyNav(json: String) {
        val items = parseNav(json)
        if (items.size < 3) return

        navReady = true
        webView.removeCallbacks(navTimeoutTask)

        val serialized = navToJson(items)
        if (serialized != settings.navCacheJson || settings.navCacheBase != settings.baseUrl) {
            settings.saveNav(settings.baseUrl, serialized)
        }
        if (items != navItems) {
            navItems = items
        }
        normalizeNavConfig()
    }

    // -------------------------------------------------------- 栏目显示与排序

    fun orderedNavItems(): List<NavItem> {
        val byPath = navItems.associateBy { it.path }
        val ordered = navOrder.mapNotNull { byPath[it] }
        val remaining = navItems.filter { it.path !in navOrder }
        return ordered + remaining
    }

    fun bottomNavItems(maxTabs: Int): List<NavItem> =
        orderedNavItems()
            .filter { navSelected.contains(it.path) }
            .take(maxTabs)

    fun updateNavAlpha(value: Float) {
        navAlpha = value.coerceIn(0.3f, 1f)
    }

    fun persistNavAlpha() {
        settings.navAlpha = navAlpha
    }

    fun toggleNav(path: String, checked: Boolean, maxTabs: Int) {
        if (checked) {
            if (navSelected.size >= maxTabs) {
                toast("当前屏幕最多显示 $maxTabs 个栏目")
                return
            }
            navSelected = navSelected + path
        } else {
            navSelected = navSelected - path
        }
        navSelectionTouched = true
        settings.navSelectionTouched = true
        settings.saveNavConfig(navOrder, navSelected)
    }

    fun moveNav(path: String, delta: Int) {
        val list = orderedNavItems().map { it.path }.toMutableList()
        val index = list.indexOf(path)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.removeAt(index)
        list.add(target, path)
        navOrder = list
        navSelectionTouched = true
        settings.navSelectionTouched = true
        settings.saveNavConfig(navOrder, navSelected)
    }

    /** 用户没手动调整过时，按屏幕能放下的数量自动截断勾选。 */
    fun limitNavSelection(maxTabs: Int) {
        if (navSelectionTouched) return
        val limited = orderedNavItems().map { it.path }.take(maxTabs).toSet()
        if (limited != navSelected) {
            navSelected = limited
            settings.saveNavConfig(navOrder, navSelected)
        }
    }

    private fun loadNavOrder(): List<String> =
        settings.navOrder.split('\n').filter { it.isNotBlank() }

    private fun loadNavSelected(): Set<String> =
        settings.navSelected.split('\n').filter { it.isNotBlank() }.toSet()

    /** 网页栏目更新后，保留用户的顺序和勾选；首次读取时默认全选。 */
    private fun normalizeNavConfig() {
        val paths = navItems.map { it.path }
        val pathSet = paths.toSet()
        val configured = settings.navOrder.isNotBlank()

        val order = (navOrder.filter { it in pathSet } + paths.filter { it !in navOrder })
            .distinct()
        val selected = if (!configured) {
            pathSet
        } else {
            navSelected.filter { it in pathSet }.toSet()
        }

        if (order != navOrder) navOrder = order
        if (selected != navSelected) navSelected = selected
        settings.saveNavConfig(order, selected)
    }

    private fun parseNav(json: String): List<NavItem> {
        return runCatching {
            val array = JSONArray(json)
            val seen = mutableSetOf<String>()
            val result = mutableListOf<NavItem>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val label = item.optString("name").trim().take(12)
                var path = item.optString("path").trim().take(300)
                if (label.isEmpty() || path.isEmpty()) continue
                if (!path.startsWith("/")) path = "/$path"
                if (!seen.add(path)) continue
                result.add(NavItem(label, path, iconKeyFor(label)))
                if (result.size >= 12) break
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun navToJson(items: List<NavItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("name", item.label)
                    put("path", item.path)
                }
            )
        }
        return array.toString()
    }

    private fun iconKeyFor(label: String): String = when {
        label.contains("首页") || label.contains("主页") -> "home"
        label.contains("电影") -> "movie"
        label.contains("电视剧") || label.contains("剧集") || label.contains("连续剧") -> "tv"
        label.contains("综艺") -> "variety"
        label.contains("动漫") || label.contains("动画") -> "anime"
        label.contains("短剧") -> "short"
        label.contains("资讯") || label.contains("新闻") -> "news"
        label.contains("专题") -> "star"
        else -> "default"
    }

    private fun parseLines(json: String): List<VideoLine> {
        return runCatching {
            val array = JSONArray(json)
            val result = LinkedHashMap<String, VideoLine>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("http")) continue
                val line = VideoLine(
                    name = item.optString("name").ifBlank { "第 ${i + 1} 集" },
                    url = url,
                    current = item.optBoolean("current"),
                )
                result.putIfAbsent(url, line)
            }
            result.values.toList()
        }.getOrDefault(emptyList())
    }

    private fun recordHistory(url: String, rawTitle: String) {
        if (!url.contains("/p/")) return
        val title = cleanTitle(rawTitle.ifBlank { pageTitle })
        if (title.isBlank()) return

        val key = Regex("/p/(\\d+)").find(url)?.groupValues?.getOrNull(1)
            ?.let { "p:$it" } ?: url
        val existing = history.firstOrNull { it.key == key }
        if (existing != null && existing.url == url && existing.title == title) return

        historyStore.record(
            HistoryEntry(
                key = key,
                title = title,
                url = url,
                updatedAt = System.currentTimeMillis(),
            )
        )
        history = historyStore.load()
    }

    private fun cleanTitle(raw: String?): String {
        var title = raw?.trim().orEmpty()
        if (title.isEmpty()) return ""

        // 播放页标题形如「正在播放《九门2026》-免费高清在线观看/...」
        Regex("正在播放《(.+?)》").find(title)?.let { match ->
            return match.groupValues[1].trim().take(80)
        }

        title = title.substringBefore(" - 茶杯狐")
            .substringBefore("-茶杯狐")
            .substringBefore("_茶杯狐")
            .substringBefore("-免费高清")
            .substringBefore("免费高清在线")
            .trim()
        return title.take(80)
    }

    private fun copyToClipboard(text: String) {
        val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("视频地址", text))
    }

    // ---------------------------------------------------------------- 生命周期

    fun onResume() {
        webView.onResume()
    }

    fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    fun onDestroy() {
        webView.removeCallbacks(navTimeoutTask)
        exitFullscreen()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private companion object {
        const val TAG = "CupFox"
        const val NAV_TIMEOUT_MS = 3000L
    }
}
