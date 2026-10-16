package com.lostf1sh.pixelplayeross.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Back handling for the queue sheet.
 *
 * On Android 14+ the live gesture progress is written to [progress], which the queue sheet and its
 * scrim read in the draw phase only. Committing calls [onBack] and eases the progress back to 0
 * while the sheet closes; cancelling springs it back to 0 with the sheet still open. Older
 * versions get a plain [BackHandler].
 *
 * The resets run in [scope], which must outlive this handler (the sheet's scope): the handler can
 * leave composition mid-gesture or mid-reset, and a scope of its own would be cancelled with it,
 * leaving [progress] stuck at a partial value for the next time the queue opens.
 */
@Composable
internal fun QueuePredictiveBackHandler(
    enabled: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    animationDurationMs: Int,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = enabled) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progress.snapTo(backEvent.progress)
                }
                onBack()
                scope.launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(animationDurationMs)
                    )
                }
            } catch (_: CancellationException) {
                scope.launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(animationDurationMs)
                    )
                }
            }
        }
    } else {
        BackHandler(enabled = enabled, onBack = onBack)
    }
}
