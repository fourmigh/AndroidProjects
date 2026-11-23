package org.caojun.quotamonitor.model

/**
 * 支持的控制台平台。
 */
enum class Platform(
    val title: String,
    val url: String,
    val loginHint: String,
) {
    OPENCODE(
        title = "OpenCode Console",
        url = "https://opencode.ai/console",
        loginHint = "在页面中完成登录后返回仪表盘",
    ),
    VOLC(
        title = "火山引擎 Coding Plan",
        url = "https://console.volcengine.com/ark/region:cn-beijing/subscription/coding-plan",
        loginHint = "在页面中完成登录后返回仪表盘",
    ),
}

enum class SyncStatus {
    /** 尚未同步过（也无缓存） */
    IDLE,

    /** 正在加载 / 提取 */
    LOADING,

    /** 页面要求登录 */
    NEED_LOGIN,

    /** 成功拿到数据 */
    OK,

    /** 加载或提取失败 */
    ERROR,
}

enum class Source { NETWORK, DOM }

/**
 * 单项额度指标。used/total 与 percent 至少有一组有效。
 */
data class QuotaItem(
    val label: String,
    val used: Double? = null,
    val total: Double? = null,
    val percent: Double? = null,
    /** 该项专属的重置倒计时（如 "Resets in 2h 15m"、"48分后重置"） */
    val resetHint: String? = null,
) {
    /** 0..1 的进度，用于进度条 */
    val fraction: Float?
        get() = when {
            percent != null -> (percent / 100.0).toFloat()
            used != null && total != null && total > 0 -> (used / total).toFloat()
            else -> null
        }
}

/**
 * 一次抓取的完整快照。
 */
data class QuotaSnapshot(
    val planName: String? = null,
    val items: List<QuotaItem> = emptyList(),
    /** 页面上的关键文本行（结构化解析失败时的兜底展示） */
    val lines: List<String> = emptyList(),
    /** 套餐级汇总信息（如 "剩余 28天 · 结束于 2026-12-20 23:59"） */
    val resetAt: String? = null,
    val source: Source = Source.DOM,
    val capturedAt: Long = System.currentTimeMillis(),
)

data class PlatformState(
    val status: SyncStatus = SyncStatus.IDLE,
    val snapshot: QuotaSnapshot? = null,
    val message: String? = null,
    /** 0..100，WebView 加载进度；-1 表示无 */
    val progress: Int = -1,
) {
    val isLoading: Boolean get() = status == SyncStatus.LOADING
}
