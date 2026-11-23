package org.caojun.quotamonitor.data

import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.Source
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs

/**
 * 把拦截到的网络 JSON 与 DOM 扫描结果解析成 [QuotaSnapshot]。
 *
 * 登录后的额度卡片结构固定，因此 DOM 层采用**平台专属精准解析**：
 * - OpenCode：`5-hour window` / `Weekly` / `Monthly` + `Resets in …` + `n% used`
 * - 火山方舟：`近5小时用量` / `近一周用量` / `近一月用量` + `…后重置` + 大号百分比
 *
 * 页面不含上述特征（停留在宣传页 / 登录页）时返回 null，
 * 由控制器判定为「需登录」——绝不用通用启发式猜测，避免把宣传文案当额度。
 *
 * 网络层仍保留字段对解析（used/limit/remaining/percent），
 * 仅在 DOM 确认额度页之后作为补充数据源。
 */
object QuotaParser {

    // ------------------------------------------------------------ OpenCode

    /** OpenCode 三项指标名 */
    private val OC_METRICS = listOf("5-hour window", "weekly", "monthly")
    private val OC_PERCENT_USED = Regex("""(?i)(\d+(?:\.\d+)?)\s*%\s*used""")
    private val OC_RESET = Regex("""(?i)resets?\s+in\s+\S[^,;.\n]*""")
    private val OC_PLAN = Regex("""(?i)when\s+your\s+(.+?)\s+usage\s+resets""")

    // ------------------------------------------------------------ 火山方舟

    private val VC_METRICS = listOf("近5小时用量", "近一周用量", "近一月用量")
    private val VC_PLAN_LINE = Regex("""Coding\s*Plan\s*\S*\s*套餐""")
    private val VC_INFO_KEYS = listOf("开始时间", "结束时间", "剩余时间", "计费模式", "自动续费")
    private val VC_RESET_LINE = Regex("""\d[^ \n]*重置""")
    /** 大号百分比：单独一行的数字（可带尾随 %） */
    private val VC_PERCENT_LINE = Regex("""^\s*(\d+(?:\.\d+)?)\s*%?\s*$""")

    // ------------------------------------------------------------ DOM 层

