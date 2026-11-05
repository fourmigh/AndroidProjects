package org.caojun.autoocrtask.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationTaskTest {

    @Test
    fun taskJsonRoundTrip() {
        val task = AutomationTask(
            id = "task_1",
            name = "每日签到",
            packageName = "com.example.app",
            launch = LaunchSpec(
                type = LaunchType.URI,
                activity = "com.example.app/.MainActivity",
                uri = "example://checkin"
            ),
            steps = listOf(
                AutomationStep(
                    type = StepType.WAIT_TEXT,
                    text = "我的",
                    timeoutMs = 15_000L
                ),
                AutomationStep(
                    type = StepType.TAP_TEXT,
                    text = "签到",
                    timeoutMs = 5_000L,
                    exact = true,
                    useOcr = false
                ),
                AutomationStep(
                    type = StepType.SWIPE,
                    x = 0.5f,
                    y = 0.8f,
                    x2 = 0.5f,
                    y2 = 0.2f,
                    durationMs = 400
                ),
                AutomationStep(type = StepType.DELAY, delayMs = 1_200L),
                AutomationStep(type = StepType.BACK)
            )
        )

        val restored = automationTaskFromJson(task.toJson())

        assertEquals(task, restored)
    }
}
