package org.caojun.shizuku.autoreturn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLaunchTrackerTest {

    @Test
    fun consumeWithinTimeout_returnsTrue() {
        val tracker = PendingLaunchTracker(timeoutMs = 1_000)
        tracker.mark(now = 5_000)

        assertTrue(tracker.consume(now = 5_500))
    }

    @Test
    fun consumeAfterTimeout_returnsFalse() {
        val tracker = PendingLaunchTracker(timeoutMs = 1_000)
        tracker.mark(now = 5_000)

        assertFalse(tracker.consume(now = 6_100))
    }

    @Test
    fun consumeWithoutMark_returnsFalse() {
        val tracker = PendingLaunchTracker(timeoutMs = 1_000)

        assertFalse(tracker.consume(now = 5_000))
    }

    @Test
    fun consumeTwice_onlyFirstSucceeds() {
        val tracker = PendingLaunchTracker(timeoutMs = 1_000)
        tracker.mark(now = 5_000)

        assertTrue(tracker.consume(now = 5_100))
        assertFalse(tracker.consume(now = 5_200))
    }

    @Test
    fun clearResetsPending() {
        val tracker = PendingLaunchTracker(timeoutMs = 1_000)
        tracker.mark(now = 5_000)
        tracker.clear()

        assertFalse(tracker.consume(now = 5_100))
    }
}