    /** 从 DOM 扫描结果（JS 回传的 JSON）提取额度；无法确认额度页时返回 null。 */
    fun parseDom(platform: Platform, scanJson: String): QuotaSnapshot? {
        val scan = try {
            JSONObject(scanJson)
        } catch (e: Exception) {
            return null
        }
        val lines = mutableListOf<String>()
        val arr = scan.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let { lines.add(it) }
        }
        val bars = scan.optJSONArray("bars") ?: JSONArray()
        return when (platform) {
            Platform.OPENCODE -> parseOpenCode(lines, bars)
            Platform.VOLC -> parseVolc(lines, bars)
        }
    }

    private fun parseOpenCode(lines: List<String>, bars: JSONArray): QuotaSnapshot? {
        val joined = lines.joinToString("\n")
        // 额度页确认特征：必须出现 "n% used"（宣传页没有此模式）。
        if (!OC_PERCENT_USED.containsMatchIn(joined)) return null

        val items = LinkedHashMap<String, QuotaItem>()

        for (metric in OC_METRICS) {
            val idx = lines.indexOfFirst { it.contains(metric, ignoreCase = true) && it.length <= 80 }
            if (idx < 0) continue
            val window = lines.subList(idx, minOf(idx + 4, lines.size))
            val windowText = window.joinToString(" \n ")

            val percentMatch = OC_PERCENT_USED.find(windowText)
            val percentText = percentMatch?.groupValues?.get(1)
            val percent = percentText?.toDoubleOrNull()
            val reset = OC_RESET.find(windowText)?.value?.trim()

            if (percent != null) {
                val label = when {
                    metric.equals("5-hour window", ignoreCase = true) -> "5-hour window"
                    metric.equals("weekly", ignoreCase = true) -> "Weekly"
                    else -> "Monthly"
                }
                items[label] = QuotaItem(
                    label = label,
                    percent = percent.coerceIn(0.0, 100.0),
                    resetHint = reset,
                    percentText = percentText,
                )
            }
        }

        // 进度条兜底（仅接受已知指标名，防止营销页图表混入）
        if (items.isEmpty()) {
            for (i in 0 until bars.length()) {
                val bar = bars.optJSONObject(i) ?: continue
                val label = bar.optString("label")
                val match = OC_METRICS.firstOrNull { label.contains(it, ignoreCase = true) } ?: continue
                val now = bar.optDouble("now", Double.NaN)
                if (now.isNaN()) continue
                val max = bar.optDouble("max", Double.NaN)
                val item = if (!max.isNaN() && max > 0 && max != 1.0) {
                    QuotaItem(label = match, used = now, total = max)
                } else {
                    val p = if (now <= 1.0) now * 100 else now
                    QuotaItem(label = match, percent = p.coerceIn(0.0, 100.0))
                }
                items[item.label] = item
            }
        }

        if (items.isEmpty()) return null

        // 套餐名："When your Go usage resets" → "Go"
        val planName = OC_PLAN.find(joined)?.groupValues?.get(1)?.trim()

        return QuotaSnapshot(
            planName = planName,
            items = items.values.toList(),
            lines = items.values.mapNotNull { it.resetHint }.ifEmpty { emptyList() },
            remainText = null,
            endText = null,
            source = Source.DOM,
        )
    }

    private fun parseVolc(lines: List<String>, bars: JSONArray): QuotaSnapshot? {
        // 额度页确认特征：三项指标名至少出现一个（登录页 / 其他页面没有）。
        val metricIndexes = VC_METRICS.map { name ->
            name to lines.indexOfFirst { line -> line.replace(" ", "").contains(name) }
        }
        val found = metricIndexes.filter { it.second >= 0 }
        if (found.isEmpty()) return null

        val items = LinkedHashMap<String, QuotaItem>()
        for ((name, idx) in found) {
            val window = lines.subList(idx, minOf(idx + 8, lines.size))
            val reset = window.drop(1).firstOrNull { VC_RESET_LINE.containsMatchIn(it) }
            val percentMatch = window.drop(1)
                .firstOrNull { VC_PERCENT_LINE.matches(it) }
                ?.let { VC_PERCENT_LINE.find(it) }
            val percentText = percentMatch?.groupValues?.get(1)
            val percent = percentText?.toDoubleOrNull()
            items[name] = QuotaItem(
                label = name,
                percent = percent?.coerceIn(0.0, 100.0),
                resetHint = reset,
                percentText = percentText,
            )
        }

        // 进度条兜底（仅已知指标名）
        if (items.values.all { it.percent == null && it.used == null }) {
            for (i in 0 until bars.length()) {
                val bar = bars.optJSONObject(i) ?: continue
                val rawLabel = bar.optString("label").replace(" ", "")
                val match = VC_METRICS.firstOrNull { rawLabel.contains(it) } ?: continue
                val now = bar.optDouble("now", Double.NaN)
                if (now.isNaN()) continue
                val max = bar.optDouble("max", Double.NaN)
                val item = if (!max.isNaN() && max > 0 && max != 1.0) {
                    QuotaItem(label = match, used = now, total = max)
                } else {
                    val p = if (now <= 1.0) now * 100 else now
                    QuotaItem(label = match, percent = p.coerceIn(0.0, 100.0))
                }
                items[item.label] = item
            }
        }

        // 套餐名行："Coding Plan Lite 套餐"
        val planName = lines.firstOrNull { VC_PLAN_LINE.containsMatchIn(it.replace("  ", " ")) }

        // 左侧套餐信息：label 与 value 同行或分行
        fun infoValue(key: String): String? {
            val idx = lines.indexOfFirst { it.startsWith(key) }
            if (idx < 0) return null
            val inline = lines[idx].substringAfter(key).trim(' ', '：', ':')
            if (inline.isNotEmpty()) return inline
            val next = lines.getOrNull(idx + 1)?.trim().orEmpty()
            return next.takeIf { it.isNotEmpty() }
        }
        val endAt = infoValue("结束时间")
        val remain = infoValue("剩余时间")

        val infoLines = VC_INFO_KEYS.mapNotNull { key ->
            lines.firstOrNull { it.startsWith(key) && it.length <= 60 }
        }

        return QuotaSnapshot(
            planName = planName,
            items = items.values.toList(),
            lines = infoLines,
            remainText = remain,
            endText = endAt,
            source = Source.DOM,
        )
    }

    // ------------------------------------------------------------ 网络层

    /** 从拦截到的 JSON 响应体中提取额度项。返回 null 表示该响应与额度无关。 */
    fun parseNetwork(platform: Platform, url: String, body: String): List<QuotaItem>? {
        val root = try {
            JSONTokener(body).nextValue()
        } catch (e: Exception) {
            return null
        }
        val items = LinkedHashMap<String, QuotaItem>()
        collectItems(root, items)
        return items.values.take(40).ifEmpty { null }
    }

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
    private val REMAIN_KEYS = setOf(
        "remaining", "remain", "left", "balance", "unused", "available",
    )
    private val PERCENT_KEYS = setOf(
        "percent", "percentage", "usagepercent", "usedpercent", "ratio", "progress",
    )
    private val LABEL_KEYS = setOf(
        "plan", "planname", "plantype", "tier", "subscription", "subscriptionplan",
        "name", "metric", "label", "title", "type", "dimension", "key", "code", "model",
    )
    private val NUM_PAIR_REGEX = Regex(
        """(-?[\d,]+(?:\.\d+)?)\s*(?:/|／)\s*(-?[\d,]+(?:\.\d+)?)"""
    )

    private fun collectItems(node: Any?, out: MutableMap<String, QuotaItem>) {
        when (node) {
            is JSONObject -> {
                val label = labelOf(node)
                // 未知指标名直接丢弃，绝不生成名为“额度”的野数据。
                if (label != null) {
                    val item = itemFromObject(node, label)
                    if (item != null && out.size < 40) {
                        out.putIfAbsent(item.label, item)
                    }
                }
                for (key in node.keys()) {
                    val v = node.opt(key)
                    if (v is JSONObject || v is JSONArray) {
                        collectItems(v, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectItems(node.opt(i), out)
                }
            }
        }
    }

    /** 仅当对象带有可识别的指标名时返回，否则返回 null（该对象被丢弃）。 */
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

        if (used != null && total != null && total > 0) {
            return QuotaItem(label = label, used = used, total = total)
        }
        if (remain != null && total != null && total > 0) {
            return QuotaItem(label = label, used = total - remain, total = total)
        }
        if (used != null && remain != null && used + remain > 0) {
            return QuotaItem(label = label, used = used, total = used + remain)
        }
        if (percent != null) {
            val p = if (percent <= 1.0) percent * 100 else percent
            return QuotaItem(label = label, percent = p.coerceIn(0.0, 100.0))
        }
        return null
    }

    private fun firstNumber(obj: JSONObject, keys: Set<String>): Double? {
        for (key in obj.keys()) {
            val norm = key.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (norm in keys) {
                val n = obj.opt(key)
                when (n) {
                    is Number -> return n.toDouble()
                    is String -> parseNum(n)?.let { return it }
                }
            }
        }
        for (key in obj.keys()) {
            val norm = key.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (norm in keys) {
                val m = NUM_PAIR_REGEX.find(obj.opt(key)?.toString() ?: "") ?: continue
                val idx = if (norm in USED_KEYS) 1 else 2
                parseNum(m.groupValues[idx])?.let { return it }
            }
        }
        return null
    }

    private fun parseNum(s: String): Double? {
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

    // ------------------------------------------------------------ 合并

    /**
     * DOM 精准数据优先（含 resetHint），网络数据仅补充**已知指标名**的项，
     * 非已知指标（如无名的 "额度"）一律不合并。
     * dom 为 null 时返回网络快照（调用方需自行确认已登录，见控制器超时逻辑）。
     */
    fun merge(networkItems: List<QuotaItem>, dom: QuotaSnapshot?): QuotaSnapshot? {
        val knownNetwork = networkItems.filter { isKnownMetricLabel(it.label) }
        if (dom == null) {
            if (knownNetwork.isEmpty()) return null
            return QuotaSnapshot(items = knownNetwork, source = Source.NETWORK)
        }
        if (knownNetwork.isEmpty()) return dom
        val merged = LinkedHashMap<String, QuotaItem>()
        dom.items.forEach { merged.putIfAbsent(it.label, it) }
        knownNetwork.forEach { net ->
            val duplicate = merged.keys.any { it.equals(net.label, ignoreCase = true) }
            if (!duplicate && merged.size < 60) merged.putIfAbsent(net.label, net)
        }
        return dom.copy(items = merged.values.toList(), source = Source.DOM)
    }

    /** 是否命中已知的指标名（用于控制器只在额度页接受网络数据）。 */
    fun isKnownMetricLabel(label: String): Boolean {
        val l = label.lowercase()
        return OC_METRICS.any { l.contains(it.lowercase()) } ||
            VC_METRICS.any { label.contains(it) }
    }

    /** 未登录时的营销页特征（OpenCode 控制台首页）。 */
    val MARKETING_REGEX = Regex(
        """(?i)(get started|explore enterprise|the cheapest inference|monthly subscription packages|quick answers)"""
    )

    /** 行文本是否像营销页内容。 */
    fun looksLikeMarketing(lines: List<String>): Boolean {
        val joined = lines.take(120).joinToString("\n")
        return MARKETING_REGEX.containsMatchIn(joined)
    }

    /** 仅用于测试/内部：数值对提取是否过于宽泛的保护（日期不视为额度）。 */
    internal fun looksLikeDate(line: String): Boolean {
        return Regex("""\b(19|20)\d{2}\s*[/\-.]\s*\d{1,2}\s*[/\-.]\s*\d{1,2}\b""").containsMatchIn(line) ||
            Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""").containsMatchIn(line) ||
            abs(line.count { it == '/' }) >= 2
    }
}
