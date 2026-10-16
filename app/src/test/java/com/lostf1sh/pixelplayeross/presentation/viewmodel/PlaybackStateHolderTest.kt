package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import com.lostf1sh.pixelplayeross.MainCoroutineExtension
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueItemSnapshot
import com.lostf1sh.pixelplayeross.data.model.PlaybackQueueSnapshot
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import androidx.media3.session.MediaController

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaybackStateHolderTest {

    private val dualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val queueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val powerManager: PowerManager = mockk(relaxed = true)

    private fun createHolder() = PlaybackStateHolder(
        dualPlayerEngine = dualPlayerEngine,
        userPreferencesRepository = userPreferencesRepository,
        queueStateHolder = queueStateHolder,
        appContext = appContext
    )

    private fun snapshot(
        mediaId: String = "duplicate-song",
        positionMs: Long = 48_000L
    ) = PlaybackQueueSnapshot(
        items = listOf(
            PlaybackQueueItemSnapshot(
                mediaId = mediaId,
                uri = "file:///music/$mediaId.mp3"
            )
        ),
        currentMediaId = mediaId,
        currentIndex = 0,
        currentPositionMs = positionMs
    )

    init {
        every { appContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isInteractive } returns true
    }

    @Test
    fun `paused override does not bleed into later occurrence with same media id`() {
        val holder = createHolder()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.rememberPausedPositionOverride("duplicate-song", 91_000L)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `clearing latest media controller restores previous activity controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(mainController)
        holder.setMediaController(externalController)

        assertSame(externalController, holder.mediaController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `clearing stale media controller keeps active controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(externalController)
        holder.setMediaController(mainController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `cold start snapshot only applies to the first matching occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(48_000L, holder.currentPosition.value)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot binds to the already active first occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(0L, holder.currentPosition.value)

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(48_000L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after playback occurrence advances`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after first occurrence already ended`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition(null)

        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    // --- Auto-play on skip and Previous ---

    private fun connectedController(
        playWhenReady: Boolean = false,
        hasNext: Boolean = true,
        hasPrevious: Boolean = true,
        positionMs: Long = 5_000L,
        state: Int = Player.STATE_READY
    ): MediaController = mockk<MediaController>(relaxed = true).also { controller ->
        every { controller.isConnected } returns true
        every { controller.playWhenReady } returns playWhenReady
        every { controller.hasNextMediaItem() } returns hasNext
        every { controller.hasPreviousMediaItem() } returns hasPrevious
        every { controller.currentPosition } returns positionMs
        every { controller.playbackState } returns state
        every { controller.mediaItemCount } returns 3
        every { controller.currentMediaItem } returns null
    }

    /** A holder whose "Auto-play on skip" preference is [autoplayOnSkip], driving [controller]. */
    private fun TestScope.holderFor(
        controller: MediaController,
        autoplayOnSkip: Boolean = true
    ): PlaybackStateHolder {
        // Finite flows, so the collectors started by initialize() let runTest finish.
        every { userPreferencesRepository.autoplayOnSkipFlow } returns flowOf(autoplayOnSkip)
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns null
        return createHolder().also { holder ->
            holder.initialize(this)
            advanceUntilIdle()
            holder.setMediaController(controller)
        }
    }

    @Test
    fun `next while paused starts playback when auto-play on skip is on`() = runTest {
        val controller = connectedController()

        holderFor(controller).nextSong()

        verifyOrder {
            controller.seekToNext()
            controller.play()
        }
    }

    @Test
    fun `next while paused stays paused when auto-play on skip is off`() = runTest {
        val controller = connectedController()

        holderFor(controller, autoplayOnSkip = false).nextSong()

        verify { controller.seekToNext() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `next while already playing does not call play again`() = runTest {
        val controller = connectedController(playWhenReady = true)

        holderFor(controller).nextSong()

        verify { controller.seekToNext() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `next on the last item does not resume the paused song`() = runTest {
        val controller = connectedController(hasNext = false)

        holderFor(controller).nextSong()

        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous between 3 and 10 seconds moves to the previous track`() = runTest {
        // Media3's seekToPrevious() restarts the track past 3 s, so it must not be used here.
        val controller = connectedController(positionMs = 5_000L)

        holderFor(controller).previousSong()

        verifyOrder {
            controller.seekToPreviousMediaItem()
            controller.play()
        }
        verify(exactly = 0) { controller.seekToPrevious() }
        verify(exactly = 0) { controller.seekTo(0L) }
    }

    @Test
    fun `previous after 10 seconds restarts the track without auto-play`() = runTest {
        val controller = connectedController(positionMs = 12_000L)

        holderFor(controller).previousSong()

        verify { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous on the first item restarts it instead of doing nothing`() = runTest {
        val controller = connectedController(hasPrevious = false, positionMs = 1_000L)

        holderFor(controller).previousSong()

        verify { controller.seekTo(0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `force previous while paused moves back and starts playback`() = runTest {
        val controller = connectedController(positionMs = 30_000L)

        holderFor(controller).forcePreviousSong()

        verifyOrder {
            controller.seekToPreviousMediaItem()
            controller.play()
        }
    }

    @Test
    fun `skip from an idle player prepares it before playing`() = runTest {
        val controller = connectedController(state = Player.STATE_IDLE)

        holderFor(controller).nextSong()

        verifyOrder {
            controller.seekToNext()
            controller.prepare()
            controller.play()
        }
    }

    // --- Owner-scoped teardown ---

    // Separate Job so each owner has its own identity, while still running on the test
    // dispatcher that advanceTimeBy drives.
    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    /** A playing controller whose reported position is read from [positionMs] on every tick. */
    private fun playingController(positionMs: () -> Long): MediaController =
        connectedController(playWhenReady = true).also { controller ->
            every { controller.isPlaying } returns true
            every { controller.currentPosition } answers { positionMs() }
        }

    /** Binds a holder to [owners] in order, so the last one is the current owner. */
    private fun TestScope.holderBoundTo(
        vararg owners: CoroutineScope,
        controller: MediaController
    ): PlaybackStateHolder {
        every { userPreferencesRepository.autoplayOnSkipFlow } returns flowOf(true)
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns null
        return createHolder().also { holder ->
            owners.forEach(holder::initialize)
            runCurrent()
            holder.setMediaController(controller)
            // Progress only ticks while someone observes the position, as the player UI does.
            backgroundScope.launch { holder.currentPosition.collect {} }
            holder.startProgressUpdates()
            runCurrent()
        }
    }

    @Test
    fun `stale owner clearing keeps the newer owner's progress updates running`() = runTest {
        var positionMs = 5_000L
        val staleOwner = ownerScope()
        val currentOwner = ownerScope()
        val holder = holderBoundTo(staleOwner, currentOwner, controller = playingController { positionMs })
        assertEquals(5_000L, holder.currentPosition.value)

        holder.onCleared(staleOwner)
        staleOwner.cancel()
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(7_000L, holder.currentPosition.value)
        currentOwner.cancel()
    }

    @Test
    fun `current owner clearing stops its progress updates`() = runTest {
        var positionMs = 5_000L
        val owner = ownerScope()
        val holder = holderBoundTo(owner, controller = playingController { positionMs })
        assertEquals(5_000L, holder.currentPosition.value)

        holder.onCleared(owner)
        positionMs = 7_000L
        advanceTimeBy(1_500L)
        runCurrent()
        // Unbound, so a later start request has no scope to run in.
        holder.startProgressUpdates()
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(5_000L, holder.currentPosition.value)
        owner.cancel()
    }
}
