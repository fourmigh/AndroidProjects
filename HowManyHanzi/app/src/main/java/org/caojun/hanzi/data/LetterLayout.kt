package org.caojun.hanzi.data

/** 信纸排版，移植自小程序 lib/letter.js。尺寸参数沿用 rpx（750 设计宽）。 */
object LetterLayout {
    const val DESIGN_WIDTH = 686f
    const val MIN_COLS = 2
    const val MAX_COLS = 14
    const val MAX_LEN = 300

    /** 字号 + 字距 → 每行字数（格子不超出纸宽）。 */
    fun colsOf(sizeRpx: Int, gapRpx: Int): Int {
        val cell = sizeRpx + gapRpx
        if (cell <= 0) return 6
        return (DESIGN_WIDTH / cell).toInt().coerceIn(MIN_COLS, MAX_COLS)
    }

    fun normalize(text: String?): String =
        (text ?: "").replace("\r\n", "\n").replace('\r', '\n')

    /** 返回行数组，每行是字符（按码点）数组；空行为空数组。 */
    fun parseLines(text: String?, cols: Int): List<List<String>> {
        val width = maxOf(1, cols)
        val out = ArrayList<List<String>>()
        for (paragraph in normalize(text).split("\n")) {
            val chars = splitCodePoints(paragraph)
            if (chars.isEmpty()) {
                out.add(emptyList())
                continue
            }
            var i = 0
            while (i < chars.size) {
                out.add(chars.subList(i, minOf(i + width, chars.size)))
                i += width
            }
        }
        return out
    }

    fun splitCodePoints(s: String): List<String> {
        val out = ArrayList<String>(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            out.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out
    }

    /** 空白字符（含半角/全角空格）占格不写。 */
    fun isBlank(ch: String): Boolean =
        ch.isNotEmpty() && ch.all { it.isWhitespace() || Character.isSpaceChar(it) }

    /** 统计需要书写的字数（不含空白）。 */
    fun countWritable(lines: List<List<String>>): Int =
        lines.sumOf { line -> line.count { !isBlank(it) } }

    /** 输入框限长，按码点截断，避免拆开代理对。 */
    fun takeCodePoints(s: String, max: Int): String {
        if (s.codePointCount(0, s.length) <= max) return s
        return s.substring(0, s.offsetByCodePoints(0, max))
    }
}
