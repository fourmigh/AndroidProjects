package org.caojun.hanzi.engine

import org.caojun.hanzi.data.HanziIndex
import kotlin.random.Random

/** 笔画名称全集，用于 L4 干扰项。 */
val STROKE_NAMES = listOf(
    "横", "竖", "撇", "捺", "点", "提", "横折", "横钩", "横撇", "横折钩",
    "横折提", "横折弯", "横折弯钩", "横撇弯钩", "横折折撇", "横折折折钩",
    "竖提", "竖折", "竖钩", "竖弯", "竖弯钩", "竖折折钩", "撇点", "撇折",
    "斜钩", "弯钩", "卧钩", "竖折撇",
)

/** 对应小程序 lib/distractors.js。 */
class Distractors(
    private val index: HanziIndex,
    private val rng: Random,
) {
    private fun randomPoolChar(pool: List<String>, exclude: Set<String>): String? {
        repeat(40) {
            if (pool.isEmpty()) return null
            val c = pool[rng.nextInt(pool.size)]
            if (c.isNotEmpty() && !exclude.contains(c)) return c
        }
        return null
    }

    fun pickPinyins(correct: String, count: Int, pool: List<String>): List<String> {
        val out = ArrayList<String>(count)
        val seen = HashSet<String>()
        seen.add(correct)
        val base = TONE_MARKS.replace(correct, "")
        var attempts = 0
        while (attempts < 60 && out.size < count) {
            attempts++
            val c = randomPoolChar(pool, setOf(correct)) ?: continue
            val py = index.getPrimaryReading(c)
            if (py.isEmpty() || seen.contains(py)) continue
            if (TONE_MARKS.replace(py, "") == base) continue
            seen.add(py)
            out.add(py)
        }
        return out
    }

    fun pickExplanations(correct: String, count: Int, pool: List<String>): List<String> {
        val out = ArrayList<String>(count)
        val seen = HashSet<String>()
        seen.add(correct)
        var attempts = 0
        while (attempts < 80 && out.size < count) {
            attempts++
            val c = randomPoolChar(pool, emptySet()) ?: continue
            val ex = index.getExplain(c)
            if (ex.isEmpty() || seen.contains(ex) || ex == correct) continue
            seen.add(ex)
            out.add(ex)
        }
        return out
    }

    fun pickWords(correctWord: String, count: Int, pool: List<String>): List<String> {
        val out = ArrayList<String>(count)
        val seen = HashSet<String>()
        seen.add(correctWord)
        val char = correctWord.take(1)
        var attempts = 0
        while (attempts < 80 && out.size < count) {
            attempts++
            val c = randomPoolChar(pool, setOf(char)) ?: continue
            val list = index.getWords(c)
            if (list.isEmpty()) continue
            val w = list[rng.nextInt(list.size)]
            if (w.isEmpty() || seen.contains(w) || w.contains(char)) continue
            if (w.codePointCount(0, w.length) > 4) continue
            seen.add(w)
            out.add(w)
        }
        return out
    }

    fun pickStrokeNames(correct: String, count: Int): List<String> {
        val pool = STROKE_NAMES.filter { it != correct }
        return pool.shuffled(rng).take(count)
    }

    fun pickRadicals(correct: String, count: Int, pool: List<String>): List<String> {
        val out = ArrayList<String>(count)
        val seen = HashSet<String>()
        seen.add(correct)
        var attempts = 0
        while (attempts < 80 && out.size < count) {
            attempts++
            val c = randomPoolChar(pool, emptySet()) ?: continue
            val r = index.getRadical(c)
            if (r.isEmpty() || seen.contains(r) || r == correct) continue
            seen.add(r)
            out.add(r)
        }
        return out
    }

    fun pickFormSimilar(ch: String, count: Int, pool: List<String>): List<String> {
        val targetRadical = index.getRadical(ch)
        val targetStroke = index.getStrokeCount(ch)
        val sameRadical = ArrayList<String>()
        val nearStroke = ArrayList<String>()
        for (c in pool) {
            if (c.isEmpty() || c == ch) continue
            val r = index.getRadical(c)
            if (targetRadical.isNotEmpty() && r == targetRadical) {
                sameRadical.add(c)
            } else if (targetStroke > 0) {
                val s = index.getStrokeCount(c)
                if (s > 0 && kotlin.math.abs(s - targetStroke) <= 1) nearStroke.add(c)
            }
        }
        var out = sameRadical.distinct().shuffled(rng).take(count)
        if (out.size < count) {
            val extra = nearStroke.distinct().filter { !out.contains(it) }
                .shuffled(rng).take(count - out.size)
            out = out + extra
        }
        return out.take(count)
    }

    companion object {
        val TONE_MARKS = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]")
    }
}
