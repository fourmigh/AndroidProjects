package org.caojun.webfilm.web

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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.caojun.webfilm.data.SettingsStore
import org.caojun.webfilm.model.NavItem
import org.caojun.webfilm.model.VideoLine
import org.caojun.player.PlayerLauncher
import org.caojun.player.PlayerPreferences
import org.caojun.player.PlayerProgressBus
import org.caojun.player.PlayerProgressListener
import org.caojun.player.PlayerType
import org.caojun.player.history.HistoryEntry
import org.caojun.player.history.HistoryStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URL

/**
 * 单 WebView 浏览器控制器。
 *
 * 只加载用户指定的网站；站内链接放行，站外跳转、弹窗、下载全部拦截。
 * 页面里的广告/公告/推广由 [PageInjector] 隐藏。
 */
class BrowserController(private val activity: ComponentActivity) {

    val settings = SettingsStore(activity)
    val playerPreferences = PlayerPreferences(activity)
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
    /** 网页点击线路后需要加载目标页、再自动用默认播放器接管的地址。 */
    private var autoPlayUrl: String? = null
    /** 因为打开了 App 播放器而暂停网页，返回时不要自动恢复网页播放。 */
    private var suspendWebMedia = false

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
        PlayerProgressBus.listener = object : PlayerProgressListener {
            override fun onProgress(
                key: String,
                episodeKey: String,
                episodeName: String,
                positionMs: Long,
                durationMs: Long,
            ) {
                historyStore.updateProgress(
                    key = key,
                    episodeKey = episodeKey,
                    episodeName = episodeName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
                history = historyStore.load()
            }

            override fun onLastPlayerChanged(key: String, player: org.caojun.player.PlayerType) {
                historyStore.setLastPlayer(key, player.storedName)
                history = historyStore.load()
            }

            override fun onVideoUrl(key: String, videoUrl: String) {
                historyStore.setVideoUrl(key, videoUrl)
                history = historyStore.load()
            }
        }
        webView = createWebView()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        if (hadNavCache) normalizeNavConfig()
        if (!navReady) {
            webView.postDelayed(navTimeoutTask, NAV_TIMEOUT_MS)
        }
        val startPath = orderedNavItems().firstOrNull { navSelected.contains(it.path) }?.path
            ?: navItems.firstOrNull()?.path
            ?: "/index.html"
        loadTab(startPath)
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
        val latest = historyStore.load().firstOrNull { it.key == entry.key } ?: entry
        when (latest.lastPlayer) {
            "media3" -> if (latest.videoUrl.isNotBlank()) {
                openHistoryWithMedia3(latest)
            } else {
                openHistoryInWeb(latest, rememberPlayer = false)
            }
            "external" -> if (latest.videoUrl.isNotBlank()) {
                openHistoryExternally(latest)
            } else {
                openHistoryInWeb(latest, rememberPlayer = false)
            }
            else -> openHistoryInWeb(latest)
        }
    }

    fun openHistoryInWeb(entry: HistoryEntry, rememberPlayer: Boolean = true) {
        if (rememberPlayer) {
            historyStore.setLastPlayer(entry.key, "web")
            history = historyStore.load()
        }
        load(entry.url)
    }

    /** 更新当前播放页所属剧集的上次播放器。 */
    fun setCurrentPlayer(player: String) {
        val url = webView.url ?: return
        if (!url.contains("/p/")) return
        historyStore.setLastPlayer(historyKey(url), player)
        history = historyStore.load()
    }

    fun openHistoryWithMedia3(entry: HistoryEntry) {
        if (entry.videoUrl.isBlank()) {
            toast("这条记录还没有视频地址，请先用 App 内播放一次")
            return
        }
        historyStore.setLastPlayer(entry.key, "media3")
        historyStore.setVideoUrl(entry.key, entry.videoUrl)
        history = historyStore.load()
        val startMs = if (entry.durationMs > 0L &&
            entry.positionMs > entry.durationMs - 30_000L
        ) {
            0L
        } else {
            entry.positionMs
        }
        PlayerLauncher.launchMedia3(
            context = activity,
            url = entry.videoUrl,
            title = entry.title,
            episodeName = entry.episodeName,
            historyKey = entry.key,
            episodeKey = entry.episodeKey,
            referer = settings.baseUrl,
            startMs = startMs,
        )
        suspendWebMedia = true
        pauseWebVideos()
    }

