package org.caojun.quotamonitor.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.caojun.quotamonitor.data.JsScripts
import org.caojun.quotamonitor.data.QuotaCache
import org.caojun.quotamonitor.data.QuotaParser
import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.model.PlatformState
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.SyncStatus
import org.json.JSONObject

/**
 * 核心控制器：为每个平台维护一个常驻 WebView（后台静默加载），
 * 通过「文档创建时注入的 fetch/XHR 钩子 + 加载完成后 DOM 扫描」双通道
 * 提取套餐限额数据，写入 [states] 供 Compose 界面消费。
 *
 * 界面打开控制台网页时，同一 WebView 会被重新挂到前台容器；
 * 关闭后移回 [bindHidden] 注册的隐藏容器继续待命。
 *
 * 所有公开方法都必须在主线程调用。
 */
class QuotaController(private val context: Context) {

    val states: SnapshotStateMap<Platform, PlatformState> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val webViews = mutableMapOf<Platform, WebView>()
    private val sessions = mutableMapOf<Platform, Session>()
    private var hiddenContainer: ViewGroup? = null
    private var started = false

    private var defaultUa: String? = null
    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /** 一次刷新周期内累积的提取上下文。 */
    private class Session(
        val gen: Int,
        val networkItems: MutableList<QuotaItem> = mutableListOf(),
        val seenBodies: MutableSet<Int> = hashSetOf(),
        var dom: QuotaSnapshot? = null,
        var scanHasPassword: Boolean = false,
        var scanLoginLike: Boolean = false,
        var scanUrl: String = "",
        var scanLines: List<String> = emptyList(),
        var scanAttempts: Int = 0,
    )

    init {
        Platform.entries.forEach { platform ->
            QuotaCache.load(context, platform)?.let { snapshot ->
                states[platform] = PlatformState(status = SyncStatus.OK, snapshot = snapshot)
            }
        }
    }

    /** 首次进入界面时触发：两个平台并行静默刷新。 */
    fun start() {
        if (started) return
        started = true
        Platform.entries.forEach { refresh(it) }
    }

    fun refreshAll() {
        Platform.entries.forEach { refresh(it) }
    }

    /** 重新加载目标页面并开始新一轮提取。 */
    fun refresh(platform: Platform) {
        val wv = ensureWebView(platform)
        val gen = (sessions[platform]?.gen ?: 0) + 1
        sessions[platform] = Session(gen = gen)
        val prev = states[platform] ?: PlatformState()
        states[platform] = prev.copy(status = SyncStatus.LOADING, message = null, progress = 0)
        wv.stopLoading()
        wv.loadUrl(platform.url)
        scope.launch {
            delay(TIMEOUT_MS)
            finalizeTimeout(platform, gen)
        }
    }

    /** 切换桌面 / 移动 User-Agent 并重新加载。 */
    fun toggleDesktopUa(platform: Platform) {
        val wv = ensureWebView(platform)
        val current = wv.settings.userAgentString
        val next = if (current == desktopUa) (defaultUa ?: current) else desktopUa
        wv.settings.userAgentString = next
        refresh(platform)
    }

    fun isDesktopUa(platform: Platform): Boolean =
        webViews[platform]?.settings?.userAgentString?.let { it == desktopUa } ?: true

    /** 注册常驻隐藏容器（不拦截触摸、全尺寸、置于内容下层）。 */
    fun bindHidden(container: ViewGroup) {
        hiddenContainer = container
        webViews.values.forEach { moveTo(it, container) }
    }

    /** 把该平台的 WebView 挂到前台显示（打开控制台网页时）。 */
    fun attachForDisplay(platform: Platform, container: ViewGroup) {
        moveTo(ensureWebView(platform), container)
    }

    /** 前台容器释放：移回隐藏容器；若此前未成功登录/无数据，则自动再刷新一次。 */
    fun detachFromDisplay(platform: Platform, container: ViewGroup) {
        val wv = webViews[platform] ?: return
        if (wv.parent !== container) return
        container.removeView(wv)
        hiddenContainer?.let { moveTo(wv, it) }
        val status = states[platform]?.status
        if (status == SyncStatus.NEED_LOGIN || status == SyncStatus.IDLE || status == SyncStatus.ERROR) {
            refresh(platform)
        }
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun destroy() {
        scope.cancel()
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViews.clear()
        sessions.clear()
        CookieManager.getInstance().flush()
    }

    // ------------------------------------------------------------ WebView 装配

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(platform: Platform): WebView {
        webViews[platform]?.let { return it }
        if (defaultUa == null) {
            defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        }
        val wv = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = desktopUa
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webViewClient = Client(platform)
            webChromeClient = Chrome(platform)
            addJavascriptInterface(Bridge(platform), "AndroidBridge")
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)
        webViews[platform] = wv
        hiddenContainer?.let { moveTo(wv, it) }
        return wv
    }

    private fun moveTo(wv: WebView, container: ViewGroup) {
        if (wv.parent === container) return
        (wv.parent as? ViewGroup)?.removeView(wv)
        container.addView(
            wv,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private inner class Client(private val platform: Platform) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // 文档开始时注入网络拦截钩子（脚本自带 __qmHooked 幂等保护，可重复注入）。
            view?.evaluateJavascript(JsScripts.HOOK_SCRIPT, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val s = sessions[platform] ?: return
            scheduleScan(platform, s.gen, FIRST_SCAN_DELAY_MS)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame != true) return
            val st = states[platform] ?: return
            if (st.status != SyncStatus.LOADING) return
            states[platform] = st.copy(
                status = SyncStatus.ERROR,
                message = "页面加载失败：${error?.description ?: "网络错误"}",
                progress = -1,
            )
        }
    }

