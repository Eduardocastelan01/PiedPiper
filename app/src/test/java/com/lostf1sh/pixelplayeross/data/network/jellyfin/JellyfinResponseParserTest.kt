package com.lostf1sh.pixelplayeross.data.network.jellyfin

import org.json.JSONObject
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JellyfinResponseParserTest {

    private fun playlist(mediaType: String?) = JSONObject().apply {
        put("Id", "playlist-1")
        put("Name", "Roadtrip")
        if (mediaType != null) put("MediaType", mediaType)
    }

    @Test
    fun `audio playlists are kept`() {
        assertTrue(JellyfinResponseParser.isAudioPlaylist(playlist("Audio")))
        assertTrue(JellyfinResponseParser.isAudioPlaylist(playlist("audio")))
    }

    @Test
    fun `mixed content playlists on Jellyfin 10 10 and newer are kept`() {
        assertTrue(JellyfinResponseParser.isAudioPlaylist(playlist(null)))
        assertTrue(JellyfinResponseParser.isAudioPlaylist(playlist("")))
        assertTrue(JellyfinResponseParser.isAudioPlaylist(playlist("Unknown")))
    }

    @Test
    fun `playlists of another media type are rejected`() {
        assertFalse(JellyfinResponseParser.isAudioPlaylist(playlist("Video")))
        assertFalse(JellyfinResponseParser.isAudioPlaylist(playlist("Photo")))
        assertFalse(JellyfinResponseParser.isAudioPlaylist(playlist("Book")))
    }

    private fun item(type: String?, mediaType: String?) = JSONObject().apply {
        put("Id", "item-1")
        put("Name", "Track")
        if (type != null) put("Type", type)
        if (mediaType != null) put("MediaType", mediaType)
    }

    @Test
    fun `tracks are kept as playlist items`() {
        assertTrue(JellyfinResponseParser.isAudioItem(item("Audio", "Audio")))
        assertTrue(JellyfinResponseParser.isAudioItem(item(null, "Audio")))
        assertTrue(JellyfinResponseParser.isAudioItem(item("Audio", null)))
    }

    @Test
    fun `non-audio children of a mixed playlist are skipped`() {
        assertFalse(JellyfinResponseParser.isAudioItem(item("Episode", "Video")))
        assertFalse(JellyfinResponseParser.isAudioItem(item("Movie", "Video")))
        assertFalse(JellyfinResponseParser.isAudioItem(item("Photo", "Photo")))
        assertFalse(JellyfinResponseParser.isAudioItem(item(null, null)))
    }

    @Test
    fun `parseSong maps ParentIndexNumber to the disc and IndexNumber to the track`() {
        val song = JellyfinResponseParser.parseSong(
            JSONObject(
                """{"Id": "song-1", "Name": "What'd I Miss", "Album": "Hamilton", "IndexNumber": 1, "ParentIndexNumber": 2}"""
            )
        )

        assertThat(song.discNumber).isEqualTo(2)
        assertThat(song.trackNumber).isEqualTo(1)
    }

    @Test
    fun `parseSong reports disc zero when ParentIndexNumber is missing`() {
        val song = JellyfinResponseParser.parseSong(
            JSONObject("""{"Id": "song-1", "Name": "Single", "IndexNumber": 4}""")
        )

        assertThat(song.discNumber).isEqualTo(0)
        assertThat(song.trackNumber).isEqualTo(4)
    }
}
