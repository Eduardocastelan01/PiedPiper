package com.lostf1sh.pixelplayeross.data.ai

import com.lostf1sh.pixelplayeross.data.model.Song

/**
 * Request specification for generating an AI-curated playlist
 */
data class PlaylistGenerationRequest(
    val prompt: String,
    val count: Int = 20,
    val availableSongs: List<Song>
)

/**
 * Result of the AI playlist generation
 */
data class AIPlaylistResult(
    val playlistName: String,
    val songs: List<Song>,
    val rationale: String = "",
    val isOfflineFallback: Boolean = false
)
