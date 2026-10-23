package org.caojun.floatingbutton

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingButtonConfigTest {

    @Test
    fun normalized_clampsValuesIntoSupportedRanges() {
        val config = FloatingButtonConfig(
            sizeDp = 200,
            edgeAutoHideMs = 99_000L,
            opacity = 0f
        ).normalized()

        assertEquals(FloatingButtonConfig.MAX_SIZE_DP, config.sizeDp)
        assertEquals(FloatingButtonConfig.MAX_EDGE_AUTO_HIDE_MS, config.edgeAutoHideMs)
        assertEquals(FloatingButtonConfig.MIN_OPACITY, config.opacity, 0.001f)
    }

    @Test
    fun normalized_forcesOpaqueBackgroundColor() {
        val config = FloatingButtonConfig(backgroundColor = 0x00212121).normalized()

        assertEquals(0xFF212121.toInt(), config.backgroundColor)
    }

    @Test
    fun edgeTapAction_unknownIdFallsBackToTrigger() {
        assertEquals(
            FloatingButtonEdgeTapAction.TRIGGER,
            FloatingButtonEdgeTapAction.fromId("unknown")
        )
    }

    @Test
    fun edgeTapAction_revealFirstIdIsParsed() {
        assertEquals(
            FloatingButtonEdgeTapAction.REVEAL_FIRST,
            FloatingButtonEdgeTapAction.fromId("reveal_first")
        )
    }

    @Test
    fun shape_unknownIdFallsBackToCircle() {
        assertEquals(
            FloatingButtonShape.CIRCLE,
            FloatingButtonShape.fromId("unknown")
        )
    }

    @Test
    fun icon_unknownIdFallsBackToCamera() {
        assertEquals(
            FloatingButtonIcon.CAMERA,
            FloatingButtonIcon.fromId("unknown")
        )
    }
}
