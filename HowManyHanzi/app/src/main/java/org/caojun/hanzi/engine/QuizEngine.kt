package org.caojun.hanzi.engine

import org.caojun.hanzi.data.HanziIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class AnswerLog(val level: Int, val correct: Boolean)

class CharTest(val char: String) {
    var level: Int = 0
    var next: Int = 1
    val log = ArrayList<AnswerLog>()
    var done: Boolean = false
    val queue = ArrayDeque<Question>()
}

data class CharResult(val char: String, val level: Int, val log: List<AnswerLog>)

class QuizSession(
    val scope: Int,
    val sampleSize: Int,
    val chars: List<String>,
) {
    var index: Int = 0
    val results = ArrayList<CharResult>()
    val startedAt: Long = System.currentTimeMillis()
}

data class Summary(
    val count: Int,
    val avgLevel: Double,
    val avgLevelText: String,
    val mastery: Int,
    val distribution: List<Int>,
    val scopeTotal: Int,
    val estimated: Int,
    val results: List<CharResult>,
)

/** 对应小程序 lib/engine.js。 */
class QuizEngine(
    private val index: HanziIndex,
    private val rng: Random,
) {
    private val builder = QuestionBuilder(index, rng)

    fun createCharTest(ch: String): CharTest = CharTest(ch)

    fun questionFor(test: CharTest, pool: List<String>): Question? {
        if (test.queue.isNotEmpty()) return test.queue.removeFirst()
        var lv = test.next
        while (lv <= MAX_LEVEL) {
            val group = builder.buildQuestionGroup(test.char, lv, pool)
            if (!group.isNullOrEmpty()) {
                for (i in 1 until group.size) test.queue.addLast(group[i])
                return group[0]
            }
            lv++
        }
        return null
    }

    fun applyAnswer(test: CharTest, question: Question, correct: Boolean) {
        test.log.add(AnswerLog(question.level, correct))
        if (correct) {
            if (test.queue.isNotEmpty()) {
                // 组内还有子题，级别保持
            } else {
                test.level = question.level
                test.next = question.level + 1
                if (test.level >= MAX_LEVEL) test.done = true
            }
        } else {
            test.queue.clear()
            test.done = true
        }
    }

    fun isCorrect(question: Question, selected: List<String>): Boolean =
        builder.isCorrect(question, selected)

    fun sampleChars(scope: Int, sampleSize: Int): List<String> {
        if (scope >= 4) {
            val weights = doubleArrayOf(0.05, 0.1, 0.2, 0.3, 0.35)
            val picked = ArrayList<String>(sampleSize)
            for (t in index.tiers.indices) {
                var n = (weights[t] * sampleSize).roundToInt()
                if (t == index.tiers.size - 1) n = sampleSize - picked.size
                val list = index.tiers[t].list
                if (n > 0) picked.addAll(list.shuffled(rng).take(n))
            }
            if (picked.size < sampleSize) {
                picked.addAll(index.allChars.shuffled(rng).take(sampleSize - picked.size))
            }
            return picked.shuffled(rng).take(sampleSize)
        }
        return index.getCharsByScope(scope).shuffled(rng).take(sampleSize)
    }

    fun createSession(scope: Int, sampleSize: Int): QuizSession =
        QuizSession(scope, sampleSize, sampleChars(scope, sampleSize))

    fun currentChar(session: QuizSession): String? =
        session.chars.getOrNull(session.index)

    fun submitCharResult(session: QuizSession, test: CharTest) {
        session.results.add(CharResult(test.char, test.level, ArrayList(test.log)))
        session.index++
    }

    fun summarize(session: QuizSession): Summary {
        val results = session.results
        val distribution = IntArray(8)
        var sum = 0
        for (r in results) {
            val lv = r.level
            if (lv in distribution.indices) distribution[lv]++
            sum += lv
        }
        val count = if (results.isEmpty()) 1 else results.size
        val avg = sum.toDouble() / count
        val mastery = (sum.toDouble() / (count * MAX_LEVEL) * 100).roundToInt()
        val scopeTotal = index.getCharsByScope(session.scope).size
        val estimated = (mastery / 100.0 * scopeTotal).roundToInt()
        return Summary(
            count = results.size,
            avgLevel = avg,
            avgLevelText = Levels.avgText(avg),
            mastery = mastery,
            distribution = distribution.toList(),
            scopeTotal = scopeTotal,
            estimated = estimated,
            results = results.toList(),
        )
    }

    companion object {
        const val MAX_LEVEL = 6
    }
}
