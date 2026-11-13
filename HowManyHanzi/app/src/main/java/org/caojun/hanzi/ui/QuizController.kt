package org.caojun.hanzi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.caojun.hanzi.engine.CharTest
import org.caojun.hanzi.engine.Question
import org.caojun.hanzi.engine.QuizEngine
import org.caojun.hanzi.engine.QuizSession
import kotlin.random.Random

/** 一次测字的 UI 状态机，逻辑对应小程序 pages/quiz/quiz.js + lib/engine.js。 */
class QuizController(
    private val vm: AppViewModel,
    private val route: Screen.Quiz,
) {
    private val index = vm.index!!
    private val pool = index.getCharsByScope(route.scope)
    private val engine = QuizEngine(index, Random(System.currentTimeMillis()))
    private val single = route.mode == "single"

    var session: QuizSession? = null
        private set
    var test: CharTest? = null
        private set

    var question: Question? by mutableStateOf(null)
        private set
    var selected: List<String> by mutableStateOf(emptyList())
        private set
    var answered: Boolean by mutableStateOf(false)
        private set
    var correct: Boolean by mutableStateOf(false)
        private set
    var progressText: String by mutableStateOf("")
        private set
    var charText: String by mutableStateOf("")
        private set
    var level: Int by mutableStateOf(0)
        private set
    var nextText: String by mutableStateOf("继续")
        private set
    var blocked: Boolean by mutableStateOf(false)
        private set

    init {
        if (single) {
            val ch = route.char.orEmpty()
            if (index.getTierOf(ch) < 0) {
                blocked = true
                progressText = "单字测级"
            } else {
                test = engine.createCharTest(ch)
                progressText = "单字测级"
                loadQuestion()
            }
        } else {
            val s = engine.createSession(route.scope, route.size)
            session = s
            if (s.chars.isEmpty()) {
                blocked = true
            } else {
                startCurrentChar()
            }
        }
    }

    private fun startCurrentChar() {
        val s = session ?: return
        val ch = engine.currentChar(s)
        if (ch == null) {
            finishSession()
            return
        }
        test = engine.createCharTest(ch)
        charText = ch
        level = 0
        progressText = "第 ${s.index + 1} / ${s.chars.size} 字"
        loadQuestion()
    }

    private fun loadQuestion() {
        val t = test ?: return
        val q = engine.questionFor(t, pool)
        if (q == null) {
            finishChar()
            return
        }
        question = q
        charText = q.char
        level = t.level
        selected = emptyList()
        answered = false
        correct = false
        nextText = "继续"
    }

    fun toggleOption(value: String) {
        if (answered) return
        val q = question ?: return
        if (q.type == "multi") {
            selected = if (selected.contains(value)) selected - value else selected + value
        } else {
            selected = listOf(value)
            submit()
        }
    }

    fun submit() {
        if (answered) return
        val q = question ?: return
        val t = test ?: return
        if (selected.isEmpty()) {
            vm.toast("请先选择")
            return
        }
        correct = engine.isCorrect(q, selected)
        engine.applyAnswer(t, q, correct)
        answered = true
        level = t.level

        val lastChar = !single && session != null &&
            session!!.index + 1 >= session!!.chars.size
        nextText = when {
            t.done -> if (single) "查看结果" else if (lastChar) "查看结果" else "下一个字"
            else -> "继续"
        }
    }

    fun next() {
        if (!answered) return
        val t = test ?: return
        if (t.done) {
            finishChar()
            return
        }
        loadQuestion()
    }

    fun finishChar() {
        val t = test ?: return
        if (single) {
            vm.saveSingleResult(t.char, t.level, t.log)
            return
        }
        val s = session ?: return
        engine.submitCharResult(s, t)
        if (s.index >= s.chars.size) {
            finishSession()
        } else {
            startCurrentChar()
        }
    }

    private fun finishSession() {
        val s = session ?: return
        val summary = engine.summarize(s)
        vm.saveTestResult(route.scope, summary)
    }

    fun feedbackTitle(): String = if (correct) "回答正确" else "回答错误"

    fun feedbackSub(): String {
        val q = question ?: return ""
        val t = test ?: return ""
        return if (correct) {
            if (t.queue.isNotEmpty()) {
                "回答正确，继续"
            } else {
                "达到 ${org.caojun.hanzi.engine.Levels.markOf(q.level)} ${q.levelName}"
            }
        } else {
            "停在 ${org.caojun.hanzi.engine.Levels.markOf(t.level)} ${org.caojun.hanzi.engine.Levels.nameOf(t.level)}"
        }
    }

    fun levelMark(level: Int): String = org.caojun.hanzi.engine.Levels.markOf(level)
    fun levelName(level: Int): String = org.caojun.hanzi.engine.Levels.nameOf(level)
}
