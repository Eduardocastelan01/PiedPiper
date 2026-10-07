package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkDao
import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import com.lostf1sh.pixelplayeross.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio bookmarks, as an array of [AudioBookmarkEntity] (the shape every version reads).
 *
 * Bookmarks name their song by MediaStore id, which differs on another device and can be
 * reused for a different song after a rescan. So each bookmark also carries its song's library
 * metadata under [SONG], and restore points it at this library's id for that song; older
 * versions ignore the extra field. A backup without it is matched on the title and artist the
 * bookmark already stores. The stored artwork uri is device-specific too, so it is refreshed
 * from the matched song.
 */
@Singleton
class AudioBookmarksModuleHandler @Inject constructor(
    private val audioBookmarkDao: AudioBookmarkDao,
    private val songResolver: BackupSongResolver,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.AUDIO_BOOKMARKS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val bookmarks = audioBookmarkDao.getAllBookmarksOnce()
        val metadata = songResolver.metadataFor(bookmarks.map { it.songId })
        val array = JsonArray()
        bookmarks.forEach { bookmark ->
            val obj = gson.toJsonTree(bookmark).asJsonObject
            metadata[bookmark.songId]?.let { obj.add(SONG, gson.toJsonTree(it)) }
            array.add(obj)
        }
        gson.toJson(array)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        audioBookmarkDao.getAllBookmarksOnce().size
    }

    // Rollback restores this device's own rows, so its ids need no matching.
    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        gson.toJson(audioBookmarkDao.getAllBookmarksOnce())
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val entries = JsonParser.parseString(payload).asJsonArray
            .filter { it.isJsonObject }
            .mapNotNull { parseEntry(it.asJsonObject) }
        val library = songResolver.library()

        var dropped = 0
        val remapped = entries.mapNotNull { (bookmark, meta) ->
            val resolvedId = library.resolve(bookmark.songId, meta)
            when {
                resolvedId != null -> bookmark.copy(songId = resolvedId)
                // The id is taken by another song here, and ours wasn't found: keeping it would
                // file the bookmark under the wrong song.
                meta != null && library.namesAnotherSong(bookmark.songId, meta) -> {
                    dropped++
                    null
                }
                // Not in the library (a cloud song, or not scanned yet): keep it. The bookmarks
                // screen relinks it once the song can be matched.
                else -> bookmark
            }
        }
        if (dropped > 0) {
            Timber.tag(TAG).w("Bookmark restore: dropped $dropped whose song id names another song here")
        }

        val songs = songResolver.songsFor(remapped.map { it.songId })
        val withArtwork = remapped.map { bookmark ->
            val song = songs[bookmark.songId] ?: return@map bookmark
            bookmark.copy(albumArtUri = song.albumArtUriString)
        }
        audioBookmarkDao.replaceAll(withArtwork)
    }

    override suspend fun rollback(snapshot: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, AudioBookmarkEntity::class.java).type
        val bookmarks: List<AudioBookmarkEntity> = gson.fromJson(snapshot, type)
        audioBookmarkDao.replaceAll(bookmarks)
    }

    /**
     * One backed-up bookmark and the metadata to match its song with (null when there is none to
     * go on), or null when it names no song. Gson ignores Kotlin nullability and defaults, so a
     * missing field arrives as null in a non-null property and would fail the insert, rolling
     * back the whole restore.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    private fun parseEntry(obj: JsonObject): Pair<AudioBookmarkEntity, BackupSongMetadata?>? {
        val exportedMeta = obj.get(SONG)?.takeIf { it.isJsonObject }
            ?.let { runCatching { gson.fromJson(it, BackupSongMetadata::class.java) }.getOrNull() }
            ?.takeIf { it.title != null && it.artist != null }
            ?.let { it.copy(album = it.album ?: "") }
        obj.remove(SONG)
        val raw = runCatching { gson.fromJson(obj, AudioBookmarkEntity::class.java) }.getOrNull() ?: return null
        val songId = raw.songId?.trim().orEmpty().ifEmpty { return null }

        val bookmark = raw.copy(
            songId = songId,
            songTitle = raw.songTitle ?: "",
            artistName = raw.artistName ?: "",
            title = raw.title ?: "",
            timestampMs = raw.timestampMs.coerceAtLeast(0L)
        )
        val meta = exportedMeta
            ?: BackupSongMetadata(bookmark.songTitle, bookmark.artistName, album = "", duration = 0L)
                .takeIf { bookmark.songTitle.isNotBlank() }
        return bookmark to meta
    }

    private companion object {
        const val TAG = "AudioBookmarksBackup"
        const val SONG = "song"
    }
}
