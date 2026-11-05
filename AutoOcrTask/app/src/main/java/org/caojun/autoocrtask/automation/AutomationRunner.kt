package org.caojun.autoocrtask.automation

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.delay
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.StepType
import org.caojun.autoocrtask.util.ForegroundApp
import org.caojun.shizuku.automation.ShizukuUiAutomationSession

enum class FailureReason { TIMEOUT, TAP_FAILED, PACKAGE_MISSING, STOPPED }

data class AutomationResult(
    val success: Boolean,
    val stopped: Boolean,
    val failedStepIndex: Int?,
    val failedStep: AutomationStep?,
    val reason: FailureReason?
)

class AutomationRunner(
    private val context: Context,
    private val session: ShizukuUiAutomationSession,
    private val locator: AutomationLocator,
    private val isStopped: () -> Boolean,
    private val onProgress: (current: Int, total: Int, step: AutomationStep) -> Unit
) {

    private val metrics = context.resources.displayMetrics

    suspend fun run(task: AutomationTask): AutomationResult {
        val total = task.steps.size
        task.steps.forEachIndexed { index, step ->
            if (isStopped()) {
                return failed(index, step, FailureReason.STOPPED, stopped = true)
            }
            onProgress(index + 1, total, step)
            val ok = execute(task, step)
            if (!ok) {
                val reason = if (isStopped()) {
                    FailureReason.STOPPED
                } else {
                    when (step.type) {
                        StepType.WAIT_TEXT, StepType.WAIT_PACKAGE -> FailureReason.TIMEOUT
                        StepType.TAP_TEXT -> FailureReason.TAP_FAILED
                        else -> FailureReason.TAP_FAILED
                    }
                }
                return failed(index, step, reason, stopped = reason == FailureReason.STOPPED)
            }
        }
        return AutomationResult(true, false, null, null, null)
    }

    private suspend fun execute(task: AutomationTask, step: AutomationStep): Boolean {
        return when (step.type) {
            StepType.WAIT_TEXT -> waitForText(step)
            StepType.TAP_TEXT -> tapText(step)
            StepType.WAIT_PACKAGE -> waitForPackage(task, step)
            StepType.TAP_POINT -> {
                val x = (step.x.coerceIn(0f, 1f) * metrics.widthPixels).toInt()
                val y = (step.y.coerceIn(0f, 1f) * metrics.heightPixels).toInt()
                session.tap(x, y)
            }

            StepType.SWIPE -> {
                val x1 = (step.x.coerceIn(0f, 1f) * metrics.widthPixels).toInt()
                val y1 = (step.y.coerceIn(0f, 1f) * metrics.heightPixels).toInt()
                val x2 = (step.x2.coerceIn(0f, 1f) * metrics.widthPixels).toInt()
                val y2 = (step.y2.coerceIn(0f, 1f) * metrics.heightPixels).toInt()
                session.swipe(x1, y1, x2, y2, step.durationMs.coerceAtLeast(50))
            }

            StepType.BACK -> session.pressBack()

            StepType.DELAY -> {
                delay(step.delayMs.coerceAtLeast(0L))
                true
            }
        }
    }

    private suspend fun waitForText(step: AutomationStep): Boolean {
        if (step.text.isBlank()) return false
        val deadline = SystemClock.elapsedRealtime() + timeoutOf(step)
        while (!isStopped() && SystemClock.elapsedRealtime() < deadline) {
            if (locator.findText(step.text, step.exact, step.useOcr) != null) {
                return true
            }
            delay(POLL_MS)
        }
        return false
    }

    private suspend fun tapText(step: AutomationStep): Boolean {
        if (step.text.isBlank()) return false
        val deadline = SystemClock.elapsedRealtime() + timeoutOf(step)
        while (!isStopped() && SystemClock.elapsedRealtime() < deadline) {
            val point = locator.findText(step.text, step.exact, step.useOcr)
            if (point != null) {
                if (session.tap(point.x, point.y)) {
                    delay(POST_TAP_MS)
                    return true
                }
            }
            delay(POLL_MS)
        }
        return false
    }

    private suspend fun waitForPackage(task: AutomationTask, step: AutomationStep): Boolean {
        val target = step.packageName.ifBlank { task.packageName }
        if (target.isBlank()) return false
        if (!ForegroundApp.isUsageAccessGranted(context)) {
            delay(POST_LAUNCH_MS)
            return true
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutOf(step)
        while (!isStopped() && SystemClock.elapsedRealtime() < deadline) {
            if (ForegroundApp.currentPackage(context) == target) {
                return true
            }
            delay(POLL_MS)
        }
        return false
    }

    private fun timeoutOf(step: AutomationStep): Long =
        step.timeoutMs.coerceIn(1_000L, 120_000L)

    private fun failed(
        index: Int,
        step: AutomationStep,
        reason: FailureReason,
        stopped: Boolean = false
    ): AutomationResult = AutomationResult(
        success = false,
        stopped = stopped,
        failedStepIndex = index,
        failedStep = step,
        reason = reason
    )

    companion object {
        private const val POLL_MS = 500L
        private const val POST_TAP_MS = 300L
        private const val POST_LAUNCH_MS = 1_500L
    }
}
