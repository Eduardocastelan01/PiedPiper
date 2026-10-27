package com.lostf1sh.pixelplayeross.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.preferences.PreviousButtonBehavior

/**
 * Applies the "Previous button" preference to the session-facing player. The notification,
 * lock screen, headset and Bluetooth keys, Android Auto and the home-screen widget all reach
 * the player through [seekToPrevious].
 *
 * [PreviousButtonBehavior.RESTART_TRACK] keeps Media3's own [seekToPrevious] (restart past
 * 3 s), so the default is unchanged. The app's own controls apply the preference in
 * PlaybackStateHolder and send [seekToPreviousMediaItem] or seekTo(), which pass through as is.
 */
@OptIn(UnstableApi::class)
class PreviousButtonPlayer(
    val innerPlayer: Player,
    private val behaviorProvider: () -> PreviousButtonBehavior,
    private val shuffleHistoryManager: ShuffleHistoryManager? = null,
) : ForwardingPlayer(innerPlayer) {

    override fun seekToPrevious() {
        if (behaviorProvider() == PreviousButtonBehavior.PREVIOUS_TRACK) {
            if (shuffleHistoryManager?.canGoBack == true && (innerPlayer.shuffleModeEnabled || !hasPreviousMediaItem())) {
                val prev = shuffleHistoryManager.getPreviousAndRewind()
                if (prev != null) {
                    val timeline = innerPlayer.currentTimeline
                    val window = Timeline.Window()
                    var targetIndex = -1
                    for (i in 0 until timeline.windowCount) {
                        timeline.getWindow(i, window)
                        if (window.mediaItem.mediaId == prev.mediaId) {
                            targetIndex = i
                            break
                        }
                    }
                    if (targetIndex >= 0) {
                        seekToDefaultPosition(targetIndex)
                        return
                    }
                }
            }
            if (hasPreviousMediaItem()) {
                seekToPreviousMediaItem()
            } else {
                super.seekToPrevious()
            }
        } else {
            super.seekToPrevious()
        }
    }
}

