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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.data.JsScripts
import org.caojun.quotamonitor.data.PlatformStore
import org.caojun.quotamonitor.data.QuotaCache
import org.caojun.quotamonitor.data.QuotaRules
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.model.PlatformState
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.SyncStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * 核心控制器：按平台配置为每个平台维护一个常驻 WebView（后台静默加载），
 * 通过「fetch/XHR 钩子 + DOM 扫描」两条通道提取额度数据。
 *
 * 平台完全由 [PlatformStore] 中的配置驱动，支持新增 / 编辑 / 删除，
 * 无需修改代码即可适配新的 AI 套餐页面。
 *
 * 所有公开方法都必须在主线程调用。
 */
class QuotaController(private val context: Context) {

    private val store = PlatformStore(context)

    /** 全部平台配置（Compose 可观察） */
    val configs: SnapshotStateList<PlatformConfig> = mutableStateListOf<PlatformConfig>()

    /** 提取状态（key = config.id） */
    val states: SnapshotStateMap<String, PlatformState> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val webViews = mutableMapOf<String, WebView>()
    private val sessions = mutableMapOf<String, Session>()
    private var hiddenContainer: ViewGroup? = null
    private var started = false

    private var defaultUa: String? = null
    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /** 配置向导使用的临时平台（不落盘）。 */
    private var draftConfig: PlatformConfig? = null

