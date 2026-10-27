package com.lostf1sh.pixelplayeross.data.ai

import android.content.Context
import com.google.gson.Gson
import com.lostf1sh.pixelplayeross.data.model.Song
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AIPlaylistClientTest {

    private lateinit var keyStore: SecureKeyStore
    private lateinit var client: AIPlaylistClient
    private val gson = Gson()

    private val sampleSongs = listOf(
        Song(id = "1", title = "Stairway to Heaven", artist = "Led Zeppelin", album = "IV", genre = "Classic Rock"),
        Song(id = "2", title = "Billie Jean", artist = "Michael Jackson", album = "Thriller", genre = "Pop"),
        Song(id = "3", title = "Smells Like Teen Spirit", artist = "Nirvana", album = "Nevermind", genre = "Grunge"),
        Song(id = "4", title = "Lose Yourself", artist = "Eminem", album = "8 Mile", genre = "Hip-Hop"),
        Song(id = "5", title = "Clair de Lune", artist = "Claude Debussy", album = "Suite Bergamasque", genre = "Classical"),
        Song(id = "6", title = "One More Time", artist = "Daft Punk", album = "Discovery", genre = "Electronic"),
        Song(id = "7", title = "Master of Puppets", artist = "Metallica", album = "Master of Puppets", genre = "Metal")
    )

    @Before
    fun setUp() {
        val mockContext = mockk<Context>(relaxed = true)
        keyStore = mockk(relaxed = true)
        client = AIPlaylistClient(keyStore, gson)
    }

    @Test
    fun `offline heuristic matches rock songs when rock prompt is given`() {
        val request = PlaylistGenerationRequest(
            prompt = "classic rock workout",
            count = 2,
            availableSongs = sampleSongs
        )

        val result = client.generateOfflineHeuristicPlaylist(request)

        assertTrue(result.isOfflineFallback)
        assertTrue(result.songs.isNotEmpty())
        assertEquals("1", result.songs.first().id) // Stairway to Heaven matched "rock"
    }

    @Test
    fun `offline heuristic matches hip-hop songs by artist or genre`() {
        val request = PlaylistGenerationRequest(
            prompt = "eminem and rap songs",
            count = 1,
            availableSongs = sampleSongs
        )

        val result = client.generateOfflineHeuristicPlaylist(request)

        assertEquals("4", result.songs.first().id) // Eminem matched "eminem"
    }

    @Test
    fun `fallback to offline heuristic when no key configured and fallback enabled`() = runBlocking {
        every { keyStore.getActiveProvider() } returns AIProvider.GEMINI
        every { keyStore.getKey(AIProvider.GEMINI) } returns null
        every { keyStore.isOfflineFallbackEnabled() } returns true

        val request = PlaylistGenerationRequest(
            prompt = "electronic dance music",
            count = 1,
            availableSongs = sampleSongs
        )

        val result = client.generatePlaylist(request)

        assertTrue(result.isSuccess)
        val playlist = result.getOrThrow()
        assertTrue(playlist.isOfflineFallback)
        assertEquals("6", playlist.songs.first().id) // Daft Punk - Electronic
    }

    @Test
    fun `fails with NoKeyConfigured when fallback disabled and key missing`() = runBlocking {
        every { keyStore.getActiveProvider() } returns AIProvider.GEMINI
        every { keyStore.getKey(AIProvider.GEMINI) } returns null
        every { keyStore.isOfflineFallbackEnabled() } returns false

        val request = PlaylistGenerationRequest(
            prompt = "summer pop hits",
            count = 5,
            availableSongs = sampleSongs
        )

        val result = client.generatePlaylist(request)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIException.NoKeyConfigured)
    }
}
