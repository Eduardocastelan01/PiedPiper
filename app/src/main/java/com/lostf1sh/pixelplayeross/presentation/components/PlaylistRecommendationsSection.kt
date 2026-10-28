package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.GenreNormalizer
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Spotify-style Recommended Songs Section displayed at the bottom of playlists.
 * Recommends library tracks that match the playlist's artists and genres,
 * with a 1-tap addition button and a refresh control to discover more.
 */
@Composable
fun PlaylistRecommendationsSection(
    playlistSongs: List<Song>,
    allSongs: List<Song>,
    onAddSong: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (allSongs.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var refreshSeed by remember { mutableIntStateOf(0) }
    val addedSongsMap = remember { mutableStateMapOf<String, Boolean>() }

    val recommendedCandidates by remember(playlistSongs, allSongs) {
        derivedStateOf {
            val existingIds = playlistSongs.map { it.id }.toSet()
            val candidatePool = allSongs.filterNot { it.id in existingIds }
            if (candidatePool.isEmpty()) return@derivedStateOf emptyList()

            if (playlistSongs.isEmpty()) {
                // If playlist is empty, recommend tracks from diverse artists in the library
                val byArtist = candidatePool.groupBy { it.artist.lowercase().trim() }
                return@derivedStateOf byArtist.values.mapNotNull { it.shuffled().firstOrNull() }.shuffled()
            }

            // Frequency maps for playlist artists, specific genres, and macro styles/moods
            val specificGenreFrequencies = mutableMapOf<String, Int>()
            val macroGenreFrequencies = mutableMapOf<String, Int>()
            val playlistArtists = mutableSetOf<String>()

            playlistSongs.forEach { s ->
                val normArtist = s.artist.lowercase().trim()
                if (normArtist.isNotBlank()) {
                    playlistArtists.add(normArtist)
                }
                val genres = GenreNormalizer.splitAndNormalize(s.genre, s.artist, s.title)
                genres.forEach { g ->
                    val normG = g.lowercase().trim()
                    if (normG.isNotBlank()) {
                        specificGenreFrequencies[normG] = (specificGenreFrequencies[normG] ?: 0) + 1
                        val macro = GenreNormalizer.toMacroCategory(normG).lowercase().trim()
                        if (macro.isNotBlank() && macro != "other" && macro != "unknown") {
                            macroGenreFrequencies[macro] = (macroGenreFrequencies[macro] ?: 0) + 1
                        }
                    }
                }
            }

            // Score each candidate track based on mood & genre coherence
            val scored = candidatePool.map { candidate ->
                var score = 0
                val candidateArtist = candidate.artist.lowercase().trim()
                val isSameArtist = candidateArtist in playlistArtists

                val candidateGenres = GenreNormalizer.splitAndNormalize(candidate.genre, candidate.artist, candidate.title)
                var genreMatchScore = 0
                candidateGenres.forEach { cg ->
                    val normG = cg.lowercase().trim()
                    val specificHits = specificGenreFrequencies[normG] ?: 0
                    if (specificHits > 0) {
                        genreMatchScore += specificHits * 20
                    }
                    val macro = GenreNormalizer.toMacroCategory(normG).lowercase().trim()
                    val macroHits = macroGenreFrequencies[macro] ?: 0
                    if (macroHits > 0) {
                        genreMatchScore += macroHits * 10
                    }
                }
                score += genreMatchScore

                // Moderate bonus for familiar artist, never overpowering mood/genre
                if (isSameArtist) {
                    score += 6
                }

                // If playlist has clear genres and this candidate doesn't match the style, drop score
                if (macroGenreFrequencies.isNotEmpty() && genreMatchScore == 0 && !isSameArtist) {
                    score = 0
                }

                candidate to score
            }

            // ARTIST DIVERSITY ENFORCEMENT (Spotify-style):
            // Group candidate matches by artist so that no single artist monopolizes the recommendations.
            // Interleave artist tracks: 1 song per artist in round 0, 1 song per artist in round 1.
            val matched = scored.filter { it.second > 0 }.sortedByDescending { it.second }
            val byArtist = linkedMapOf<String, MutableList<Pair<Song, Int>>>()
            for ((candidate, score) in matched) {
                val artistKey = candidate.artist.lowercase().trim()
                byArtist.getOrPut(artistKey) { mutableListOf() }.add(candidate to score)
            }

            val diversePrioritized = mutableListOf<Song>()
            val maxRounds = 2
            for (round in 0 until maxRounds) {
                for ((_, songList) in byArtist) {
                    if (round < songList.size) {
                        diversePrioritized.add(songList[round].first)
                    }
                }
            }

            // Diverse fallback from varied artists if match pool is small
            val unmatchedPool = scored.filter { it.second <= 0 }.map { it.first }
            val unmatchedDiverse = unmatchedPool.groupBy { it.artist.lowercase().trim() }
                .values.mapNotNull { it.shuffled().firstOrNull() }.shuffled()

            if (diversePrioritized.size >= 18) {
                diversePrioritized
            } else {
                diversePrioritized + unmatchedDiverse
            }
        }
    }

    val displayedRecommendations by remember(recommendedCandidates, refreshSeed) {
        derivedStateOf {
            if (recommendedCandidates.isEmpty()) emptyList()
            else {
                val chunkSize = 6
                val offset = (refreshSeed * chunkSize) % maxOf(1, recommendedCandidates.size)
                val window = recommendedCandidates.drop(offset).take(chunkSize)
                if (window.size < chunkSize && recommendedCandidates.size > chunkSize) {
                    window + recommendedCandidates.take(chunkSize - window.size)
                } else {
                    window.ifEmpty { recommendedCandidates.take(chunkSize) }
                }
            }
        }
    }

    if (displayedRecommendations.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (playlistSongs.isEmpty()) "Canciones para tu lista" else "Canciones recomendadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (playlistSongs.isEmpty()) "Agrega pistas para comenzar a armar tu lista" else "Basadas en las canciones de esta lista de reproducción",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    refreshSeed++
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refrescar",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Refrescar",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // List of recommended tracks
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayedRecommendations.forEach { song ->
                val isAdded = addedSongsMap[song.id] == true
                RecommendedSongRow(
                    song = song,
                    isAdded = isAdded,
                    onAddClick = {
                        if (!isAdded) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            addedSongsMap[song.id] = true
                            onAddSong(song)
                        }
                    },
                    onRowClick = { onPlaySong(song) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedSongRow(
    song: Song,
    isAdded: Boolean,
    onAddClick: () -> Unit,
    onRowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AbsoluteSmoothCornerShape(16.dp, 60)
    val buttonColor by animateColorAsState(
        targetValue = if (isAdded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "btnColor"
    )
    val buttonContentColor by animateColorAsState(
        targetValue = if (isAdded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        label = "btnContentColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onRowClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    shape = RoundedCornerShape(10.dp),
                    targetSize = Size(168, 168),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = onAddClick,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = buttonColor,
                    contentColor = buttonContentColor
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (isAdded) "Agregada" else "Agregar a la lista",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