    /** 最近一次 DOM 扫描原始 JSON（供自动识别使用）。 */
    private var lastScanJson: String? = null

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
        var scanJson: String = "",
        var scanAttempts: Int = 0,
    )

    init {
        val loaded = store.loadAll()
        configs.addAll(loaded)
        loaded.forEach { config ->
            QuotaCache.load(context, config.id)?.let { snapshot ->
                states[config.id] = PlatformState(status = SyncStatus.OK, snapshot = snapshot)
            }
        }
    }

    // ------------------------------------------------------------ 配置管理

    fun findConfig(id: String): PlatformConfig? =
        draftConfig?.takeIf { it.id == id } ?: configs.firstOrNull { it.id == id }

    fun enabledConfigs(): List<PlatformConfig> = configs.filter { it.enabled }

    fun start() {
        if (started) return
        started = true
        enabledConfigs().forEach { refresh(it.id) }
    }

    fun refreshAll() {
        enabledConfigs().forEach { refresh(it.id) }
    }

    /** 新增配置：校验通过则保存并立即刷新。返回错误类型或 null。 */
    fun addConfig(config: PlatformConfig): PlatformStore.ValidationError? {
        val err = PlatformStore.validate(config)
        if (err != null) return err
        configs.add(config)
        store.saveAll(configs)
        if (config.enabled) refresh(config.id)
        return null
    }

    /** 更新配置：URL / 启用状态变化时重建 WebView。返回错误类型或 null。 */
    fun updateConfig(config: PlatformConfig): PlatformStore.ValidationError? {
        val err = PlatformStore.validate(config)
        if (err != null) return err
        val index = configs.indexOfFirst { it.id == config.id }
        if (index < 0) return null
        val old = configs[index]
        configs[index] = config
        store.saveAll(configs)
        if (old.url != config.url || old.enabled != config.enabled) {
            teardown(config.id, dropState = old.enabled != config.enabled && !config.enabled)
            if (config.enabled) refresh(config.id)
        }
        return null
    }

    fun deleteConfig(id: String) {
        teardown(id)
        configs.removeAll { it.id == id }
        states.remove(id)
        QuotaCache.clear(context, id)
        store.saveAll(configs)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val index = configs.indexOfFirst { it.id == id }
        if (index < 0) return
        val config = configs[index]
        if (config.enabled == enabled) return
        configs[index] = config.copy(enabled = enabled)
        store.saveAll(configs)
        if (enabled) refresh(id) else teardown(id)
    }

    fun restoreDefaults() {
        configs.forEach { teardown(it.id) }
        states.clear()
        val defaults = store.restoreDefaults()
        configs.clear()
        configs.addAll(defaults)
        defaults.forEach { config ->
            QuotaCache.load(context, config.id)?.let { snapshot ->
                states[config.id] = PlatformState(status = SyncStatus.OK, snapshot = snapshot)
            }
        }
        refreshAll()
    }

    fun exportJson(): String {
        val json = JSONObject().apply {
            put("version", 1)
            put("platforms", JSONArray().apply {
                configs.forEach { put(configToJson(it)) }
            })
        }
        return json.toString(2)
    }

    /** 导入配置（替换当前全部配置）。返回导入的平台数，失败返回 -1。 */
    fun importJson(text: String): Int {
        val configsToImport = runCatching {
            val root = JSONObject(text)
            PlatformStore.parseConfigs(root.getJSONArray("platforms"))
        }.getOrNull() ?: return -1
        if (configsToImport.isEmpty()) return -1
        for (config in configsToImport) {
            if (PlatformStore.validate(config) != null) return -1
        }
        configs.forEach { teardown(it.id) }
        states.clear()
        configs.clear()
        configs.addAll(configsToImport)
        store.saveAll(configs)
        configsToImport.forEach { config ->
            QuotaCache.load(context, config.id)?.let { snapshot ->
                states[config.id] = PlatformState(status = SyncStatus.OK, snapshot = snapshot)
            }
        }
        refreshAll()
        return configsToImport.size
    }

    fun newConfigId(): String = store.newId()

    /** 供界面获取本地化字符串（Toast 等非 Composable 场景）。 */
    fun text(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    // ------------------------------------------------------------ 配置向导（草稿）

    fun draftId(): String = DRAFT_ID

    /** 打开草稿页面（用于自动识别）。 */
    fun openDraft(url: String, name: String) {
        draftConfig = PlatformConfig(
            id = DRAFT_ID,
            name = name.ifBlank { url },
            url = url,
            metrics = emptyList(),
        )
        refresh(DRAFT_ID)
    }

    fun closeDraft() {
        draftConfig = null
        lastScanJson = null
        teardown(DRAFT_ID)
    }

    fun draftScanJson(): String? = lastScanJson

    // ------------------------------------------------------------ 刷新 / 提取

    fun refresh(id: String) {
        val config = findConfig(id) ?: return
        val wv = ensureWebView(config)
        val gen = (sessions[id]?.gen ?: 0) + 1
        sessions[id] = Session(gen = gen)
        val prev = states[id] ?: PlatformState()
        states[id] = prev.copy(status = SyncStatus.LOADING, message = null, progress = 0)
        wv.stopLoading()
        wv.loadUrl(config.url)
        scope.launch {
            delay(TIMEOUT_MS)
            finalizeTimeout(id, gen)
        }
    }

    fun toggleDesktopUa(id: String) {
        val wv = webViews[id] ?: return
        val current = wv.settings.userAgentString
        val next = if (current == desktopUa) (defaultUa ?: current) else desktopUa
        wv.settings.userAgentString = next
        refresh(id)
    }

    fun isDesktopUa(id: String): Boolean =
        webViews[id]?.settings?.userAgentString?.let { it == desktopUa } ?: true

    fun bindHidden(container: ViewGroup) {
        hiddenContainer = container
        webViews.values.forEach { moveTo(it, container) }
    }

    fun attachForDisplay(id: String, container: ViewGroup) {
        val config = findConfig(id) ?: return
        moveTo(ensureWebView(config), container)
    }

    fun detachFromDisplay(id: String, container: ViewGroup) {
        val wv = webViews[id] ?: return
        if (wv.parent !== container) return
        container.removeView(wv)
        hiddenContainer?.let { moveTo(wv, it) }
        val status = states[id]?.status
        if (status == SyncStatus.NEED_LOGIN || status == SyncStatus.IDLE || status == SyncStatus.ERROR) {
            refresh(id)
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

    /** 销毁某平台的 WebView / session（配置变更或删除时）。 */
    private fun teardown(id: String, dropState: Boolean = false) {
        sessions.remove(id)
        webViews.remove(id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        if (dropState) {
            states[id] = (states[id] ?: PlatformState()).copy(status = SyncStatus.IDLE, progress = -1)
        }
    }

    // ------------------------------------------------------------ WebView 装配

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(config: PlatformConfig): WebView {
        webViews[config.id]?.let { return it }
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
            webViewClient = Client(config.id)
            webChromeClient = Chrome(config.id)
            addJavascriptInterface(Bridge(config.id), "AndroidBridge")
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)
        webViews[config.id] = wv
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

    private inner class Client(private val id: String) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.evaluateJavascript(JsScripts.HOOK_SCRIPT, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val s = sessions[id] ?: return
            scheduleScan(id, s.gen, FIRST_SCAN_DELAY_MS)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame != true) return
            val st = states[id] ?: return
            if (st.status != SyncStatus.LOADING) return
            states[id] = st.copy(
                status = SyncStatus.ERROR,
                message = context.getString(
                    R.string.msg_page_load_failed,
                    error?.description ?: context.getString(R.string.msg_network_error),
                ),
                progress = -1,
            )
        }
    }

    private inner class Chrome(private val id: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            val st = states[id] ?: return
            if (st.status == SyncStatus.LOADING) {
                states[id] = st.copy(progress = newProgress)
            }
        }
    }

    /** JS → Kotlin 桥。方法运行在 WebView 脚本线程，统一投递回主线程。 */
    private inner class Bridge(private val id: String) {

        @JavascriptInterface
        fun onNetwork(url: String, body: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                handleNetwork(id, body)
            }
        }

        @JavascriptInterface
        fun onDomScan(json: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                handleDomScan(id, json)
            }
        }
    }

    // ------------------------------------------------------------ 提取流水线

    private fun scheduleScan(id: String, gen: Int, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            val s = sessions[id] ?: return@launch
            if (s.gen != gen) return@launch
            val wv = webViews[id] ?: return@launch
            wv.evaluateJavascript(JsScripts.DOM_SCAN_SCRIPT, null)
        }
    }

    private fun handleDomScan(id: String, json: String) {
        val s = sessions[id] ?: return
        runCatching {
            val obj = JSONObject(json)
            s.scanHasPassword = obj.optBoolean("hasPassword", false)
            s.scanLoginLike = obj.optBoolean("loginLike", false)
            s.scanUrl = obj.optString("url", "")
            s.scanJson = json
            lastScanJson = json
            val rawLines = buildList {
                val arr = obj.optJSONArray("lines")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            s.scanLines = rawLines
            findConfig(id)?.let { config ->
                s.dom = QuotaRules.parseDom(config, json)
            }
        }
        if (s.dom == null && id != DRAFT_ID && s.scanAttempts < MAX_SCAN_RETRY) {
            s.scanAttempts++
            scheduleScan(id, s.gen, RETRY_SCAN_DELAY_MS)
        }
        tryComplete(id, s)
    }

    private fun handleNetwork(id: String, body: String) {
        val s = sessions[id] ?: return
        if (!s.seenBodies.add(body.hashCode())) return
        val config = findConfig(id) ?: return
        val items = QuotaRules.parseNetwork(config, body) ?: return
        for (item in items) {
            if (s.networkItems.size >= 60) break
            if (s.networkItems.none { it.label == item.label }) s.networkItems.add(item)
        }
        tryComplete(id, s)
    }

    private fun tryComplete(id: String, s: Session) {
        val config = findConfig(id) ?: return
        if (id == DRAFT_ID) {
            // 草稿只采集数据，不做状态推断
            if (s.dom != null || s.networkItems.isNotEmpty()) {
                states[id] = (states[id] ?: PlatformState()).copy(status = SyncStatus.OK, progress = -1)
            }
            return
        }
        if (s.dom != null) {
            val merged = QuotaRules.merge(s.networkItems, s.dom, config) ?: return
            if (merged.items.isEmpty()) return
            complete(id, merged)
            return
        }
        val strong = s.networkItems.filter { QuotaRules.isKnownLabel(config, it.label) }
        if (strong.isNotEmpty()) {
            QuotaRules.merge(strong, null, config)?.let { complete(id, it) }
        }
    }

    private fun complete(id: String, snapshot: QuotaSnapshot) {
        states[id] = PlatformState(
            status = SyncStatus.OK,
            snapshot = snapshot,
            message = null,
            progress = -1,
        )
        QuotaCache.save(context, id, snapshot)
    }

    private fun finalizeTimeout(id: String, gen: Int) {
        if (id == DRAFT_ID) return
        val s = sessions[id] ?: return
        if (s.gen != gen) return
        val st = states[id] ?: return
        if (st.status != SyncStatus.LOADING) return
        val config = findConfig(id) ?: return

        s.dom?.let { dom ->
            if (dom.items.isNotEmpty()) {
                complete(id, QuotaRules.merge(s.networkItems, dom, config) ?: dom)
                return
            }
        }

        val strong = s.networkItems.filter { QuotaRules.isKnownLabel(config, it.label) }
        if (strong.isNotEmpty()) {
            QuotaRules.merge(strong, null, config)?.let {
                complete(id, it)
                return
            }
        }

        val url = s.scanUrl.ifBlank { webViews[id]?.url ?: "" }
        val looksLogin = QuotaRules.looksLikeLogin(config, url, s.scanHasPassword, s.scanLoginLike, s.scanLines) ||
            QuotaRules.looksLikeMarketing(config, s.scanLines)

        states[id] = if (looksLogin) {
            st.copy(
                status = SyncStatus.NEED_LOGIN,
                message = context.getString(R.string.login_hint_default),
                progress = -1,
            )
        } else {
            st.copy(
                status = SyncStatus.ERROR,
                message = context.getString(R.string.msg_quota_not_found),
                progress = -1,
            )
        }
    }

    private companion object {
        const val DRAFT_ID = "__draft"
        const val TIMEOUT_MS = 30_000L
        const val FIRST_SCAN_DELAY_MS = 3_500L
        const val RETRY_SCAN_DELAY_MS = 5_000L
        const val MAX_SCAN_RETRY = 3
    }
}

