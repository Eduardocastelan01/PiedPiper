package com.lostf1sh.pixelplayeross.presentation.components

import android.view.ViewTreeObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the slider losing track of playback position: it must follow the current
 * value provider even when the caller swaps the state object behind it, which happens when a caller
 * keys its remember on the current song. Also covers the slider going idle once playback pauses.
 */
@RunWith(AndroidJUnit4::class)
class WavySliderExpressiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun reportedProgress(): Float {
        val node = composeRule.onNodeWithContentDescription(LABEL).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    @Test
    fun followsValueAfterBackingStateIsReplaced() {
        var stateKey by mutableStateOf(0)
        lateinit var latestState: MutableState<Float>

        composeRule.setContent {
            val position = remember(stateKey) { mutableStateOf(0.25f) }
            latestState = position
            WavySliderExpressive(
                value = { position.value },
                onValueChange = {},
                semanticsLabel = LABEL
            )
        }

        composeRule.waitForIdle()
        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0.25f)

        composeRule.runOnUiThread { stateKey = 1 }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { latestState.value = 0.8f }
        composeRule.waitForIdle()

        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0.8f)
    }

    @Test
    fun reportsNonFiniteValueAsZero() {
        composeRule.setContent {
            val position = remember { mutableFloatStateOf(Float.NaN) }
            WavySliderExpressive(
                value = { position.floatValue },
                onValueChange = {},
                semanticsLabel = LABEL
            )
        }

        composeRule.waitForIdle()
        assertThat(reportedProgress()).isWithin(TOLERANCE).of(0f)
    }

    /**
     * Pausing must leave the wave flat and the slider idle. When the indicator was fed the animated
     * amplitude it settled on a tiny residual wave after a pause, and its wave offset animation then
     * redrew the paused player on every frame.
     */
    @Test
    fun stopsRedrawingAfterPause() {
        var isPlaying by mutableStateOf(true)
        val draws = AtomicInteger()
        // While the test clock auto-advances it cancels infinite animations, which would hide the
        // wave offset animation that kept redrawing.
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val listener = ViewTreeObserver.OnDrawListener { draws.incrementAndGet() }
                view.viewTreeObserver.addOnDrawListener(listener)
                onDispose { view.viewTreeObserver.removeOnDrawListener(listener) }
            }
            WavySliderExpressive(
                value = { 0.5f },
                onValueChange = {},
                isPlaying = isPlaying,
                semanticsLabel = LABEL
            )
        }
        advanceFrames(10)

        composeRule.runOnUiThread { isPlaying = false }
        // Long enough for the slider's amplitude spring and the indicator's own fade to settle.
        advanceFrames(150)
        Thread.sleep(SETTLE_WAIT_MS)

        draws.set(0)
        advanceFrames(20)
        assertThat(draws.get()).isEqualTo(0)
    }

    /**
     * Steps the test clock one frame at a time. Drawing runs on the real choreographer rather than
     * the test clock, so each step waits for that frame's invalidation to be drawn.
     */
    private fun advanceFrames(count: Int) {
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            Thread.sleep(FRAME_DRAW_WAIT_MS)
        }
    }

    private companion object {
        const val LABEL = "Playback position"
        const val TOLERANCE = 0.02f
        const val FRAME_DRAW_WAIT_MS = 20L
        const val SETTLE_WAIT_MS = 100L
    }
}
