package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MediaItem
import timber.log.Timber

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pied Piper -- Bug Fix #2: Shuffle History Manager
 *
 * The original PixelPlayerOSS randomly selects tracks without maintaining
 * a history, so pressing "previous" in shuffle mode has undefined behavior.
 *
 * This class maintains two stacks:
 * - playedStack: tracks played so far (most recent at the end)
 * - upcomingStack: tracks that were previously in the shuffle queue but
 *   the user navigated back from (re-inserted when going forward again)
 *
 * Integration: call onTrackStarted when a new track begins playing in shuffle mode,
 * and consult canGoBack/getPreviousAndRewind for the "previous" button behavior.
 */
@Singleton
class ShuffleHistoryManager @Inject constructor() {

    /** Tracks played so far. The last entry is the current track. */
    private val playedStack = ArrayDeque<MediaItem>()

    /** Tracks the user navigated "back" from -- re-queued on "next". */
    private val upcomingStack = ArrayDeque<MediaItem>()

    /** Whether there is a previous track to go back to (requires at least 2 items in history). */
    val canGoBack: Boolean
        get() = playedStack.size >= 2

    /** Number of tracks in the shuffle history. */
    val historySize: Int
        get() = playedStack.size

    /**
     * Called when a new track starts playing in shuffle mode.
     * If clearUpcoming is true (default when user picks a new track normally),
     * the upcoming stack is cleared.
     */
    fun onTrackStarted(item: MediaItem, clearUpcoming: Boolean = true) {
        playedStack.addLast(item)
        if (clearUpcoming) upcomingStack.clear()

        // Prevent unbounded memory growth: keep last 200 tracks in history
        while (playedStack.size > MAX_HISTORY_SIZE) {
            playedStack.removeFirst()
        }
        Timber.tag(TAG).d("Track started: %s | History: %d | Upcoming: %d",
            item.mediaMetadata.title, playedStack.size, upcomingStack.size)
    }

    /**
     * Returns the previous MediaItem and rewinds the position pointer.
     * The current track (last in playedStack) is moved to upcomingStack.
     * Returns null if there is no history to go back to.
     */
    fun getPreviousAndRewind(): MediaItem? {
        if (!canGoBack) return null
        val current = playedStack.removeLast()
        upcomingStack.addFirst(current) // Re-queue current for "next"
        return playedStack.lastOrNull()
    }

    /**
     * Returns the next track to play from upcomingStack if there is one
     * (happens after navigating backwards), otherwise returns null to signal
     * that a new random track should be picked from the library.
     */
    fun getNextFromHistory(): MediaItem? = upcomingStack.removeFirstOrNull()

    /**
     * Whether there is a track in the upcoming stack (i.e., the user
     * navigated backward and can go forward to a previously played track).
     */
    val hasUpcoming: Boolean
        get() = upcomingStack.isNotEmpty()

    /** Clears all history (e.g., when shuffle is turned off or a new queue starts). */
    fun clear() {
        playedStack.clear()
        upcomingStack.clear()
        Timber.tag(TAG).d("Shuffle history cleared")
    }

    companion object {
        private const val TAG = "ShuffleHistoryManager"
        private const val MAX_HISTORY_SIZE = 200
    }
}