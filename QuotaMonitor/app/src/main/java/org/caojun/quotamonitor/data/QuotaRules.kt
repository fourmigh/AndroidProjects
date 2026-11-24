package org.caojun.quotamonitor.data

import org.caojun.quotamonitor.model.MetricMode
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.RuleDiagnostic
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
            // 防御：指标名若是重置倒计时（历史误配置），直接忽略
            if (isResetLikeLabel(rule.label)) continue
            val item = extractMetric(config, rule, lines) ?: continue
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

    private fun extractMetric(config: PlatformConfig, rule: MetricRule, lines: List<String>): QuotaItem? =
        bestCandidate(config, rule, lines)?.item

    private class Candidate(
        val idx: Int,
        val item: QuotaItem,
        val valueOffset: Int,
        val clean: Boolean,
    )

    /**
     * 在指标名的所有出现位置中挑最佳候选：
     * 优先「指标行到数值行之间没有其他标题行」的候选，再取距离数值最近的候选。
     */
    private fun bestCandidate(
        config: PlatformConfig,
        rule: MetricRule,
        lines: List<String>,
    ): Candidate? {
        if (rule.label.isBlank()) return null
        val indexes = lines.indices.filter { i ->
            lines[i].length <= 100 && labelsMatch(rule.label, lines[i])
        }
        var bestClean: Candidate? = null
        var bestAny: Candidate? = null
        for (idx in indexes) {
            val window = metricWindow(config, rule, lines, idx)
            val result = extractFromWindow(rule, window) ?: continue
            val offset = result.valueOffset
            val hasHeadingBetween = (1 until offset).any { k -> isHeadingLike(window[k]) }
            val candidate = Candidate(idx, result.item, offset, !hasHeadingBetween)
            if (bestAny == null || offset < bestAny.valueOffset) bestAny = candidate
            if (candidate.clean && (bestClean == null || offset < bestClean.valueOffset)) {
                bestClean = candidate
            }
        }
        return bestClean ?: bestAny
    }

    /**
     * “标题样式”行：无数字、不是重置倒计时、长度较短（如 `My usage`、`Limits`、`套餐用量限额`）。
     * 指标行与数值行之间若隔着这类行，说明该匹配位置很可能是图例或分组标题。
     */
    private fun isHeadingLike(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 40) return false
        if (t.any { it.isDigit() }) return false
        if (t.contains('%')) return false
        val lower = t.lowercase()
        if (RESET_KEYWORDS.any { lower.contains(it) }) return false
        return true
    }

    private val RESET_KEYWORDS = listOf(
        "reset", "renew", "expires", "到期", "重置", "resets in",
    )

    /**
     * 指标附近的文本窗口：从指标行开始，最多向下 [MetricRule.lookAhead] 行，
     * 但一旦遇到**其他已配置指标**的行就截断，避免跨区块取到别的指标的值。
     */
    private fun metricWindow(
        config: PlatformConfig,
        rule: MetricRule,
        lines: List<String>,
        idx: Int,
    ): List<String> {
        val maxEnd = minOf(idx + 1 + rule.lookAhead, lines.size)
        var end = maxEnd
        for (j in idx + 1 until maxEnd) {
            val line = lines[j]
            val isOtherMetric = config.metrics.any { other ->
                !other.label.equals(rule.label, ignoreCase = true) && labelsMatch(other.label, line)
            }
            if (isOtherMetric) {
                end = j
                break
            }
        }
        return lines.subList(idx, end)
    }

    private data class WindowResult(val item: QuotaItem, val valueOffset: Int)

    private fun extractFromWindow(rule: MetricRule, window: List<String>): WindowResult? {
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
                WindowResult(
                    item = QuotaItem(
                        label = rule.label,
                        percent = value.coerceIn(0.0, 100.0),
                        resetHint = reset,
                        percentText = raw,
                    ),
                    valueOffset = lineOffsetOf(windowText, m.range.first),
                )
            }

            MetricMode.PERCENT_NEXT_NUMBER -> {
                val regex = rule.valueRegex?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Regex(it) }.getOrNull() }
                    ?: Regex("""^\s*(\d+(?:\.\d+)?)\s*%?\s*$""")
                var raw: String? = null
                var offset = -1
                for ((i, line) in window.drop(1).withIndex()) {
                    val m = regex.matchEntire(line) ?: continue
                    raw = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                    if (raw != null) {
                        offset = i + 1
                        break
                    }
                }
                val value = raw?.toDoubleOrNull() ?: return null
                WindowResult(
                    item = QuotaItem(
                        label = rule.label,
                        percent = value.coerceIn(0.0, 100.0),
                        resetHint = reset,
                        percentText = raw,
                    ),
                    valueOffset = offset,
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
                WindowResult(
                    item = QuotaItem(label = rule.label, used = used, total = total, resetHint = reset),
                    valueOffset = lineOffsetOf(windowText, m.range.first),
                )
            }
        }
    }

    /** 字符偏移 → 行偏移（0 = 指标行本身）。 */
    private fun lineOffsetOf(windowText: String, charIndex: Int): Int {
        if (charIndex <= 0) return 0
        return windowText.substring(0, charIndex).count { it == '\n' }
    }

    /**
     * 解析诊断：对每条指标规则给出「匹配到的行、窗口、取值」，
     * 便于用户排查规则是否取错数据。
     */
    fun diagnose(config: PlatformConfig, scanJson: String): List<RuleDiagnostic> {
        val scan = runCatching { JSONObject(scanJson) }.getOrNull() ?: return emptyList()
        val lines = parseLines(scan)
        return config.metrics.map { rule ->
            val best = bestCandidate(config, rule, lines)
            if (best == null) {
                RuleDiagnostic(label = rule.label, matchedIndex = -1, matchedLine = null, window = emptyList())
            } else {
                RuleDiagnostic(
                    label = rule.label,
                    matchedIndex = best.idx,
                    matchedLine = lines[best.idx],
                    window = metricWindow(config, rule, lines, best.idx),
                    value = diagValueText(best.item),
                    resetHint = best.item.resetHint,
                )
            }
        }
    }

    private fun diagValueText(item: QuotaItem): String = when {
        item.percent != null -> (item.percentText ?: item.percent.toString()) + "%"
        item.used != null && item.total != null -> "${item.used} / ${item.total}"
        item.used != null -> item.used.toString()
        else -> "—"
    }

    private fun parseLines(scan: JSONObject): List<String> = buildList {
        val arr = scan.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
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

    /** 是否像“重置倒计时”而不是指标名（用于拒绝历史误配置）。 */
    private fun isResetLikeLabel(label: String): Boolean {
        val l = label.trim()
        if (l.isEmpty()) return true
        return RESET_LABEL_REGEX.containsMatchIn(l)
    }

    private val RESET_LABEL_REGEX = Regex(
        """(?i)(后重置|重置|resets?\s+in|renew|expires?)\s*$""",
    )

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
        // 必须是页面行包含指标名；绝不反向包含，
        // 否则页面上的短符号（"-"、"5"、"in" 等）会被误判成指标行。
        val a = ruleLabel.replace(" ", "").lowercase()
        val b = text.replace(" ", "").lowercase()
        return b.contains(a)
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
