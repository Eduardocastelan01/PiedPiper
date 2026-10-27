package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShuffleHistoryManagerTest {

    private lateinit var manager: ShuffleHistoryManager

    private fun createMediaItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
    }

    @Before
    fun setUp() {
        manager = ShuffleHistoryManager()
    }

    @Test
    fun `initial state has no history and cannot go back`() {
        assertEquals(0, manager.historySize)
        assertFalse(manager.canGoBack)
        assertFalse(manager.hasUpcoming)
        assertNull(manager.getPreviousAndRewind())
    }

    @Test
    fun `single track started cannot go back`() {
        val item1 = createMediaItem("1", "Song 1")
        manager.onTrackStarted(item1)

        assertEquals(1, manager.historySize)
        assertFalse(manager.canGoBack)
        assertNull(manager.getPreviousAndRewind())
    }

    @Test
    fun `two tracks started allows going back to first track`() {
        val item1 = createMediaItem("1", "Song 1")
        val item2 = createMediaItem("2", "Song 2")

        manager.onTrackStarted(item1)
        manager.onTrackStarted(item2)

        assertTrue(manager.canGoBack)
        val prev = manager.getPreviousAndRewind()
        assertEquals("1", prev?.mediaId)
        assertTrue(manager.hasUpcoming)
    }

    @Test
    fun `sequential rewind and forward navigation works`() {
        val item1 = createMediaItem("1", "Song 1")
        val item2 = createMediaItem("2", "Song 2")
        val item3 = createMediaItem("3", "Song 3")

        manager.onTrackStarted(item1)
        manager.onTrackStarted(item2)
        manager.onTrackStarted(item3)

        // Rewind to item 2
        val prev1 = manager.getPreviousAndRewind()
        assertEquals("2", prev1?.mediaId)

        // Rewind to item 1
        val prev2 = manager.getPreviousAndRewind()
        assertEquals("1", prev2?.mediaId)

        // No more history to rewind to
        assertFalse(manager.canGoBack)
        assertNull(manager.getPreviousAndRewind())

        // Moving forward from upcoming
        val next1 = manager.getNextFromHistory()
        assertEquals("2", next1?.mediaId)

        val next2 = manager.getNextFromHistory()
        assertEquals("3", next2?.mediaId)

        assertFalse(manager.hasUpcoming)
        assertNull(manager.getNextFromHistory())
    }

    @Test
    fun `clear resets all stacks`() {
        val item1 = createMediaItem("1", "Song 1")
        val item2 = createMediaItem("2", "Song 2")

        manager.onTrackStarted(item1)
        manager.onTrackStarted(item2)
        manager.clear()

        assertEquals(0, manager.historySize)
        assertFalse(manager.canGoBack)
        assertFalse(manager.hasUpcoming)
    }

    @Test
    fun `history respects maximum capacity`() {
        for (i in 1..250) {
            manager.onTrackStarted(createMediaItem("$i", "Song $i"))
        }

        assertEquals(200, manager.historySize)
    }
}
