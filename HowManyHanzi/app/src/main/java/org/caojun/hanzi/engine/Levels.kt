package org.caojun.hanzi.engine

/** 等级与科举功名常量，对应小程序 locales/zh-CN.js 的 levels 段。 */
object Levels {
    val NAMES = listOf("未识", "识形", "读音", "明义", "能写", "善用", "通源")
    val MARKS = listOf("庚", "己", "戊", "丁", "丙", "乙", "甲")

    val RESULT_DESC = listOf(
        "尚未认识",
        "能辨认字形，但读音、意义掌握有限",
        "知道读音；多音字能选出全部读音",
        "明白主要含义；多音字按读音对应不同意思",
        "掌握笔顺，能够书写",
        "能组词、会运用",
        "了解部首与构字来源",
    )

    val INTRO_DESC = listOf(
        "还不能稳定辨认这个字。",
        "能从形近字中认出目标字，对字形有印象。",
        "知道它的读音；多音字需选出全部常见读音。",
        "明白主要含义；多音字按读音对应不同意思。",
        "掌握笔顺，能够正确书写。",
        "能辨认包含它的词语，会组词运用。",
        "了解它的部首与构字来源。",
    )

    data class Rank(val min: Int, val name: String, val desc: String)

    /** 从高到低。 */
    val RANKS = listOf(
        Rank(85, "状元", "登峰造极"),
        Rank(70, "进士", "融会贯通"),
        Rank(55, "贡士", "登堂入室"),
        Rank(40, "举人", "渐入佳境"),
        Rank(25, "秀才", "粗通文义"),
        Rank(10, "童生", "略有所得"),
        Rank(0, "蒙童", "初识门径"),
    )

    const val MIN_CHARS = 18
    const val RANK_ZERO = "白丁"

    fun markOf(level: Int): String = MARKS.getOrElse(level) { "" }

    fun nameOf(level: Int): String = NAMES.getOrElse(level) { "" }

    fun labelOf(level: Int, name: String): String {
        val mark = markOf(level)
        return if (mark.isNotEmpty()) "$mark · $name" else name
    }

    fun rankOf(percent: Int): String {
        for (r in RANKS) if (percent >= r.min) return r.name
        return RANKS.last().name
    }

    fun avgText(avg: Double): String {
        val lv = avg.coerceAtMost(6.0).toInt()
        return "${markOf(lv)}等 ${String.format(java.util.Locale.ROOT, "%.1f", avg)}"
    }
}
