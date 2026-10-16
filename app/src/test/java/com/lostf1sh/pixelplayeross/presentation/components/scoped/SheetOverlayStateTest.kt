package com.lostf1sh.pixelplayeross.presentation.components.scoped

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SheetOverlayStateTest {

    private val hiddenOffsetPx = 1200f

    private fun fraction(
        isQueueShown: Boolean = true,
        offsetPx: Float,
        hiddenPx: Float = hiddenOffsetPx,
        backProgress: Float = 0f
    ): Float = computeQueueVisualOpenFraction(
        isQueueShown = isQueueShown,
        queueSheetOffsetPx = offsetPx,
        queueHiddenOffsetPx = hiddenPx,
        predictiveBackProgress = backProgress
    )

    @Test
    fun queueAtHiddenOffset_doesNotCoverThePlayer() {
        assertEquals(0f, fraction(offsetPx = hiddenOffsetPx))
    }

    @Test
    fun fullyOpenQueue_coversThePlayerCompletely() {
        assertEquals(1f, fraction(offsetPx = 0f))
    }

    @Test
    fun queueDraggedHalfway_coversHalfThePlayer() {
        assertEquals(0.5f, fraction(offsetPx = hiddenOffsetPx / 2f), 0.0001f)
    }

    @Test
    fun predictiveBackHalfway_halvesTheOpenFraction() {
        assertEquals(0.5f, fraction(offsetPx = 0f, backProgress = 0.5f), 0.0001f)
        assertEquals(0.25f, fraction(offsetPx = hiddenOffsetPx / 2f, backProgress = 0.5f), 0.0001f)
    }

    @Test
    fun completedBackGesture_removesTheCoverage() {
        assertEquals(0f, fraction(offsetPx = 0f, backProgress = 1f))
    }

    @Test
    fun outOfRangeBackProgress_isClamped() {
        assertEquals(0f, fraction(offsetPx = 0f, backProgress = 1.4f))
        assertEquals(1f, fraction(offsetPx = 0f, backProgress = -0.3f))
    }

    @Test
    fun unmeasuredQueueSheet_reportsNoCoverage() {
        assertEquals(0f, fraction(offsetPx = 0f, hiddenPx = 0f))
        assertEquals(0f, fraction(offsetPx = 0f, hiddenPx = -10f))
    }

    @Test
    fun hiddenQueue_reportsNoCoverageWhateverItsOffset() {
        assertEquals(0f, fraction(isQueueShown = false, offsetPx = 0f))
        assertEquals(0f, fraction(isQueueShown = false, offsetPx = hiddenOffsetPx / 2f))
    }

    @Test
    fun offsetBeyondHiddenOffset_isClampedToNoCoverage() {
        assertEquals(0f, fraction(offsetPx = hiddenOffsetPx * 1.5f))
    }

    @Test
    fun overscrolledOffset_isClampedToFullCoverage() {
        assertEquals(1f, fraction(offsetPx = -40f))
    }
}