    fun openHistoryExternally(entry: HistoryEntry) {
        if (entry.videoUrl.isBlank()) {
            toast("这条记录还没有视频地址，请先用 App 内播放一次")
            return
        }
        historyStore.setLastPlayer(entry.key, "external")
        history = historyStore.load()
        openExternally(VideoLine(name = entry.episodeName, url = entry.videoUrl))
    }

    fun copyHistoryUrl(entry: HistoryEntry) {
        if (entry.videoUrl.isBlank()) {
            toast("这条记录还没有视频地址")
            return
        }
        copyToClipboard(entry.videoUrl)
        toast("视频链接已复制")
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

    /** 用 Media3 / ExoPlayer 播放当前线路，进度写入同一份观看历史。 */
    fun openWithMedia3(line: VideoLine) {
        val url = webView.url ?: return
        if (!url.contains("/p/")) {
            toast("请在播放页使用")
            return
        }
        openWithMedia3At(line, url)
    }

    /** 按指定播放页地址打开 Media3，供网页点击集数时使用。 */
    private fun openWithMedia3At(line: VideoLine, pageUrl: String) {
        val key = historyKey(pageUrl)
        val epKey = episodeKey(pageUrl)
        rememberPlayChoice(
            key = key,
            videoUrl = line.url,
            player = "media3",
            pageUrl = pageUrl,
            episodeName = line.name,
        )
        val saved = history.firstOrNull { it.key == key }?.episodes?.get(epKey)
        val startMs = saved?.let { progress ->
            if (progress.durationMs > 0L &&
                progress.positionMs > progress.durationMs - 30_000L
            ) {
                0L
            } else {
                progress.positionMs
            }
        } ?: 0L

        PlayerLauncher.launchMedia3(
            context = activity,
            url = line.url,
            title = pageTitle.ifBlank { line.name },
            episodeName = line.name,
            historyKey = key,
            episodeKey = epKey,
            referer = settings.baseUrl,
            startMs = startMs,
        )
        suspendWebMedia = true
        pauseWebVideos()
    }

    /** 记住某部剧的播放器选择和视频地址；条目不存在时补一条。 */
    private fun rememberPlayChoice(
        key: String,
        videoUrl: String,
        player: String,
        pageUrl: String,
        episodeName: String,
    ) {
        val exists = historyStore.load().any { it.key == key }
        if (exists) {
            historyStore.updatePage(
                key = key,
                url = pageUrl,
                episodeKey = episodeKey(pageUrl),
                episodeName = episodeName,
            )
            historyStore.setLastPlayer(key, player)
            historyStore.setVideoUrl(key, videoUrl)
        } else {
            historyStore.record(
                HistoryEntry(
                    key = key,
                    title = pageTitle.ifBlank { episodeName },
                    url = pageUrl,
                    updatedAt = System.currentTimeMillis(),
                    episodeName = episodeName,
                    episodeKey = episodeKey(pageUrl),
                    videoUrl = videoUrl,
                    lastPlayer = player,
                )
            )
        }
        history = historyStore.load()
    }

    /** 调用系统里的外部播放器（MX Player / VLC 等），也用于投屏。 */
    fun openExternally(line: VideoLine) {
        if (PlayerLauncher.launchExternal(activity, line.url)) {
            suspendWebMedia = true
            pauseWebVideos()
            return
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

    fun refreshHistory() {
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

        // 注入到所有 frame（含跨域播放器 iframe）：页面转后台时暂停视频
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                wv,
                PageInjector.PAUSE_WHEN_HIDDEN_JS,
                setOf("*"),
            )
        }

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
            view.evaluateJavascript(PageInjector.HOOK_PLAY_JS, null)
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
                view.evaluateJavascript(PageInjector.TRACK_PROGRESS_JS, null)
                val key = historyKey(url)
                val epKey = episodeKey(url)
                val saved = history.firstOrNull { it.key == key }?.episodes?.get(epKey)
                if (saved != null) {
                    val nearEnd = saved.durationMs > 0L &&
                        saved.positionMs > saved.durationMs - 30_000L
                    if (!nearEnd && saved.positionMs > 10_000L) {
                        view.postDelayed({
                            if (view.url?.let { historyKey(it) } == key &&
                                view.url?.let { episodeKey(it) } == epKey
                            ) {
                                view.evaluateJavascript(
                                    PageInjector.seekScript(saved.positionMs),
                                    null,
                                )
                            }
                        }, 1500L)
                    }
                }
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

        /**
         * 网页点击了播放链接（集数 / 线路 / 立即播放）。
         * 返回 true 表示宿主接管，网页不再自己播放。
         */
        @JavascriptInterface
        fun onPlayClick(href: String, videoUrl: String, episodeName: String): Boolean {
            val player = playerPreferences.defaultPlayer
            if (player == PlayerType.WEB) return false
            val target = absoluteUrl(href) ?: return false

            activity.runOnUiThread {
                if (videoUrl.isNotBlank()) {
                    val line = VideoLine(episodeName.ifBlank { "默认线路" }, videoUrl)
                    when (player) {
                        PlayerType.MEDIA3 -> openWithMedia3At(line, target)
                        PlayerType.EXTERNAL -> {
                            rememberPlayChoice(
                                key = historyKey(target),
                                videoUrl = videoUrl,
                                player = "external",
                                pageUrl = target,
                                episodeName = episodeName,
                            )
                            openExternally(line)
                        }
                        PlayerType.WEB -> Unit
                    }
                } else {
                    // 拿不到直链（多是切换线路），先加载目标页，提取到 m3u8 后自动接管
                    autoPlayUrl = target
                    suppressMediaAutoPlay(true)
                    load(target)
                }
            }
            return true
        }

        @JavascriptInterface
        fun onLines(json: String) {
            activity.runOnUiThread {
                if (!isPlayingPage) return@runOnUiThread
                lines = parseLines(json)
                val url = webView.url
                if (url != null && url.contains("/p/")) {
                    historyStore.setVideoUrl(historyKey(url), currentVideoUrl())
                    history = historyStore.load()
                }
                autoPlayIfReady()
            }
        }

        @JavascriptInterface
        fun onPageMeta(json: String) {
            activity.runOnUiThread {
                val url = webView.url ?: return@runOnUiThread
                if (!url.contains("/p/")) return@runOnUiThread
                val data = runCatching { JSONObject(json) }.getOrNull() ?: return@runOnUiThread
                recordHistory(
                    url = url,
                    rawTitle = data.optString("title"),
                    episode = data.optString("episode"),
                )
            }
        }

        @JavascriptInterface
        fun onProgress(positionMs: Long, durationMs: Long, episodeName: String) {
            activity.runOnUiThread {
                val url = webView.url ?: return@runOnUiThread
                if (!url.contains("/p/") || durationMs <= 0L) return@runOnUiThread
                historyStore.updateProgress(
                    key = historyKey(url),
                    episodeKey = episodeKey(url),
                    episodeName = episodeName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    videoUrl = currentVideoUrl(),
                )
                history = historyStore.load()
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

    fun applyNavOrder(paths: List<String>) {
        if (paths == navOrder) return
        navOrder = paths
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

    private fun recordHistory(url: String, rawTitle: String, episode: String = "") {
        if (!url.contains("/p/")) return
        val title = cleanTitle(rawTitle.ifBlank { pageTitle })
        if (title.isBlank()) return

        val key = historyKey(url)
        val epKey = episodeKey(url)
        val existing = history.firstOrNull { it.key == key }

        if (existing != null) {
            val saved = existing.episodes[epKey]
            val nextEpisode = episode.ifBlank {
                if (existing.episodeKey == epKey) existing.episodeName else ""
            }
            val next = existing.copy(
                title = title,
                url = url,
                episodeName = nextEpisode,
                episodeKey = epKey,
                positionMs = saved?.positionMs ?: 0L,
                durationMs = saved?.durationMs ?: 0L,
                updatedAt = System.currentTimeMillis(),
                videoUrl = currentVideoUrl().ifBlank { existing.videoUrl },
            )
            if (next != existing) {
                historyStore.record(next)
                history = historyStore.load()
            }
            return
        }

        historyStore.record(
            HistoryEntry(
                key = key,
                title = title,
                url = url,
                updatedAt = System.currentTimeMillis(),
                episodeName = episode,
                episodeKey = epKey,
                videoUrl = currentVideoUrl(),
            )
        )
        history = historyStore.load()
    }

    private fun currentVideoUrl(): String =
        lines.firstOrNull { it.current }?.url ?: lines.firstOrNull()?.url ?: ""

    /** 把网页里的相对地址转成绝对地址。 */
    private fun absoluteUrl(href: String): String? {
        if (href.isBlank()) return null
        return runCatching { URL(URL(settings.baseUrl), href).toString() }.getOrNull()
    }

    /** 临时禁止网页自动播放，避免 App 播放器启动时网页也在出声。 */
    private fun suppressMediaAutoPlay(suppress: Boolean) {
        webView.settings.mediaPlaybackRequiresUserGesture = suppress
        if (suppress) {
            webView.postDelayed({
                if (autoPlayUrl != null) {
                    autoPlayUrl = null
                    webView.settings.mediaPlaybackRequiresUserGesture = false
                }
            }, 8000L)
        }
    }

    /** 暂停网页里正在播放的视频，进入 App 播放器时避免两边同时出声。 */
    private fun pauseWebVideos() {
        webView.evaluateJavascript(
            "(function(){var vs=document.querySelectorAll('video');" +
                "for(var i=0;i<vs.length;i++){try{vs[i].pause();}catch(e){}}})()",
            null,
        )
    }

    /** 点击网页线路后，目标页提取到 m3u8 时自动用默认播放器接管。 */
    private fun autoPlayIfReady() {
        val pending = autoPlayUrl ?: return
        val current = webView.url ?: return
        if (current != pending) return
        autoPlayUrl = null
        suppressMediaAutoPlay(false)
        val player = playerPreferences.defaultPlayer
        if (player == PlayerType.WEB) return
        val line = lines.firstOrNull { it.current } ?: lines.firstOrNull() ?: return
        when (player) {
            PlayerType.MEDIA3 -> openWithMedia3At(line, current)
            PlayerType.EXTERNAL -> {
                rememberPlayChoice(
                    key = historyKey(current),
                    videoUrl = line.url,
                    player = "external",
                    pageUrl = current,
                    episodeName = line.name,
                )
                openExternally(line)
            }
            PlayerType.WEB -> Unit
        }
    }

    /** 每部剧一条历史。 */
    private fun historyKey(url: String): String =
        Regex("/p/(\\d+)").find(url)?.groupValues?.getOrNull(1)?.let { "p:$it" } ?: url

    /** 集标识：URL 里的集索引，不含线路参数。 */
    private fun episodeKey(url: String): String {
        val path = Uri.parse(url).path.orEmpty()
        val parts = path.trim('/').split('/')
        return parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "0"
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
        if (suspendWebMedia) {
            suspendWebMedia = false
            pauseWebVideos()
        }
    }

    fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    fun onDestroy() {
        PlayerProgressBus.listener = null
        webView.removeCallbacks(navTimeoutTask)
        exitFullscreen()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private companion object {
        const val TAG = "WebFilm"
        const val NAV_TIMEOUT_MS = 3000L
    }
}
