package com.lostf1sh.pixelplayeross.data.backup.module

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.isSmartPlaylistSource
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.preferences.PlaylistPreferencesRepository
import com.lostf1sh.pixelplayeross.data.preferences.PreferenceBackupEntry
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncManager
import com.lostf1sh.pixelplayeross.di.AppScope
import com.lostf1sh.pixelplayeross.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Playlists and their ordering preferences.
 *
 * Song ids are matched against the current library on restore (see [BackupSongResolver]). A
 * restore made before the library scan has indexed the songs can't match them yet, so the
 * payload is kept in [PENDING_RESTORE_FILE_NAME] and the restored playlists are filled in after
 * each library sync finishes, until every song resolves.
 */
@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val songResolver: BackupSongResolver,
    @BackupGson private val gson: Gson,
    private val syncManagerProvider: Provider<SyncManager>,
    @AppScope private val appScope: CoroutineScope
) : BackupModuleHandler {

    private val pendingRestoreMutex = Mutex()
    private val pendingRestoreFile: File
        get() = File(context.filesDir, PENDING_RESTORE_FILE_NAME)

    init {
        appScope.launch { retryPendingWhenIdle() }
    }

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        val playlists = allPlaylists.filter { isBackedUpPlaylistSource(it.source) }

        val cloudSongIds = buildCloudSongIdSet()

        val filteredPlaylists = playlists.map { playlist ->
            playlist.copy(songIds = playlist.songIds.filter { id -> id !in cloudSongIds })
        }
        val songMetadata = songResolver.metadataFor(filteredPlaylists.flatMap { it.songIds })

        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .count { isBackedUpPlaylistSource(it.source) }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        playlists + orderModes.size + if (sortOption.isNotBlank()) 1 else 0
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        val payload = PlaylistsBackupPayload(
            playlists = playlistPreferencesRepository.getPlaylistsOnce(),
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        )
        gson.toJson(payload)
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrElse { e ->
            throw IllegalStateException("Playlists payload could not be parsed: ${e.message}", e)
        } ?: throw IllegalStateException("Playlists payload could not be parsed: empty JSON document")

        val backupPlaylists = parsed.playlists.orEmpty()
        val songMetadata = parsed.songMetadata
        val coverImages = parsed.coverImages

        val resolution = if (songMetadata != null && songMetadata.isNotEmpty()) {
            resolvePlaylists(backupPlaylists, songMetadata)
        } else {
            PlaylistsResolution(backupPlaylists, unresolvedCount = 0)
        }
        savePendingRestore(payload, hasUnresolvedSongs = resolution.unresolvedCount > 0)

        val finalPlaylists = if (coverImages != null && coverImages.isNotEmpty()) {
            restoreCoverImages(resolution.playlists, coverImages)
        } else {
            resolution.playlists
        }

        playlistPreferencesRepository.replaceAllPlaylists(finalPlaylists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    /**
     * Retries the pending restore on every distinct idle sync state: the first one at startup,
     * and each one a sync leaves behind. A running -> idle edge isn't required, because a short
     * sync can finish before its running state is ever observed.
     */
    private suspend fun retryPendingWhenIdle() {
        syncManagerProvider.get().syncProgress
            .distinctUntilChanged()
            .filter { !it.isRunning }
            .collect { resolvePendingPlaylists() }
    }

    /**
     * Keeps [payload] for [resolvePendingPlaylists] when some of its songs aren't in the library
     * yet; otherwise drops any payload left from an earlier restore.
     */
    private suspend fun savePendingRestore(payload: String, hasUnresolvedSongs: Boolean) =
        pendingRestoreMutex.withLock {
            val pendingFile = pendingRestoreFile
            if (!hasUnresolvedSongs) {
                if (pendingFile.exists()) pendingFile.delete()
                return@withLock
            }
            try {
                pendingFile.writeText(payload)
                Timber.tag(TAG).i("Saved pending playlist restore for songs not in the library yet")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to save pending playlist restore file")
            }
        }

    /**
     * Re-matches the pending restore payload against the library and grows the restored
     * playlists that gained songs. The pending file is dropped once every song resolves.
     */
    private suspend fun resolvePendingPlaylists() = pendingRestoreMutex.withLock {
        val pendingFile = pendingRestoreFile
        if (!pendingFile.exists()) return@withLock

        val payload = try {
            pendingFile.readText()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read pending playlist restore file")
            return@withLock
        }
        val parsed = runCatching { gson.fromJson(payload, PlaylistsBackupPayload::class.java) }.getOrNull()
        val songMetadata = parsed?.songMetadata
        if (parsed == null || songMetadata.isNullOrEmpty()) {
            pendingFile.delete()
            return@withLock
        }

        val resolution = resolvePlaylists(parsed.playlists.orEmpty(), songMetadata)
        val resolvedById = resolution.playlists.associateBy { it.id }

        var updated = false
        val updatedPlaylists = playlistPreferencesRepository.getPlaylistsOnce().map { current ->
            val resolved = resolvedById[current.id]
            if (resolved != null && current.songIds.size < resolved.songIds.size) {
                updated = true
                current.copy(songIds = resolved.songIds)
            } else {
                current
            }
        }
        if (updated) {
            playlistPreferencesRepository.replaceAllPlaylists(updatedPlaylists)
            Timber.tag(TAG).i(
                "Pending playlist restore: playlists grew, ${resolution.unresolvedCount} songs still unresolved"
            )
        }

        if (resolution.unresolvedCount == 0) {
            pendingFile.delete()
            Timber.tag(TAG).i("Pending playlist restore: all songs resolved")
        }
    }

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read cover image: $path")
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to restore cover image for playlist ${playlist.id}")
                playlist.copy(coverImageUri = null)
            }
        }
    }

    /**
     * Resolves backup song IDs to current device song IDs (see [BackupSongResolver]). Songs
     * without a confident match are left out of the playlist, since keeping their backup ids
     * would risk false matches; [resolvePendingPlaylists] retries them after the next sync.
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, BackupSongMetadata>
    ): PlaylistsResolution {
        val library = songResolver.library()

        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = library.resolve(songId, songMetadata[songId])
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Timber.tag(TAG).w("Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        val resolvedPlaylists = playlists.map { playlist ->
            playlist.copy(songIds = playlist.songIds.mapNotNull { songId -> resolutionCache[songId] })
        }
        return PlaylistsResolution(resolvedPlaylists, unresolvedCount)
    }

    private data class PlaylistsResolution(
        val playlists: List<Playlist>,
        val unresolvedCount: Int
    )

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllNavidromeSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllJellyfinSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    private suspend fun restoreLegacyPreferenceEntries(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, BackupSongMetadata>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val PENDING_RESTORE_FILE_NAME = "pending_playlists_restore.json"

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private fun isBackedUpPlaylistSource(source: String): Boolean =
            source == "LOCAL" || isSmartPlaylistSource(source)

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY
        )
    }
}
