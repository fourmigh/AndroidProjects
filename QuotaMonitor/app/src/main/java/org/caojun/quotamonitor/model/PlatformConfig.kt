package org.caojun.quotamonitor.model

/**
 * 单个额度指标的提取规则。
 */
data class MetricRule(
    /** 在网页上显示的指标名（用于定位行）与卡片标题 */
    val label: String,
    val mode: MetricMode = MetricMode.PERCENT_INLINE,
    /** 取值正则：PERCENT_* 取第 1 组；USED_TOTAL 取前 2 组。为空时使用默认正则 */
    val valueRegex: String? = null,
    /** 指标附近“重置倒计时”行的正则 */
    val resetRegex: String? = null,
    /** 从指标名所在行向下看多少行 */
    val lookAhead: Int = 6,
)

enum class MetricMode {
    /** 同行百分比：`29% used` */
    PERCENT_INLINE,

    /** 指标下方单独一行的大号数字：`13.51` 或 `13.51 %` */
    PERCENT_NEXT_NUMBER,

    /** 已用 / 总量：`45,300 / 90,600` */
    USED_TOTAL;

    companion object {
        fun fromJson(name: String): MetricMode = when (name) {
            "percent_next_number" -> PERCENT_NEXT_NUMBER
            "used_total" -> USED_TOTAL
            else -> PERCENT_INLINE
        }
    }
}

/**
 * 一个可配置的额度平台。
 */
data class PlatformConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    /** 登录页关键词（用于判定“需登录”） */
    val loginKeywords: List<String> = emptyList(),
    /** 宣传页关键词（未登录时的营销落地页） */
    val marketingKeywords: List<String> = emptyList(),
    /** 必须命中的“额度页”特征正则；为空时以“至少识别出一个指标”判定 */
    val pageMarkerRegex: String? = null,
    /** 套餐名正则（第 1 组为套餐名） */
    val planNameRegex: String? = null,
    /** 包含套餐名的整行正则 */
    val planLineRegex: String? = null,
    /** “剩余时间”字段名 */
    val remainKey: String? = null,
    /** “结束时间”字段名 */
    val endKey: String? = null,
    val metrics: List<MetricRule> = emptyList(),
)

/**
 * 自动识别得到的候选指标（供配置向导勾选确认）。
 */
data class DetectedMetric(
    val label: String,
    val mode: MetricMode,
    val valueRegex: String?,
    val resetRegex: String?,
    /** 示例值文本，用于在向导里展示（如 "29%"、"45,300 / 90,600"） */
    val sampleText: String,
    val resetHint: String? = null,
)

/**
 * 解析诊断：某条指标规则在最近一次页面扫描中的匹配情况。
 */
data class RuleDiagnostic(
    val label: String,
    /** -1 表示未匹配到任何行 */
    val matchedIndex: Int,
    val matchedLine: String?,
    /** 指标附近的文本窗口（已被下一个指标行截断） */
    val window: List<String> = emptyList(),
    /** 实际取到的值（如 "29%"、"300 / 900"），null 表示未取到 */
    val value: String? = null,
    val resetHint: String? = null,
)
