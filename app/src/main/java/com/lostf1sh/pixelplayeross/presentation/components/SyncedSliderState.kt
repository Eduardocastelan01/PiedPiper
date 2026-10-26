package com.lostf1sh.pixelplayeross.presentation.components

import androidx.annotation.IntRange
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * [SliderState] for a [Slider] whose value is hoisted elsewhere, such as a preference. When
 * `onValueChange` is passed, Material 3 leaves the state alone and reports drags to the caller, so
 * the hoisted [value] is copied back after each composition instead of during it.
 */
@Composable
fun rememberSyncedSliderState(
    value: Float,
    @IntRange(from = 0) steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): SliderState {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    SideEffect { state.value = value }
    return state
}
