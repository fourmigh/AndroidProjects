package org.caojun.hanzi.data

import android.content.Context

/**
 * 8105 字离线索引。
 *
 * 数据来自小程序 char.data.js 的预转换结果：
 * assets/data/tiers.tsv、chars.tsv、poly.txt。
 * 全部在首次进入时一次性读入内存（约 1 MB 文本）。
 */
class HanziIndex private constructor(
    val tiers: List<Tier>,
    val allChars: List<String>,
    val chars: Map<String, HanziChar>,
    val polySet: Set<String>,
) {
    private val tierOfMap = HashMap<String, Int>(allChars.size * 2)

    init {
        tiers.forEachIndexed { tierIndex, tier ->
            for (ch in tier.list) tierOfMap[ch] = tierIndex
        }
    }

    fun listScopes(): List<ScopeInfo> {
        var count = 0
        return tiers.mapIndexed { index, tier ->
            count += tier.list.size
            ScopeInfo(index, tier.name, count)
        }
    }

    fun getTierChars(tier: Int): List<String> {
        if (tier < 0 || tier >= tiers.size) return emptyList()
        return tiers[tier].list
    }

    /** 与原版一致：scope >= 4 表示全部字库，按难度倒序。 */
    fun getCharsByScope(scope: Int): List<String> {
        if (scope >= tiers.size - 1) {
            val out = ArrayList<String>(allChars.size)
            for (i in tiers.indices.reversed()) out.addAll(tiers[i].list)
            return out
        }
        return getTierChars(scope.coerceAtLeast(0))
    }

    fun getExplain(ch: String): String = chars[ch]?.explain ?: ""
    fun getRadical(ch: String): String = chars[ch]?.radical ?: ""
    fun getStrokeCount(ch: String): Int = chars[ch]?.strokeCount ?: 0
    fun getReadings(ch: String): List<String> = chars[ch]?.readings ?: emptyList()
    fun getPrimaryReading(ch: String): String = readingsFirst(ch)
    fun getSenses(ch: String): List<Sense> = chars[ch]?.senses ?: emptyList()
    fun getStrokeNames(ch: String): List<String> = chars[ch]?.strokeNames ?: emptyList()
    fun getWords(ch: String): List<String> = chars[ch]?.words ?: emptyList()
    fun isPolyphone(ch: String): Boolean = polySet.contains(ch)
    fun getPolyChars(pool: List<String>): List<String> = pool.filter { polySet.contains(it) }
    fun getTierOf(ch: String): Int = tierOfMap[ch] ?: -1

    private fun readingsFirst(ch: String): String {
        val list = chars[ch]?.readings ?: return ""
        return list.firstOrNull() ?: ""
    }

    companion object {
        fun load(context: Context): HanziIndex {
            val assets = context.assets
            val tiersText = assets.open("data/tiers.tsv").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val charsText = assets.open("data/chars.tsv").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val polyText = assets.open("data/poly.txt").bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return parse(tiersText, charsText, polyText)
        }

        /** 供构建期数据校验和 JVM 单测复用。 */
        fun parse(tiersText: String, charsText: String, polyText: String): HanziIndex {
            val tiers = tiersText.lineSequence()
                .filter { it.isNotBlank() }
                .map { line ->
                    val sep = line.indexOf('\t')
                    if (sep <= 0) Tier("", "") else Tier(line.substring(0, sep), line.substring(sep + 1))
                }
                .toList()

            val chars = HashMap<String, HanziChar>(9000)
            charsText.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val f = line.split('\t')
                if (f.size < 9) return@forEach
                val ch = f[0]
                if (ch.isEmpty()) return@forEach
                chars[ch] = HanziChar(
                    char = ch,
                    explain = f[1],
                    radical = f[2],
                    strokeCount = f[3].toIntOrNull() ?: 0,
                    readings = splitList(f[4]),
                    senses = parseSenses(f[5]),
                    strokeNames = splitList(f[6]),
                    words = splitList(f[7]),
                    tier = f[8].toIntOrNull() ?: 0,
                )
            }

            val poly = polyText.trim()
            val all = ArrayList<String>(chars.size)
            tiers.forEach { all.addAll(it.list) }
            return HanziIndex(tiers, all, chars, poly.splitCodePoints().toHashSet())
        }

        private fun splitList(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            return text.split(',').filter { it.isNotEmpty() }
        }

        private fun parseSenses(text: String): List<Sense> {
            if (text.isEmpty()) return emptyList()
            val out = ArrayList<Sense>(4)
            for (seg in text.split(';')) {
                val c = seg.indexOf(':')
                if (c <= 0) continue
                val p = seg.substring(0, c)
                val m = seg.substring(c + 1)
                if (p.isNotEmpty() && m.isNotEmpty()) out.add(Sense(p, m))
            }
            return out
        }
    }
}
