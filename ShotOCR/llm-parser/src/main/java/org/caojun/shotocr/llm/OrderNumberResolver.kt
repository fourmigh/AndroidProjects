package org.caojun.shotocr.llm

import kotlin.math.abs

/**
 * 订单号确定性解析：OCR 常把"标签"与"数字"拆成不同文本块且顺序错乱，
 * 这里按"标签就近关联"从全量 OCR 文本里取支付订单号（排除商户单号）。
 */
object OrderNumberResolver {

    /** 支付订单号标签（按优先级）。 */
    private val paymentLabels = listOf(
        "交易单号", "订单号", "订单编号", "交易流水号", "交易号", "支付宝交易号", "流水号"
    )

    /** 需要排除的商户侧单号标签。 */
    private val excludedLabels = listOf(
        "商户单号", "商户订单号", "商家订单号", "商户号"
    )

    private val tokenRegex = Regex("""[A-Za-z0-9]{16,}""")

    fun resolve(ocrText: String): String? {
        val lines = ocrText.lines().map { it.trim() }
        if (lines.isEmpty()) return null

        val labelHits = mutableListOf<LabelHit>()
        lines.forEachIndexed { index, line ->
            val excluded = excludedLabels.any { line.contains(it) }
            if (excluded) {
                labelHits.add(LabelHit(index, payment = false, priority = Int.MAX_VALUE))
            } else {
                val priority = paymentLabels.indexOfFirst { line.contains(it) }
                if (priority >= 0) labelHits.add(LabelHit(index, payment = true, priority = priority))
            }
        }
        if (labelHits.isEmpty()) return null

        data class Candidate(val value: String, val priority: Int)

        val candidates = mutableListOf<Candidate>()
        lines.forEachIndexed { index, line ->
            val tokens = tokenRegex.findAll(line).map { it.value }.toList()
            if (tokens.isEmpty()) return@forEachIndexed

            // 找到最近的标签（距离相同优先支付标签）
            var nearest: LabelHit? = null
            var bestDistance = Int.MAX_VALUE
            for (hit in labelHits) {
                val distance = abs(hit.lineIndex - index)
                val better = distance < bestDistance ||
                    (distance == bestDistance && hit.payment && nearest?.payment == false)
                if (better) {
                    nearest = hit
                    bestDistance = distance
                }
            }
            val hit = nearest ?: return@forEachIndexed
            if (!hit.payment) return@forEachIndexed

            tokens.forEach { token ->
                candidates.add(Candidate(token, hit.priority))
            }
        }

        if (candidates.isEmpty()) return null
        return candidates
            .sortedWith(
                compareBy<Candidate> { it.priority }
                    .thenByDescending { it.value.all { ch -> ch.isDigit() } }
                    .thenByDescending { it.value.length }
            )
            .first()
            .value
    }

    private data class LabelHit(val lineIndex: Int, val payment: Boolean, val priority: Int)
}
