package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.GsonBuilder
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkDao
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SongSummary
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioBookmarksModuleHandlerTest {
    private val gson = GsonBuilder().serializeNulls().create()
    private val sourceDao = mockk<MusicDao>()
    private val targetDao = mockk<MusicDao>()
    private val bookmarks = mockk<AudioBookmarkDao>(relaxed = true)

    private fun summary(id: Long, title: String) = SongSummary(id, title, "Artist", "Album", 200_000)

    // A non-local content uri keeps the stored artwork uri as-is through toSong().
    private fun song(id: Long, title: String, art: String) = SongEntity(
        id = id, title = title, artistName = "Artist", artistId = 1, albumName = "Album", albumId = 1,
        contentUriString = "jellyfin://$id", albumArtUriString = art, duration = 200_000, genre = null,
        filePath = "", parentDirectoryPath = ""
    )

    private fun bookmark(id: Long, songId: String, songTitle: String, title: String) = AudioBookmarkEntity(
        id = id, songId = songId, songTitle = songTitle, artistName = "Artist",
        albumArtUri = "content://media/external/audio/albumart/$songId", title = title, timestampMs = 30_000, createdTime = 1
    )

    private fun restoreOnTarget(payload: String): List<AudioBookmarkEntity> {
        val restored = slot<List<AudioBookmarkEntity>>()
        coEvery { bookmarks.replaceAll(capture(restored)) } just Runs
        runTest { AudioBookmarksModuleHandler(bookmarks, BackupSongResolver(targetDao), gson).restore(payload) }
        return restored.captured
    }

    @Test
    fun `bookmarks follow their songs to another device's ids and take its artwork`() = runTest {
        coEvery { sourceDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"))
        coEvery { bookmarks.getAllBookmarksOnce() } returns listOf(
            bookmark(10, "1", "One", "Drop"),
            bookmark(11, "2", "Two", "Chorus"),
        )
        val payload = AudioBookmarksModuleHandler(bookmarks, BackupSongResolver(sourceDao), gson).export()

        // New device: same songs under new ids, and id 1 now names another song.
        coEvery { targetDao.getAllLocalSongSummaries() } returns
            listOf(summary(1, "Other"), summary(41, "One"), summary(42, "Two"))
        coEvery { targetDao.getSongsByIdsListSimple(any()) } returns
            listOf(song(41, "One", "art://41"), song(42, "Two", "art://42"))

        val restored = restoreOnTarget(payload).associateBy { it.title }
        assertEquals("41", restored.getValue("Drop").songId)
        assertEquals("art://41", restored.getValue("Drop").albumArtUri)
        assertEquals("42", restored.getValue("Chorus").songId)
        assertEquals("art://42", restored.getValue("Chorus").albumArtUri)
    }

    @Test
    fun `a bookmark whose id now names another song and whose song is missing is dropped`() {
        val legacy = """[{"id":10,"songId":"1","songTitle":"Gone","artistName":"Artist","title":"Drop","timestampMs":5000,"createdTime":1}]"""
        coEvery { targetDao.getAllLocalSongSummaries() } returns listOf(summary(1, "Other"))
        coEvery { targetDao.getSongsByIdsListSimple(any()) } returns emptyList()

        assertTrue(restoreOnTarget(legacy).isEmpty())
    }

    @Test
    fun `a legacy payload is matched on the stored title and artist`() {
        val legacy = """[{"id":10,"songId":"7","songTitle":"One","artistName":"Artist","title":"Drop","timestampMs":5000,"createdTime":1}]"""
        coEvery { targetDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        coEvery { targetDao.getSongsByIdsListSimple(any()) } returns emptyList()

        assertEquals("41", restoreOnTarget(legacy).single().songId)
    }

    @Test
    fun `an unmatched song that no other song claims keeps its id`() {
        // A cloud song, or a library that hasn't been scanned yet.
        val legacy = """[{"id":10,"songId":"-55","songTitle":"Remote","artistName":"Artist","title":"Drop","timestampMs":5000,"createdTime":1}]"""
        coEvery { targetDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery { targetDao.getSongsByIdsListSimple(any()) } returns emptyList()

        val restored = restoreOnTarget(legacy).single()
        assertEquals("-55", restored.songId)
        assertNull(restored.albumArtUri)
    }

    @Test
    fun `malformed entries are skipped or repaired instead of failing the restore`() {
        val payload = """[
            {"id":1,"songTitle":"No id","title":"x","timestampMs":1},
            {"id":2,"songId":"  ","title":"blank id","timestampMs":1},
            {"id":3,"songId":"41","timestampMs":-20},
            "not an object"
        ]"""
        coEvery { targetDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"))
        coEvery { targetDao.getSongsByIdsListSimple(any()) } returns emptyList()

        val restored = restoreOnTarget(payload).single()
        assertEquals("41", restored.songId)
        assertEquals("", restored.title)
        assertEquals("", restored.songTitle)
        assertEquals(0L, restored.timestampMs)
    }
}
