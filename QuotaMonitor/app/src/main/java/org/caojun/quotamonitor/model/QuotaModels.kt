package org.caojun.quotamonitor.model

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
    /**
     * 网页上的原始百分比文本（如 "29"、"13.51"、"100"）。
     * 展示时优先使用它，小数位数与网页完全一致；
     * [percent] 仅用于计算进度条长度。
     */
    val percentText: String? = null,
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
    /** 页面上的关键文本行（兜底展示） */
    val lines: List<String> = emptyList(),
    /** 套餐剩余时间原文（如 "28天"），由界面套用本地化模板展示 */
    val remainText: String? = null,
    /** 套餐结束时间原文（如 "2026-12-20 23:59"） */
    val endText: String? = null,
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