private fun configToJson(config: PlatformConfig): JSONObject = JSONObject().apply {
    put("id", config.id)
    put("name", config.name)
    put("url", config.url)
    put("enabled", config.enabled)
    put("loginKeywords", JSONArray(config.loginKeywords))
    put("marketingKeywords", JSONArray(config.marketingKeywords))
    put("pageMarkerRegex", config.pageMarkerRegex ?: JSONObject.NULL)
    put("planNameRegex", config.planNameRegex ?: JSONObject.NULL)
    put("planLineRegex", config.planLineRegex ?: JSONObject.NULL)
    put("remainKey", config.remainKey ?: JSONObject.NULL)
    put("endKey", config.endKey ?: JSONObject.NULL)
    put("metrics", JSONArray().apply {
        config.metrics.forEach { m ->
            put(JSONObject().apply {
                put("label", m.label)
                put("mode", m.mode.name.lowercase())
                put("valueRegex", m.valueRegex ?: JSONObject.NULL)
                put("resetRegex", m.resetRegex ?: JSONObject.NULL)
                put("lookAhead", m.lookAhead)
            })
        }
    })
}

/** 全尺寸、不拦截触摸、永不显示的宿主容器，用于承载后台 WebView。 */
class HiddenWebFrame(context: Context) : FrameLayout(context) {
    init {
        isClickable = false
        isFocusable = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
}
