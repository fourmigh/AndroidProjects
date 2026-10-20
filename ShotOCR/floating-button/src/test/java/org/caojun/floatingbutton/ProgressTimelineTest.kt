package org.caojun.floatingbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTimelineTest {

    @Test
    fun recordsStepDurations() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a", "b", "c"), 1, 1_000)
        timeline.update(2, 1_120)
        timeline.update(3, 2_500)
        timeline.finish(3_000)

        val rows = timeline.rows(4_000)

        assertEquals(120L, rows[0].durationMs)
        assertEquals(1_380L, rows[1].durationMs)
        assertEquals(500L, rows[2].durationMs)
        assertFalse(rows.any { it.isCurrent })
        assertTrue(timeline.isFinished)
        assertEquals(2_000L, timeline.totalDurationMs(4_000))
    }

    @Test
    fun currentStepDurationGrowsUntilNextStep() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a", "b"), 1, 1_000)
        timeline.update(2, 1_300)

        assertEquals(300L, timeline.rows(1_500)[0].durationMs)
        assertEquals(200L, timeline.rows(1_500)[1].durationMs)
        assertTrue(timeline.rows(1_500)[1].isCurrent)
    }

    @Test
    fun liveTotalIncludesCurrentStep() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a", "b"), 1, 1_000)
        timeline.update(2, 1_200)

        assertEquals(500L, timeline.totalDurationMs(1_500))
    }

    @Test
    fun duplicateUpdateDoesNotResetStart() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a", "b"), 1, 1_000)
        timeline.update(1, 1_500)
        timeline.update(2, 1_800)

        assertEquals(800L, timeline.rows(2_000)[0].durationMs)
    }

    @Test
    fun pendingStepsHaveNoDuration() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a", "b", "c"), 1, 1_000)

        val rows = timeline.rows(1_200)

        assertEquals(200L, rows[0].durationMs)
        assertNull(rows[1].durationMs)
        assertNull(rows[2].durationMs)
    }

    @Test
    fun resetClearsPreviousRun() {
        val timeline = ProgressTimeline()
        timeline.start(listOf("a"), 1, 1_000)
        timeline.finish(2_000)
        timeline.reset()

        assertFalse(timeline.isStarted)
        assertFalse(timeline.isFinished)
        assertEquals(0, timeline.totalSteps)
        assertTrue(timeline.rows(3_000).isEmpty())
    }
}
