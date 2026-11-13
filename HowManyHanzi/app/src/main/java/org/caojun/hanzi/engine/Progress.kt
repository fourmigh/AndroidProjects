package org.caojun.hanzi.engine

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ProgressInfo(
    val count: Int,
    val total: Int,
    val coverage: Double,
    val depth: Double,
    val mastery: Int,
    val rank: String,
)

/** 对应小程序 lib/progress.js。 */
object Progress {
    fun calcProgress(charLevels: Map<String, Int>, totalChars: Int): ProgressInfo {
        val total = if (totalChars > 0) totalChars else 8105
        val count = charLevels.size
        var sum = 0L
        charLevels.values.forEach { sum += it }
        val coverage = if (count > 0) (count * 10000.0 / total).roundToInt() / 100.0 else 0.0
        val depth = if (count > 0) sum.toDouble() / (count * 6.0) else 0.0
        val confidence = if (count > 0) sqrt(count.toDouble() / total) else 0.0
        val mastery = (depth * confidence * 100).roundToInt()
        val rank = if (count < Levels.MIN_CHARS) Levels.RANK_ZERO else Levels.rankOf(mastery)
        return ProgressInfo(count, total, coverage, depth, mastery, rank)
    }
}
