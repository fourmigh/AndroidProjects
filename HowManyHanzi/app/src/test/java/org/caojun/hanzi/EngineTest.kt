package org.caojun.hanzi

import org.caojun.hanzi.data.HanziIndex
import org.caojun.hanzi.engine.CharTest
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.engine.Progress
import org.caojun.hanzi.engine.QuizEngine
import org.caojun.hanzi.engine.QuizSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class EngineTest {
    private val index: HanziIndex by lazy {
        val dir = listOf(
            File("src/main/assets/data"),
            File("app/src/main/assets/data"),
        ).first { it.exists() }
        HanziIndex.parse(
            tiersText = File(dir, "tiers.tsv").readText(),
            charsText = File(dir, "chars.tsv").readText(),
            polyText = File(dir, "poly.txt").readText(),
        )
    }

    @Test
    fun `index loads 8105 chars and five tiers`() {
        assertEquals(8105, index.allChars.size)
        assertEquals(8105, index.chars.size)
        assertEquals(listOf(300, 2201, 999, 2000, 2605), index.tiers.map { it.list.size })
        assertEquals(listOf("启蒙", "小学", "初中", "高中", "全部"), index.tiers.map { it.name })
        assertEquals("数名，最小的正整数", index.getExplain("一"))
        assertEquals("行", index.getRadical("行"))
        assertEquals(6, index.getStrokeCount("行"))
        assertEquals(listOf("háng", "xíng"), index.getReadings("行"))
        assertEquals(2, index.getSenses("行").size)
        assertTrue(index.isPolyphone("行"))
        assertEquals(0, index.getTierOf("一"))
        assertEquals(4, index.getTierOf("㽏"))
    }

    @Test
    fun `scope returns cumulative all in reverse difficulty`() {
        val all = index.getCharsByScope(4)
        assertEquals(8105, all.size)
        assertEquals(index.tiers[4].list.first(), all.first())
        assertEquals(index.tiers[0].list.last(), all.last())
        assertEquals(300, index.getCharsByScope(0).size)
        assertEquals(2201, index.getCharsByScope(1).size)
    }

    @Test
    fun `every question option set contains the answer`() {
        val engine = QuizEngine(index, Random(42))
        val pool = index.getCharsByScope(4)
        for (ch in listOf("一", "行", "的", "鑫", "人", "大", "中")) {
            for (level in 1..6) {
                val group = org.caojun.hanzi.engine.QuestionBuilder(index, Random(7))
                    .buildQuestionGroup(ch, level, pool) ?: continue
                for (q in group) {
                    assertTrue("$ch L$level 选项为空", q.options.isNotEmpty())
                    assertTrue("$ch L$level 选项数量", q.options.size >= 2)
                    assertTrue("$ch L$level 重复选项", q.options.distinct().size == q.options.size)
                    for (a in q.answer) assertTrue("$ch L$level 缺少答案 $a", q.options.contains(a))
                    assertNotNull(engine)
                }
            }
        }
    }

    @Test
    fun `polyphone level 2 is multi select`() {
        val builder = org.caojun.hanzi.engine.QuestionBuilder(index, Random(1))
        val q = builder.buildQuestionGroup("行", 2, index.getCharsByScope(4))!!.first()
        assertEquals("multi", q.type)
        assertEquals(listOf("háng", "xíng"), q.answer.sorted())
    }

    @Test
    fun `correct answers raise level and wrong answers stop`() {
        val engine = QuizEngine(index, Random(3))
        val test = engine.createCharTest("一")
        var guard = 0
        while (!test.done && guard++ < 20) {
            val q = engine.questionFor(test, index.getCharsByScope(4)) ?: break
            engine.applyAnswer(test, q, true)
        }
        assertEquals(6, test.level)
        assertTrue(test.done)

        val wrong = engine.createCharTest("行")
        val q1 = engine.questionFor(wrong, index.getCharsByScope(4))!!
        engine.applyAnswer(wrong, q1, false)
        assertTrue(wrong.done)
        assertEquals(0, wrong.level)
    }

    @Test
    fun `summary matches original formula`() {
        val engine = QuizEngine(index, Random(5))
        val session = QuizSession(4, 2, listOf("一", "二"))
        val a = engine.createCharTest("一").apply { level = 3 }
        val b = engine.createCharTest("二").apply { level = 6 }
        engine.submitCharResult(session, a)
        engine.submitCharResult(session, b)
        val summary = engine.summarize(session)
        assertEquals(2, summary.count)
        assertEquals(4.5, summary.avgLevel, 0.001)
        assertEquals(75, summary.mastery)
        assertEquals(8105, summary.scopeTotal)
        assertEquals(6079, summary.estimated)
        assertEquals("丙等 4.5", summary.avgLevelText)
    }

    @Test
    fun `progress uses depth times square root coverage`() {
        val levels = mapOf("一" to 6, "二" to 3, "三" to 0, "四" to 6)
        val p = Progress.calcProgress(levels, 8105)
        assertEquals(4, p.count)
        // sum=15, depth=15/24=0.625, confidence=sqrt(4/8105)
        val expected = Math.round(15.0 / 24.0 * Math.sqrt(4.0 / 8105) * 100).toInt()
        assertEquals(expected, p.mastery)
        assertEquals("白丁", p.rank)
        assertEquals(0.05, p.coverage, 0.001)
    }

    @Test
    fun `sampling respects tier weights when scope is all`() {
        val engine = QuizEngine(index, Random(11))
        val sample = engine.sampleChars(4, 200)
        assertEquals(200, sample.size)
        assertEquals(200, sample.distinct().size)
        val tierCounts = IntArray(5)
        sample.forEach { ch ->
            val t = index.getTierOf(ch)
            if (t in 0..4) tierCounts[t]++
        }
        assertTrue(tierCounts[4] >= 60)
        assertTrue(tierCounts[0] >= 5)
    }

    @Test
    fun `level helpers`() {
        assertEquals("甲", Levels.markOf(6))
        assertEquals("庚", Levels.markOf(0))
        assertEquals("", Levels.markOf(9))
        assertEquals("状元", Levels.rankOf(90))
        assertEquals("蒙童", Levels.rankOf(0))
        assertEquals("甲等 6.0", Levels.avgText(6.0))
        assertEquals("丙等 4.5", Levels.avgText(4.5))
    }
}
