package org.caojun.quotamonitor.data

import org.caojun.quotamonitor.model.DetectedMetric
import org.caojun.quotamonitor.model.MetricMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自动识别：从已渲染页面的文本行里找出“疑似额度指标”候选，
 * 并自动推导出可用的规则（正则由程序生成，用户无需编写）。
 *
 * 识别三类形态：
 * 1. 同行百分比：`29% used`、`5-hour window … 29% used`
 * 2. 指标下方单独一行的大号数字：`13.51`、`13.51 %`
 * 3. 已用 / 总量：`45,300 / 90,600`
 */
object QuotaAutoDetector {

    private val INLINE_PERCENT = Regex("""(\d[\d,]*(?:\.\d+)?)\s*%\s*(used)?$""", RegexOption.IGNORE_CASE)
    private val USED_TOTAL = Regex("""(\d[\d,]*(?:\.\d+)?)\s*(?:/|of|／)\s*(\d[\d,]*(?:\.\d+)?)""")
    private val PURE_NUMBER = Regex("""^\s*(\d+(?:\.\d+)?)\s*%?\s*$""")
    private val RESET_KEYWORDS = listOf("reset", "renew", "expires", "到期", "重置")
    private val NOISE_LABELS = setOf(
        "0%", "50%", "100%", "%", "used", "updated", "update", "total", "remaining",
    )

