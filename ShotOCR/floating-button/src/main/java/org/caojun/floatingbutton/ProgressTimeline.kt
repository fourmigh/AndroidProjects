package org.caojun.floatingbutton

/** 记录识别各步骤的开始/结束时间，供悬浮进度列表展示耗时。 */
class ProgressTimeline {

    data class Row(
        val index: Int,
        val label: String,
        val durationMs: Long?,
        val isCurrent: Boolean
    )

    private class Step(
        val index: Int,
        val label: String,
        var startMs: Long,
        var endMs: Long? = null
    )

    private val steps = mutableListOf<Step>()
    private var currentIndex = 0
    private var started = false
    private var finished = false

    val totalSteps: Int get() = steps.size
    val current: Int get() = currentIndex
    val isStarted: Boolean get() = started
    val isFinished: Boolean get() = finished

    fun start(labels: List<String>, currentIndex: Int, now: Long) {
        steps.clear()
        labels.forEachIndexed { index, label ->
            val stepIndex = index + 1
            steps.add(
                Step(
                    index = stepIndex,
                    label = label,
                    startMs = if (stepIndex == currentIndex) now else UNSET
                )
            )
        }
        this.currentIndex = currentIndex
        started = true
        finished = false
    }

    fun update(index: Int, now: Long) {
        if (!started || finished || index == currentIndex) return

        steps.firstOrNull { it.index == currentIndex }?.let { step ->
            if (step.startMs != UNSET) step.endMs = now
        }
        steps.firstOrNull { it.index == index }?.let { step ->
            if (step.startMs == UNSET) step.startMs = now
        }
        currentIndex = index
    }

    fun finish(now: Long) {
        if (!started || finished) return
        steps.firstOrNull { it.index == currentIndex }?.let { step ->
            if (step.startMs != UNSET) step.endMs = now
        }
        finished = true
    }

    fun rows(now: Long): List<Row> = steps.map { step ->
        val duration = when {
            step.startMs == UNSET -> null
            step.endMs != null -> (step.endMs!! - step.startMs).coerceAtLeast(0)
            else -> (now - step.startMs).coerceAtLeast(0)
        }
        Row(
            index = step.index,
            label = step.label,
            durationMs = duration,
            isCurrent = !finished && step.index == currentIndex
        )
    }

    fun totalDurationMs(now: Long): Long? {
        val startedSteps = steps.filter { it.startMs != UNSET }
        if (startedSteps.isEmpty()) return null
        val start = startedSteps.minOf { it.startMs }
        val end = if (finished) {
            steps.mapNotNull { it.endMs }.maxOrNull() ?: now
        } else {
            now
        }
        return (end - start).coerceAtLeast(0)
    }

    fun reset() {
        steps.clear()
        currentIndex = 0
        started = false
        finished = false
    }

    companion object {
        private const val UNSET = -1L
    }
}
