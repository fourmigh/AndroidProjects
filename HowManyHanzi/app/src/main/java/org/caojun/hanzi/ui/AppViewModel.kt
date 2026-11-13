package org.caojun.hanzi.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.hanzi.data.HanziIndex
import org.caojun.hanzi.data.HistoryRecord
import org.caojun.hanzi.data.UserStore
import org.caojun.hanzi.engine.AnswerLog
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.engine.Summary
import org.caojun.hanzi.strokes.StrokeRepository

sealed interface Screen {
    data object Home : Screen
    data class Quiz(
        val mode: String,
        val scope: Int,
        val char: String? = null,
        val size: Int = 20,
    ) : Screen
    data object Result : Screen
    data object History : Screen
    data object Chars : Screen
    data class CharsList(val scope: Int) : Screen
    data class CharDetail(val char: String, val scope: Int, val index: Int) : Screen
    data object Intro : Screen
}

sealed interface ResultData {
    data class Single(val char: String, val level: Int, val log: List<AnswerLog>) : ResultData
    data class Test(val scope: Int, val scopeName: String, val summary: Summary) : ResultData
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var index: HanziIndex? by mutableStateOf(null)
        private set

    val store = UserStore(app)
    val strokes = StrokeRepository(app)

    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    private val backStack = ArrayDeque<Screen>()

    var result: ResultData? by mutableStateOf(null)
        private set

    var message: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            index = withContext(Dispatchers.IO) { HanziIndex.load(getApplication()) }
        }
    }

    fun go(target: Screen) {
        backStack.addLast(screen)
        screen = target
    }

    fun back() {
        screen = if (backStack.isEmpty()) Screen.Home else backStack.removeLast()
    }

    fun canBack(): Boolean = backStack.isNotEmpty()

    fun home() {
        backStack.clear()
        screen = Screen.Home
    }

    fun showResult(data: ResultData) {
        result = data
        backStack.clear()
        backStack.addLast(Screen.Home)
        screen = Screen.Result
    }

    fun toast(text: String) {
        message = text
    }

    fun clearToast() {
        message = null
    }

    fun saveSingleResult(char: String, level: Int, log: List<AnswerLog>) {
        val name = Levels.NAMES.getOrElse(level) { "" }
        store.addCharRecord(char, level, name, UserStore.nowText())
        showResult(ResultData.Single(char, level, log))
    }

    fun saveTestResult(scope: Int, summary: Summary) {
        val scopeName = index?.listScopes()?.getOrNull(scope)?.name ?: ""
        store.addHistory(
            HistoryRecord(
                scopeName = scopeName,
                avgLevelText = summary.avgLevelText,
                avgLevel = summary.avgLevel,
                mastery = summary.mastery,
                estimated = summary.estimated,
                count = summary.count,
                createdAt = System.currentTimeMillis(),
                dateText = UserStore.nowText(),
            )
        )
        showResult(ResultData.Test(scope, scopeName, summary))
    }
}
