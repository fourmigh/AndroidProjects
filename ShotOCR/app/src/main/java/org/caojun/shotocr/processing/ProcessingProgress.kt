package org.caojun.shotocr.processing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunState { IDLE, RUNNING, SUCCESS, FAILED }

data class ProcessStatus(
    val step: ProcessStep = ProcessStep.IDLE,
    val state: RunState = RunState.IDLE,
    val detail: String? = null,
    val error: String? = null
)

/**
 * 截图处理进度的进程级状态，供通知栏（当前）与后续 UI（进度页）共同订阅。
 */
object ProcessingProgress {

    private val _state = MutableStateFlow(ProcessStatus())
    val state: StateFlow<ProcessStatus> = _state.asStateFlow()

    fun update(step: ProcessStep, state: RunState, detail: String? = null, error: String? = null) {
        _state.value = ProcessStatus(step = step, state = state, detail = detail, error = error)
    }

    fun reset() {
        _state.value = ProcessStatus()
    }
}