    private inner class Chrome(private val platform: Platform) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            val st = states[platform] ?: return
            if (st.status == SyncStatus.LOADING) {
                states[platform] = st.copy(progress = newProgress)
            }
        }
    }

    /** JS → Kotlin 桥。方法运行在 WebView 的脚本线程，统一投递回主线程。 */
    private inner class Bridge(private val platform: Platform) {

        @JavascriptInterface
        fun onNetwork(url: String, body: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                handleNetwork(platform, url, body)
            }
        }

        @JavascriptInterface
        fun onDomScan(json: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                handleDomScan(platform, json)
            }
        }
    }

    // ------------------------------------------------------------ 提取流水线

    private fun scheduleScan(platform: Platform, gen: Int, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            val s = sessions[platform] ?: return@launch
            if (s.gen != gen) return@launch
            val wv = webViews[platform] ?: return@launch
            wv.evaluateJavascript(JsScripts.DOM_SCAN_SCRIPT, null)
        }
    }

    private fun handleDomScan(platform: Platform, json: String) {
        val s = sessions[platform] ?: return
        runCatching {
            val obj = JSONObject(json)
            s.scanHasPassword = obj.optBoolean("hasPassword", false)
            s.scanLoginLike = obj.optBoolean("loginLike", false)
            s.scanUrl = obj.optString("url", "")
            val rawLines = buildList {
                val arr = obj.optJSONArray("lines")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            s.scanLines = rawLines
            s.dom = QuotaParser.parseDom(platform, json)
        }
        // SPA 渲染可能晚于首屏，没抓到就再扫几轮。
        if (s.dom == null && s.scanAttempts < MAX_SCAN_RETRY) {
            s.scanAttempts++
            scheduleScan(platform, s.gen, RETRY_SCAN_DELAY_MS)
        }
        tryComplete(platform, s)
    }

    private fun handleNetwork(platform: Platform, url: String, body: String) {
        val s = sessions[platform] ?: return
        if (!s.seenBodies.add(body.hashCode())) return
        val items = QuotaParser.parseNetwork(platform, url, body) ?: return
        for (item in items) {
            if (s.networkItems.size >= 60) break
            if (s.networkItems.none { it.label == item.label }) {
                s.networkItems.add(item)
            }
        }
        tryComplete(platform, s)
    }

    private fun tryComplete(platform: Platform, s: Session) {
        if (s.dom != null) {
            // DOM 已确认额度页 → 立即完成，网络数据作为补充合并。
            val merged = QuotaParser.merge(s.networkItems, s.dom) ?: return
            if (merged.items.isEmpty() && merged.lines.isEmpty() && merged.planName == null) return
            complete(platform, merged)
            return
        }
        // DOM 尚未确认额度页：仅当网络数据命中已知指标名才接受，
        // 避免把宣传页 / 登录页的普通 JSON 当成额度。
        val strong = s.networkItems.filter { QuotaParser.isKnownMetricLabel(it.label) }
        if (strong.isNotEmpty()) {
            val snapshot = QuotaParser.merge(strong, null) ?: return
            complete(platform, snapshot)
        }
    }

    private fun complete(platform: Platform, snapshot: QuotaSnapshot) {
        states[platform] = PlatformState(
            status = SyncStatus.OK,
            snapshot = snapshot,
            message = null,
            progress = -1,
        )
        QuotaCache.save(context, platform, snapshot)
    }

    private fun finalizeTimeout(platform: Platform, gen: Int) {
        val s = sessions[platform] ?: return
        if (s.gen != gen) return
        val st = states[platform] ?: return
        if (st.status != SyncStatus.LOADING) return

        // 1) DOM 已确认额度页 → 用已有数据完成
        s.dom?.let { dom ->
            if (dom.items.isNotEmpty() || dom.lines.isNotEmpty() || dom.planName != null) {
                complete(platform, QuotaParser.merge(s.networkItems, dom) ?: dom)
                return
            }
        }

        // 2) 未确认额度页：仅接受命中已知指标名的网络数据
        val strong = s.networkItems.filter { QuotaParser.isKnownMetricLabel(it.label) }
        if (strong.isNotEmpty()) {
            QuotaParser.merge(strong, null)?.let {
                complete(platform, it)
                return
            }
        }

        // 3) 判定为需登录或失败
        val url = s.scanUrl.ifBlank { webViews[platform]?.url ?: "" }
        val looksLogin = s.scanHasPassword ||
            s.scanLoginLike ||
            LOGIN_URL_REGEX.containsMatchIn(url) ||
            QuotaParser.looksLikeMarketing(s.scanLines)

        states[platform] = if (looksLogin) {
            st.copy(
                status = SyncStatus.NEED_LOGIN,
                message = platform.loginHint,
                progress = -1,
            )
        } else {
            st.copy(
                status = SyncStatus.ERROR,
                message = "未识别到额度数据；请点「打开网页」登录并确认额度卡片后重试",
                progress = -1,
            )
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val FIRST_SCAN_DELAY_MS = 3_500L
        const val RETRY_SCAN_DELAY_MS = 5_000L
        const val MAX_SCAN_RETRY = 3
        val LOGIN_URL_REGEX = Regex("(?i)(login|signin|sign-in|/auth|oauth)")
    }
}

/** 全尺寸、不拦截触摸、永不显示的宿主容器，用于承载后台 WebView。 */
class HiddenWebFrame(context: Context) : FrameLayout(context) {
    init {
        isClickable = false
        isFocusable = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
}