    fun detect(scanJson: String): List<DetectedMetric> {
        val scan = runCatching { JSONObject(scanJson) }.getOrNull() ?: return emptyList()
        val lines = mutableListOf<String>()
        scan.optJSONArray("lines")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { lines.add(it) }
            }
        }
        val bars = scan.optJSONArray("bars") ?: JSONArray()
        val out = LinkedHashMap<String, DetectedMetric>()

        // 1) 同行百分比
        for ((i, line) in lines.withIndex()) {
            if (line.length > 120) continue
            if (looksLikeDate(line)) continue
            val match = INLINE_PERCENT.find(line) ?: continue
            val raw = match.groupValues[1]
            val before = line.substring(0, match.range.first)
            val label = deriveLabel(before) ?: labelAbove(lines, i) ?: continue
            if (isNoiseLabel(label)) continue
            val hint = extractResetText(line) ?: resetNearby(lines, i)
            out.putIfAbsent(
                label,
                DetectedMetric(
                    label = label,
                    mode = MetricMode.PERCENT_INLINE,
                    valueRegex = if (match.groupValues[2].isNotBlank()) {
                        """(\d+(?:\.\d+)?)\s*%\s*used"""
                    } else {
                        """(\d+(?:\.\d+)?)\s*%"""
                    },
                    resetRegex = hint?.let { buildResetRegex(it) },
                    sampleText = "$raw%",
                    resetHint = hint,
                )
            )
        }

        // 2) 指标下方单独一行的大号数字
        for ((i, line) in lines.withIndex()) {
            if (!isLabelLine(line)) continue
            val label = cleanLabel(line) ?: continue
            // 值行必须在 label 之后 1~2 行内；若中间又出现指标名行，
            // 说明当前行是分组标题（如“套餐用量限额”），跳过。
            var valueLine = -1
            for (j in i + 1..minOf(i + 2, lines.lastIndex)) {
                val candidate = lines[j]
                if (isLabelLine(candidate)) break
                if (PURE_NUMBER.matches(candidate)) {
                    valueLine = j
                    break
                }
            }
            if (valueLine < 0) continue
            val m = PURE_NUMBER.find(lines[valueLine]) ?: continue
            val raw = m.groupValues[1]
            if (looksLikeDate(lines[valueLine])) continue
            val hint = resetNearby(lines, i, preferBackward = false)
            out.putIfAbsent(
                label,
                DetectedMetric(
                    label = label,
                    mode = MetricMode.PERCENT_NEXT_NUMBER,
                    valueRegex = null,
                    resetRegex = hint?.let { buildResetRegex(it) },
                    sampleText = "$raw%",
                    resetHint = hint,
                )
            )
        }

        // 3) 已用 / 总量
        for ((i, line) in lines.withIndex()) {
            if (looksLikeDate(line)) continue
            val match = USED_TOTAL.find(line) ?: continue
            val label = deriveLabel(line.substring(0, match.range.first)) ?: labelAbove(lines, i) ?: continue
            if (isNoiseLabel(label)) continue
            out.putIfAbsent(
                label,
                DetectedMetric(
                    label = label,
                    mode = MetricMode.USED_TOTAL,
                    valueRegex = """(\d[\d,]*(?:\.\d+)?)\s*(?:/|of|／)\s*(\d[\d,]*(?:\.\d+)?)""",
                    resetRegex = resetNearby(lines, i)?.let { buildResetRegex(it) },
                    sampleText = "${match.groupValues[1]} / ${match.groupValues[2]}",
                    resetHint = resetNearby(lines, i),
                )
            )
        }

        // 4) 进度条元素兜底（无文本层时）
        for (i in 0 until bars.length()) {
            val bar = bars.optJSONObject(i) ?: continue
            val label = cleanLabel(bar.optString("label")) ?: continue
            if (isNoiseLabel(label)) continue
            val now = bar.optDouble("now", Double.NaN)
            if (now.isNaN()) continue
            out.values.firstOrNull { it.label == label }?.let { continue }
            out.putIfAbsent(
                label,
                DetectedMetric(
                    label = label,
                    mode = MetricMode.PERCENT_INLINE,
                    valueRegex = """(\d+(?:\.\d+)?)""",
                    resetRegex = null,
                    sampleText = now.toString(),
                    resetHint = null,
                )
            )
        }

        return out.values.take(20)
    }

    // ---------------------------------------------------------------- 辅助

    /** 从百分比前的文字里提取指标名（去掉重置倒计时部分）。 */
    private fun deriveLabel(before: String): String? {
        val cut = resetKeywordIndex(before)
        val raw = if (cut >= 0) before.substring(0, cut) else before
        return cleanLabel(raw)
    }

    private fun cleanLabel(text: String): String? {
        val t = text.trim(' ', ':', '：', '-', '·', '•', '|', '$', '¥', '（', '(', '）', ')')
        if (t.isEmpty()) return null
        if (t.length > 40) return null
        if (t.all { it.isDigit() || it == '%' || it == '.' || it == ',' }) return null
        return t
    }

    private fun labelAbove(lines: List<String>, index: Int): String? {
        for (j in index - 1 downTo maxOf(0, index - 3)) {
            val line = lines[j]
            // 跳过重置倒计时行、日期行与纯数字行
            if (RESET_KEYWORDS.any { line.contains(it, ignoreCase = true) }) continue
            if (looksLikeDate(line)) continue
            if (line.trim { it.isDigit() || it.isWhitespace() || it == '%' || it == ',' }.isEmpty()) continue
            cleanLabel(line)?.takeIf { it.length in 2..40 }?.let { return it }
        }
        return null
    }

    /** 是否像“指标名”行：含字母或汉字、不是日期/纯数字/重置倒计时/噪声词。 */
    private fun isLabelLine(line: String): Boolean {
        val label = cleanLabel(line) ?: return false
        if (label.length !in 2..40) return false
        if (isNoiseLabel(label)) return false
        if (looksLikeDate(line)) return false
        // 重置倒计时行（如“5天8小时后重置”）不是指标名
        if (hasResetKeyword(line)) return false
        if (!label.any { it.isLetter() }) return false
        return true
    }

    private fun hasResetKeyword(line: String): Boolean =
        RESET_KEYWORDS.any { line.contains(it, ignoreCase = true) } ||
            Regex("""^\s*\d.*后重置\s*$""").matches(line)

    /** 重置倒计时通常位于指标名下方或百分比上方：按调用方偏好方向查找。
     *  注意：只接受包含数字的行（“When your Go usage resets”这类句子不是倒计时）。 */
    private fun resetNearby(lines: List<String>, index: Int, preferBackward: Boolean = true): String? {
        val backward = (index - 1 downTo maxOf(0, index - 3)).map { lines[it] }
        val forward = (index + 1..minOf(index + 5, lines.lastIndex)).map { lines[it] }
        val candidates = if (preferBackward) backward + forward else forward + backward
        return candidates.firstOrNull { line ->
            line.length <= 80 &&
                line.any { it.isDigit() } &&
                RESET_KEYWORDS.any { line.contains(it, ignoreCase = true) }
        }
    }

    private fun extractResetText(line: String): String? {
        val idx = resetKeywordIndex(line)
        if (idx <= 0) return null
        val text = line.substring(idx).trim()
        if (text.length > 80) return null
        if (text.none { it.isDigit() }) return null
        return text
    }

    private fun resetKeywordIndex(text: String): Int {
        val lower = text.lowercase()
        val keywords = listOf("resets in", "reset in", "resets", "renew", "expires", "到期", "重置")
        return keywords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
    }

    private fun isNoiseLabel(label: String): Boolean {
        val l = label.lowercase().trim()
        if (l in NOISE_LABELS) return true
        if (l.length <= 1) return true
        if (looksLikeDate(label)) return true
        return false
    }

    /** 由“重置倒计时”文本生成宽松正则：数字部分替换为 \d+，只转义正则元字符。 */
    fun buildResetRegex(text: String): String {
        val out = StringBuilder()
        var inDigits = false
        for (ch in text) {
            if (ch.isDigit()) {
                if (!inDigits) {
                    out.append("\\d+")
                    inDigits = true
                }
            } else {
                inDigits = false
                if (ch in REGEX_META_CHARS) out.append('\\')
                out.append(ch)
            }
        }
        return out.toString().takeIf { it.isNotBlank() } ?: ""
    }

    private const val REGEX_META_CHARS = "\\.^$|?*+()[]{}"

    private fun looksLikeDate(line: String): Boolean {
        return Regex("""\b(19|20)\d{2}\s*[/\-.]\s*\d{1,2}\s*[/\-.]\s*\d{1,2}\b""").containsMatchIn(line) ||
            Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""").containsMatchIn(line)
    }
}
