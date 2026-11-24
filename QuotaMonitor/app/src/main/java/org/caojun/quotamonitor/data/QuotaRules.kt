package org.caojun.quotamonitor.data

import org.caojun.quotamonitor.model.MetricMode
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.Source
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 配置驱动的通用规则引擎：按 [PlatformConfig] 里声明的指标规则从 DOM 文本中提取额度。
 *
 * 页面不含任何已配置指标（登录页 / 宣传页 / 其他页面）时返回 null，
 * 由控制器判定为「需登录」或「失败」，绝不盲猜数字。
 */
object QuotaRules {

    /** 从 DOM 扫描结果提取额度；无法确认额度页时返回 null。 */
    fun parseDom(config: PlatformConfig, scanJson: String): QuotaSnapshot? {
        val scan = runCatching { JSONObject(scanJson) }.getOrNull() ?: return null
        val lines = mutableListOf<String>()
        scan.optJSONArray("lines")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { lines.add(it) }
            }
        }
        val bars = scan.optJSONArray("bars") ?: JSONArray()

        // 页面特征校验
        val joined = lines.joinToString("\n")
        val marker = config.pageMarkerRegex?.takeIf { it.isNotBlank() }
        if (marker != null) {
            val re = runCatching { Regex(marker) }.getOrNull() ?: return null
            if (!re.containsMatchIn(joined)) return null
        }

        val items = LinkedHashMap<String, QuotaItem>()
        for (rule in config.metrics) {
            val item = extractMetric(rule, lines) ?: continue
            items.putIfAbsent(item.label, item)
        }

        // 进度条兜底：标签命中已配置的指标名
        if (items.isEmpty()) {
            for (i in 0 until bars.length()) {
                val bar = bars.optJSONObject(i) ?: continue
                val barLabel = bar.optString("label")
                val rule = config.metrics.firstOrNull {
                    labelsMatch(it.label, barLabel)
                } ?: continue
                val now = bar.optDouble("now", Double.NaN)
                if (now.isNaN()) continue
                val max = bar.optDouble("max", Double.NaN)
                val item = if (!max.isNaN() && max > 0 && max != 1.0) {
                    QuotaItem(label = rule.label, used = now, total = max)
                } else {
                    val p = if (now <= 1.0) now * 100 else now
                    QuotaItem(label = rule.label, percent = p.coerceIn(0.0, 100.0))
                }
                items[item.label] = item
            }
        }

        // 必须至少识别出一个指标，才算额度页
        if (items.isEmpty()) return null

        val planName = detectPlanName(config, joined, lines)
        val infoLines = listOfNotNull(
            config.remainKey,
            config.endKey,
        ).mapNotNull { key ->
            lines.firstOrNull { it.startsWith(key) && it.length <= 60 }
        }

        return QuotaSnapshot(
            planName = planName,
            items = items.values.toList(),
            lines = infoLines,
            remainText = infoValue(lines, config.remainKey),
            endText = infoValue(lines, config.endKey),
            source = Source.DOM,
        )
    }

    private fun extractMetric(rule: MetricRule, lines: List<String>): QuotaItem? {
        if (rule.label.isBlank()) return null
        val idx = lines.indexOfFirst { labelsMatch(rule.label, it) && it.length <= 100 }
        if (idx < 0) return null
        val windowEnd = minOf(idx + 1 + rule.lookAhead, lines.size)
        val window = lines.subList(idx, windowEnd)
        val windowText = window.joinToString("\n")
        val reset = rule.resetRegex?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Regex(it) }.getOrNull() }
            ?.find(windowText)?.value?.trim()

        return when (rule.mode) {
            MetricMode.PERCENT_INLINE -> {
                val regex = rule.valueRegex?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Regex(it) }.getOrNull() }
                    ?: Regex("""(\d+(?:\.\d+)?)\s*%""")
                val m = regex.find(windowText) ?: return null
                val raw = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                val value = raw?.toDoubleOrNull() ?: m.value.toDoubleOrNull() ?: return null
                QuotaItem(
                    label = rule.label,
                    percent = value.coerceIn(0.0, 100.0),
                    resetHint = reset,
                    percentText = raw,
                )
            }

            MetricMode.PERCENT_NEXT_NUMBER -> {
                val regex = rule.valueRegex?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Regex(it) }.getOrNull() }
                    ?: Regex("""^\s*(\d+(?:\.\d+)?)\s*%?\s*$""")
                var raw: String? = null
                for (line in window.drop(1)) {
                    val m = regex.matchEntire(line) ?: continue
                    raw = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                    if (raw != null) break
                }
                val value = raw?.toDoubleOrNull() ?: return null
                QuotaItem(
                    label = rule.label,
                    percent = value.coerceIn(0.0, 100.0),
                    resetHint = reset,
                    percentText = raw,
                )
            }

            MetricMode.USED_TOTAL -> {
                val regex = rule.valueRegex?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Regex(it) }.getOrNull() }
                    ?: Regex("""(\d[\d,]*(?:\.\d+)?)\s*(?:/|of|／)\s*(\d[\d,]*(?:\.\d+)?)""")
                val m = regex.find(windowText) ?: return null
                val used = parseNumber(m.groupValues.getOrNull(1) ?: return null) ?: return null
                val total = parseNumber(m.groupValues.getOrNull(2) ?: return null) ?: return null
                if (total <= 0) return null
                QuotaItem(label = rule.label, used = used, total = total, resetHint = reset)
            }
        }
    }

    private fun detectPlanName(config: PlatformConfig, joined: String, lines: List<String>): String? {
        config.planNameRegex?.takeIf { it.isNotBlank() }?.let { pattern ->
            val re = runCatching { Regex(pattern) }.getOrNull() ?: return@let
            val m = re.find(joined) ?: return@let
            m.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        config.planLineRegex?.takeIf { it.isNotBlank() }?.let { pattern ->
            val re = runCatching { Regex(pattern) }.getOrNull() ?: return@let
            lines.firstOrNull { re.containsMatchIn(it) }?.let { return it }
        }
        return null
    }

    /** 取 "剩余时间/结束时间" 字段值：同行或下一行。 */
    private fun infoValue(lines: List<String>, key: String?): String? {
        if (key.isNullOrBlank()) return null
        val idx = lines.indexOfFirst { it.startsWith(key) }
        if (idx < 0) return null
        val inline = lines[idx].substringAfter(key).trim(' ', '：', ':')
        if (inline.isNotEmpty()) return inline
        return lines.getOrNull(idx + 1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------ 网络层

    /** 从拦截的 JSON 中找出带指标名的额度对象；指标名必须命中配置。 */
    fun parseNetwork(config: PlatformConfig, body: String): List<QuotaItem>? {
        val root = runCatching { JSONTokener(body).nextValue() }.getOrNull() ?: return null
        val items = LinkedHashMap<String, QuotaItem>()
        collectItems(root, config, items)
        return items.values.take(40).ifEmpty { null }
    }

    private fun collectItems(node: Any?, config: PlatformConfig, out: MutableMap<String, QuotaItem>) {
        when (node) {
            is JSONObject -> {
                val label = labelOf(node)
                if (label != null && isKnownLabel(config, label)) {
                    itemFromObject(node, label)?.let { out.putIfAbsent(it.label, it) }
                }
                for (key in node.keys()) {
                    val v = node.opt(key)
                    if (v is JSONObject || v is JSONArray) collectItems(v, config, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectItems(node.opt(i), config, out)
            }
        }
    }

    private val LABEL_KEYS = setOf(
        "name", "metric", "label", "title", "type", "dimension", "key", "code", "model",
    )
    private val USED_KEYS = setOf(
        "used", "use", "consumed", "usage", "usedquota", "usedcount",
        "requestcount", "requests", "usedamount", "chargeused",
        "usedinputtokens", "usedtokens", "tokenused",
    )
    private val TOTAL_KEYS = setOf(
        "limit", "total", "quota", "max", "maximum", "capacity", "totalquota",
        "limitquota", "quotalimit", "allowance", "totalamount", "requestlimit",
        "maxrequests", "totaltokens", "tokenlimit",
    )
    private val REMAIN_KEYS = setOf("remaining", "remain", "left", "balance", "unused", "available")
    private val PERCENT_KEYS = setOf("percent", "percentage", "usagepercent", "usedpercent", "ratio", "progress")

    private fun labelOf(obj: JSONObject): String? {
        for (k in LABEL_KEYS) {
            val v = obj.opt(k)
            if (v is String && v.isNotBlank() && v.length <= 48) return v
        }
        return null
    }

    private fun itemFromObject(obj: JSONObject, label: String): QuotaItem? {
        val used = firstNumber(obj, USED_KEYS)
        val total = firstNumber(obj, TOTAL_KEYS)
        val remain = firstNumber(obj, REMAIN_KEYS)
        val percent = firstNumber(obj, PERCENT_KEYS)
        return when {
            used != null && total != null && total > 0 ->
                QuotaItem(label = label, used = used, total = total)
            remain != null && total != null && total > 0 ->
                QuotaItem(label = label, used = total - remain, total = total)
            used != null && remain != null && used + remain > 0 ->
                QuotaItem(label = label, used = used, total = used + remain)
            percent != null -> {
                val p = if (percent <= 1.0) percent * 100 else percent
                QuotaItem(label = label, percent = p.coerceIn(0.0, 100.0))
            }
            else -> null
        }
    }

    private fun firstNumber(obj: JSONObject, keys: Set<String>): Double? {
        for (key in obj.keys()) {
            val norm = key.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (norm in keys) {
                when (val n = obj.opt(key)) {
                    is Number -> return n.toDouble()
                    is String -> parseNumber(n)?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseNumber(s: String): Double? {
        val cleaned = s.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val suffix = cleaned.lastOrNull()?.lowercaseChar()
        val multiplier = when (suffix) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            'b' -> 1_000_000_000.0
            else -> 1.0
        }
        val core = if (multiplier > 1.0) cleaned.dropLast(1) else cleaned
        return core.toDoubleOrNull()?.let { it * multiplier }
    }

    /** 是否命中配置里的指标名（双向包含、忽略大小写与空格）。 */
    fun isKnownLabel(config: PlatformConfig, label: String): Boolean {
        if (label.isBlank()) return false
        return config.metrics.any { labelsMatch(it.label, label) }
    }

    private fun labelsMatch(ruleLabel: String, text: String): Boolean {
        if (ruleLabel.isBlank() || text.isBlank()) return false
        val a = ruleLabel.replace(" ", "").lowercase()
        val b = text.replace(" ", "").lowercase()
        return b.contains(a) || a.contains(b)
    }

    /** DOM 精准数据优先，网络数据仅补充已知指标的项。 */
    fun merge(networkItems: List<QuotaItem>, dom: QuotaSnapshot?, config: PlatformConfig): QuotaSnapshot? {
        val known = networkItems.filter { isKnownLabel(config, it.label) }
        if (dom == null) {
            if (known.isEmpty()) return null
            return QuotaSnapshot(items = known, source = Source.NETWORK)
        }
        if (known.isEmpty()) return dom
        val merged = LinkedHashMap<String, QuotaItem>()
        dom.items.forEach { merged.putIfAbsent(it.label, it) }
        known.forEach { net ->
            val duplicate = merged.keys.any { labelsMatch(it, net.label) }
            if (!duplicate && merged.size < 60) merged.putIfAbsent(net.label, net)
        }
        return dom.copy(items = merged.values.toList(), source = Source.DOM)
    }

    /** 未登录营销页特征。 */
    fun looksLikeMarketing(config: PlatformConfig, lines: List<String>): Boolean {
        if (config.marketingKeywords.isEmpty()) return false
        val head = lines.take(150).joinToString("\n")
        return config.marketingKeywords.any { head.contains(it, ignoreCase = true) }
    }

    /** 页面登录特征。 */
    fun looksLikeLogin(
        config: PlatformConfig,
        url: String,
        hasPassword: Boolean,
        loginLike: Boolean,
        lines: List<String>,
    ): Boolean {
        if (hasPassword || loginLike) return true
        if (LOGIN_URL_REGEX.containsMatchIn(url)) return true
        if (config.loginKeywords.isEmpty()) return false
        val head = lines.take(150).joinToString("\n")
        return config.loginKeywords.any { head.contains(it, ignoreCase = true) }
    }

    private val LOGIN_URL_REGEX = Regex("(?i)(login|signin|sign-in|/auth|oauth)")
}
