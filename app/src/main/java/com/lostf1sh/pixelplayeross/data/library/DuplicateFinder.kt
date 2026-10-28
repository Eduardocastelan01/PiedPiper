package com.lostf1sh.pixelplayeross.data.library

import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.SearchNormalizer
import java.util.Locale

/**
 * Pure, side-effect-free duplicate-track detection and quality ranking.
 *
 * Uses diacritic folding, metadata stripping (remastered tags, feat., track numbers),
 * and primary artist extraction to detect duplicates that standard strict matching misses.
 * Quality scoring prioritizes lossless codecs (FLAC, WAV, ALAC), higher bitrates,
 * and higher sample rates so that lower quality duplicates can be pruned safely.
 */
object DuplicateFinder {

    const val DEFAULT_DURATION_TOLERANCE_MS = 3_500L

    data class DuplicateGroup(
        val title: String,
        val artist: String,
        val songs: List<Song>,
    ) {
        val bestSong: Song get() = songs.maxWithOrNull(QualityComparator) ?: songs.first()
        val duplicatesToRemove: List<Song> get() = songs.filter { it.id != bestSong.id }
    }

    private val whitespace = Regex("""\s+""")
    private val trackNumberPrefix = Regex("""^\d{1,3}[\s.\-_]+""")
    private val extensionSuffix = Regex("""\.(mp3|flac|m4a|wav|ogg|opus|aac|wma)$""", RegexOption.IGNORE_CASE)
    // Only strip pure remaster or audio tags that represent the exact same recording.
    // NEVER strip Live, Remix, Version, Acoustic, Mix, Bonus, etc. which are different recordings.
    private val remasterTags = Regex("""\s*[\[\(](remaster(ed)?(\s*\d{4})?|official audio|audio)[\]\)]""", RegexOption.IGNORE_CASE)
    private val punctuationRegex = Regex("""[’'`"“”.,\-–—!¡?¿]""")

    fun cleanTitle(title: String): String {
        var t = title.trim()
        t = extensionSuffix.replace(t, "")
        t = trackNumberPrefix.replace(t, "")
        t = remasterTags.replace(t, "")
        t = punctuationRegex.replace(t, " ")
        t = whitespace.replace(t, " ").trim()
        return SearchNormalizer.normalize(t)
    }

    fun cleanArtist(artist: String): String {
        var a = artist.trim()
        a = punctuationRegex.replace(a, " ")
        a = whitespace.replace(a, " ").trim()
        return SearchNormalizer.normalize(a)
    }

    /**
     * Scores a song by audio quality to determine the best track to keep.
     */
    fun qualityScore(song: Song): Long {
        var score = 0L

        val ext = song.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = song.mimeType?.lowercase(Locale.ROOT).orEmpty()

        // Lossless codec bonus
        if (ext in listOf("flac", "wav", "alac", "aiff") || mime.contains("flac") || mime.contains("wav")) {
            score += 2_000_000L
        } else if (ext in listOf("m4a", "aac") || mime.contains("aac") || mime.contains("mp4")) {
            score += 1_000_000L
        } else if (ext in listOf("opus", "ogg") || mime.contains("ogg") || mime.contains("opus")) {
            score += 800_000L
        } else if (ext == "mp3" || mime.contains("mpeg") || mime.contains("mp3")) {
            score += 600_000L
        }

        // Bitrate contribution (e.g. 320 kbps -> 320,000)
        val bitrate = song.bitrate ?: 0
        score += (bitrate * 1000L).coerceAtLeast(0L)

        // Sample rate contribution (e.g. 96000 -> 960)
        val sampleRate = song.sampleRate ?: 0
        score += (sampleRate / 100L).coerceAtLeast(0L)

        return score
    }

    val QualityComparator: Comparator<Song> = Comparator { a, b ->
        val scoreA = qualityScore(a)
        val scoreB = qualityScore(b)
        when {
            scoreA != scoreB -> scoreA.compareTo(scoreB)
            a.dateModified != b.dateModified -> a.dateModified.compareTo(b.dateModified)
            else -> a.id.compareTo(b.id)
        }
    }

    fun findDuplicates(
        songs: List<Song>,
        durationToleranceMs: Long = DEFAULT_DURATION_TOLERANCE_MS,
    ): List<DuplicateGroup> {
        val result = mutableListOf<DuplicateGroup>()

        songs
            .groupBy { cleanTitle(it.title) to cleanArtist(it.artist) }
            .forEach { (key, sameNamed) ->
                val (cTitle, cArtist) = key
                if (cTitle.isBlank() || sameNamed.size < 2) return@forEach

                val sorted = sameNamed.sortedBy { it.duration }
                var cluster = mutableListOf(sorted.first())
                for (i in 1 until sorted.size) {
                    val song = sorted[i]
                    if (song.duration - cluster.first().duration <= durationToleranceMs) {
                        cluster.add(song)
                    } else {
                        result.addClusterIfDuplicate(cluster)
                        cluster = mutableListOf(song)
                    }
                }
                result.addClusterIfDuplicate(cluster)
            }

        return result.sortedWith(
            compareByDescending<DuplicateGroup> { it.songs.size }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
    }

    private fun MutableList<DuplicateGroup>.addClusterIfDuplicate(cluster: List<Song>) {
        if (cluster.size > 1) {
            val head = cluster.first()
            add(DuplicateGroup(title = head.title, artist = head.artist, songs = cluster.toList()))
        }
    }
}
