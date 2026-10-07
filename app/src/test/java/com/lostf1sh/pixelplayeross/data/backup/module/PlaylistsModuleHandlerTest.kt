package com.lostf1sh.pixelplayeross.data.backup.module

import android.content.Context
import com.google.gson.GsonBuilder
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongSummary
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SMART_PLAYLIST_SOURCE_LEGACY
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.data.worker.SyncProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import javax.inject.Provider

class PlaylistsModuleHandlerTest {
    @TempDir lateinit var tempDir: Path

    private val gson = GsonBuilder().serializeNulls().create()
    private val musicDao = mockk<MusicDao>(relaxed = true)
    private val playlists = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)

    private fun summary(id: Long, title: String) = SongSummary(id, title, "Artist", "Album", 200_000)

    private fun handler(scope: CoroutineScope, syncProgress: Flow<SyncProgress> = emptyFlow()): PlaylistsModuleHandler {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir.toFile()
        val sync = mockk<SyncManager>()
        every { sync.syncProgress } returns syncProgress
        return PlaylistsModuleHandler(
            context, playlists, preferences, musicDao, BackupSongResolver(musicDao), gson,
            Provider { sync }, scope
        )
    }

    private val pendingFile: File
        get() = File(tempDir.toFile(), "pending_playlists_restore.json")

    @Test
    fun `playlists keep their name and order across device ids`() = runTest {
        val handler = handler(backgroundScope)
        val local = Playlist("road", "Road trip", listOf("2", "1"))
        val smart = local.copy(id = "smart", source = SMART_PLAYLIST_SOURCE_LEGACY)
        coEvery { playlists.getPlaylistsOnce() } returns listOf(local, smart)
        every { playlists.playlistSongOrderModesFlow } returns flowOf(mapOf("road" to "custom"))
        every { playlists.playlistsSortOptionFlow } returns flowOf("name_az")
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(1, "One"), summary(2, "Two"))
        val payload = handler.export()

        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        handler.restore(payload)

        coVerify {
            playlists.replaceAllPlaylists(
                listOf(
                    local.copy(songIds = listOf("42", "41")),
                    smart.copy(songIds = listOf("42", "41")),
                )
            )
            playlists.setPlaylistSongOrderModes(mapOf("road" to "custom"))
        }
        assertFalse(pendingFile.exists())
    }

    @Test
    fun `songs restored before the library scan are filled in once a sync finishes`() = runTest {
        val syncProgress = MutableStateFlow(SyncProgress())
        val handler = handler(backgroundScope, syncProgress)
        var stored = emptyList<Playlist>()
        coEvery { playlists.getPlaylistsOnce() } answers { stored }
        coEvery { playlists.replaceAllPlaylists(any()) } answers { stored = firstArg() }
        val payload = """
            {"playlists":[{"id":"road","name":"Road trip","songIds":["1","2"],"source":"LOCAL"}],
             "songMetadata":{
               "1":{"title":"One","artist":"Artist","album":"Album","duration":200000},
               "2":{"title":"Two","artist":"Artist","album":"Album","duration":200000}}}
        """.trimIndent()

        // Fresh install: nothing scanned yet. The handler starts watching sync progress.
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        runCurrent()
        handler.restore(payload)
        assertEquals(emptyList<String>(), stored.single().songIds)
        assertTrue(pendingFile.exists())

        // The first scan indexes the songs under this device's ids. The running state is
        // conflated away before the collector sees it, as a short sync can do in production.
        coEvery { musicDao.getAllLocalSongSummaries() } returns listOf(summary(41, "One"), summary(42, "Two"))
        syncProgress.value = SyncProgress(isRunning = true)
        syncProgress.value = SyncProgress(isRunning = false, isCompleted = true)
        // runCurrent, not advanceUntilIdle: the latter stops once only backgroundScope work is left.
        runCurrent()
        assertEquals(listOf("41", "42"), stored.single().songIds)
        assertFalse(pendingFile.exists())
    }
}
