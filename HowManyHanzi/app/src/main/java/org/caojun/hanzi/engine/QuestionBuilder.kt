package org.caojun.hanzi.engine

import org.caojun.hanzi.data.HanziIndex
import kotlin.random.Random

data class Question(
    val char: String,
    val level: Int,
    val levelName: String,
    val type: String,
    val options: List<String>,
    val answer: List<String>,
    val prompt: String,
)

/** 对应小程序 lib/quiz.js。 */
class QuestionBuilder(
    private val index: HanziIndex,
    private val rng: Random,
) {
    private val distractors = Distractors(index, rng)

    fun buildQuestion(ch: String, level: Int, pool: List<String>): Question? = when (level) {
        1 -> buildLevel1(ch, pool)
        2 -> buildLevel2(ch, pool)
        3 -> buildLevel3(ch, pool)
        4 -> buildLevel4(ch)
        5 -> buildLevel5(ch, pool)
        6 -> buildLevel6(ch, pool)
        else -> null
    }

    fun buildQuestionGroup(ch: String, level: Int, pool: List<String>): List<Question>? {
        if (level != 3) {
            val q = buildQuestion(ch, level, pool) ?: return null
            return listOf(q)
        }
        val senses = index.getSenses(ch)
        if (senses.size >= 2) {
            val group = ArrayList<Question>(senses.size)
            for (s in senses.shuffled(rng)) {
                buildSenseQuestion(ch, s, senses, pool)?.let { group.add(it) }
            }
            if (group.size >= 2) return group
        }
        val q = buildLevel3(ch, pool) ?: return null
        return listOf(q)
    }

    private fun buildSenseQuestion(
        ch: String,
        sense: org.caojun.hanzi.data.Sense,
        senses: List<org.caojun.hanzi.data.Sense>,
        pool: List<String>,
    ): Question? {
        val others = ArrayList<String>()
        val seen = HashSet<String>()
        for (s in senses) {
            if (s.pinyin == sense.pinyin) continue
            if (s.meaning == sense.meaning || seen.contains(s.meaning)) continue
            seen.add(s.meaning)
            others.add(s.meaning)
        }
        var picks = others.shuffled(rng).take(3).toMutableList()
        if (picks.size < 3) {
            val extra = distractors
                .pickExplanations(sense.meaning, 3 - picks.size, pool)
                .filter { it != sense.meaning && !picks.contains(it) }
            picks.addAll(extra)
        }
        if (picks.size < 3) return null
        return Question(
            char = ch,
            level = 3,
            levelName = Levels.NAMES[3],
            type = "single",
            options = buildOptions(sense.meaning, picks),
            answer = listOf(sense.meaning),
            prompt = "它读「${sense.pinyin}」时的主要意思是？",
        )
    }

    private fun buildOptions(correct: String, picks: List<String>): List<String> {
        return (listOf(correct) + picks).shuffled(rng)
    }

    private fun base(ch: String, level: Int, prompt: String): Question = Question(
        char = ch,
        level = level,
        levelName = Levels.NAMES[level],
        type = "single",
        options = emptyList(),
        answer = emptyList(),
        prompt = prompt,
    )

    private fun buildLevel1(ch: String, pool: List<String>): Question? {
        val picks = distractors.pickFormSimilar(ch, 3, pool)
        if (picks.size < 3) return null
        return base(ch, 1, "下面哪个字与它完全相同？").copy(
            options = buildOptions(ch, picks),
            answer = listOf(ch),
        )
    }

    private fun buildLevel2(ch: String, pool: List<String>): Question? {
        val readings = index.getReadings(ch).filter { it.isNotEmpty() }
        if (readings.size >= 2) {
            val seen = readings.toHashSet()
            val distractorCount = minOf(3, maxOf(2, 5 - readings.size))
            val picks = distractors.pickPinyins(readings[0], distractorCount, pool)
                .filter { !seen.contains(it) }
            if (picks.isEmpty()) return null
            return base(ch, 2, "它有哪些读音？（多选）").copy(
                type = "multi",
                options = (readings + picks).shuffled(rng),
                answer = readings,
            )
        }
        val correct = index.getPrimaryReading(ch)
        if (correct.isEmpty()) return null
        val picks = distractors.pickPinyins(correct, 3, pool)
        if (picks.size < 3) return null
        return base(ch, 2, "它的正确读音是？").copy(
            options = buildOptions(correct, picks),
            answer = listOf(correct),
        )
    }

    private fun buildLevel3(ch: String, pool: List<String>): Question? {
        val correct = index.getExplain(ch)
        if (correct.isEmpty()) return null
        val picks = distractors.pickExplanations(correct, 3, pool)
        if (picks.size < 3) return null
        return base(ch, 3, "它的主要意思是？").copy(
            options = buildOptions(correct, picks),
            answer = listOf(correct),
        )
    }

    private fun buildLevel4(ch: String): Question? {
        val names = index.getStrokeNames(ch).filter { it.isNotEmpty() }
        if (names.size < 2) return null
        val idx = rng.nextInt(names.size)
        val correct = names[idx]
        val picks = distractors.pickStrokeNames(correct, 3)
        if (picks.size < 3) return null
        return base(ch, 4, "它的第 ${idx + 1} 笔是？").copy(
            options = buildOptions(correct, picks),
            answer = listOf(correct),
        )
    }

    private fun buildLevel5(ch: String, pool: List<String>): Question? {
        val words = index.getWords(ch).filter {
            val len = it.codePointCount(0, it.length)
            len in 2..4 && it.contains(ch)
        }
        if (words.isEmpty()) return null
        val correct = words[rng.nextInt(words.size)]
        val picks = distractors.pickWords(correct, 3, pool)
        if (picks.size < 3) return null
        return base(ch, 5, "下面哪个词语包含这个字？").copy(
            options = buildOptions(correct, picks),
            answer = listOf(correct),
        )
    }

    private fun buildLevel6(ch: String, pool: List<String>): Question? {
        val correct = index.getRadical(ch)
        if (correct.isEmpty()) return null
        val picks = distractors.pickRadicals(correct, 3, pool)
        if (picks.size < 3) return null
        return base(ch, 6, "它的部首是？").copy(
            options = buildOptions(correct, picks),
            answer = listOf(correct),
        )
    }

    fun isCorrect(question: Question, selected: List<String>): Boolean {
        if (selected.isEmpty()) return false
        return question.answer.sorted() == selected.sorted()
    }
}
