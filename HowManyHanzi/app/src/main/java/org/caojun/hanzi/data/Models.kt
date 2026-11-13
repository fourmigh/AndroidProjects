package org.caojun.hanzi.data

/** 一个字的多音义项。 */
data class Sense(val pinyin: String, val meaning: String)

/** 字库中的一个字及预生成的题目素材。 */
class HanziChar(
    val char: String,
    val explain: String,
    val radical: String,
    val strokeCount: Int,
    val readings: List<String>,
    val senses: List<Sense>,
    val strokeNames: List<String>,
    val words: List<String>,
    val tier: Int,
)

/** 字库档位：启蒙 / 小学 / 初中 / 高中 / 全部。 */
data class Tier(val name: String, val chars: String) {
    val list: List<String> by lazy { chars.splitCodePoints() }
}

/** 按 Unicode 码点拆分，保证扩展 B 区汉字不被拆成两个代理字符。 */
fun String.splitCodePoints(): List<String> {
    val out = ArrayList<String>(length)
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        out.add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return out
}

/** 首页展示的累计档位信息。 */
data class ScopeInfo(val index: Int, val name: String, val count: Int)
