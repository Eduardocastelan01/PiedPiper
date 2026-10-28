package com.lostf1sh.pixelplayeross.data.gdrive

import com.lostf1sh.pixelplayeross.data.database.AlbumEntity
import com.lostf1sh.pixelplayeross.data.database.ArtistEntity
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongArtistCrossRef
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class GoogleDriveRepository @Inject constructor(
    private val config: GoogleDriveConfig,
    private val musicDao: MusicDao,
    private val okHttpClient: OkHttpClient
) {
    val account: GoogleDriveAccount
        get() = config.getAccount()

    val isConnected: Boolean
        get() = account.isConnected

    val isConfigured: Boolean
        get() = isConnected

    val isConfiguredFlow: Flow<Boolean> = config.accountFlow.map { it.isConnected }
    val lastSyncTimestampFlow: Flow<Long> = config.accountFlow.map { it.lastSyncTimestamp }
    val lastSyncSongCountFlow: Flow<Int> = config.accountFlow.map { it.syncedTrackCount }
    val folderIdFlow: Flow<String> = config.folderIdFlow
    val folderNameFlow: Flow<String> = config.folderNameFlow

    val serverUrl: String = "https://www.googleapis.com"

    fun getAuthorizationHeader(): String? {
        val token = account.accessToken.trim()
        return if (token.isNotBlank()) "Bearer $token" else null
    }

    fun getStreamUrl(fileId: String): String {
        val apiKey = account.apiKey.trim()
        return if (apiKey.isNotBlank()) {
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$apiKey"
        } else {
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        }
    }

    fun getThumbnailUrl(fileId: String): String {
        return "gdrive_cover://$fileId"
    }

    fun saveConfig(apiKey: String, oauthToken: String, folderId: String, folderName: String) {
        val current = config.getAccount()
        config.saveAccount(
            current.copy(
                apiKey = apiKey,
                accessToken = oauthToken,
                folderId = folderId,
                folderName = folderName
            )
        )
    }

    suspend fun syncAllSongs(onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<Int> {
        return syncGoogleDriveSongs(onProgress)
    }

    suspend fun clearAllSongs() = withContext(Dispatchers.IO) {
        musicDao.clearAllGDriveSongs()
        config.updateSyncStats(0)
    }

    suspend fun testConnection(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val acc = config.getAccount()
            if (!acc.isConnected) {
                return@withContext Result.failure(IllegalStateException("No Google Drive API Key or Access Token configured"))
            }
            if (acc.apiKey.isNotBlank() && acc.accessToken.isBlank() && acc.folderId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Al usar una Clave de API, debes especificar el ID o enlace de la carpeta de Drive pública."))
            }

            val fetchResult = fetchAudioFiles()
            if (fetchResult.isFailure) {
                return@withContext Result.failure(fetchResult.exceptionOrNull() ?: IOException("Connection test failed"))
            }
            Result.success(fetchResult.getOrThrow().size)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "testConnection error")
            Result.failure(e)
        }
    }

    suspend fun fetchAudioFiles(): Result<List<GoogleDriveFile>> = withContext(Dispatchers.IO) {
        val files = mutableListOf<GoogleDriveFile>()
        var pageToken: String? = null
        val acc = config.getAccount()

        try {
            var query = "trashed = false and (mimeType contains 'audio/' or name contains '.mp3' or name contains '.flac' or name contains '.m4a' or name contains '.ogg' or name contains '.wav')"
            val rawFolderId = acc.folderId.trim()
            val cleanFolderId = when {
                rawFolderId.contains("folders/") -> Regex("""folders/([a-zA-Z0-9_-]+)""").find(rawFolderId)?.groupValues?.get(1) ?: rawFolderId
                rawFolderId.contains("id=") -> Regex("""id=([a-zA-Z0-9_-]+)""").find(rawFolderId)?.groupValues?.get(1) ?: rawFolderId
                else -> rawFolderId
            }
            if (cleanFolderId.isNotBlank()) {
                query = "'$cleanFolderId' in parents and $query"
            }

            do {
                val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()?.newBuilder()
                    ?: break

                urlBuilder.addQueryParameter("pageSize", "100")
                urlBuilder.addQueryParameter("q", query)
                urlBuilder.addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,thumbnailLink)")
                urlBuilder.addQueryParameter("supportsAllDrives", "true")
                urlBuilder.addQueryParameter("includeItemsFromAllDrives", "true")
                if (acc.apiKey.isNotBlank()) {
                    urlBuilder.addQueryParameter("key", acc.apiKey)
                }
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }

                val requestBuilder = Request.Builder().url(urlBuilder.build())
                val authHeader = getAuthorizationHeader()
                if (authHeader != null) {
                    requestBuilder.header("Authorization", authHeader)
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    val errorMsg = runCatching {
                        val errObj = JSONObject(body).getJSONObject("error")
                        val code = errObj.optInt("code", response.code)
                        val message = errObj.optString("message", "HTTP $code")
                        when {
                            code == 401 || message.contains("authentication credential", ignoreCase = true) || message.contains("invalid_token", ignoreCase = true) ->
                                "Google Drive requiere un Token de Acceso OAuth válido o una carpeta pública para acceder."
                            code == 403 && (message.contains("API keys are not supported", ignoreCase = true) || message.contains("has not been used", ignoreCase = true)) ->
                                "La API de Google Drive no está habilitada en este proyecto o requiere OAuth en vez de API Key."
                            code == 404 -> "Carpeta de Google Drive no encontrada. Revisa el ID de la carpeta."
                            else -> message
                        }
                    }.getOrDefault("HTTP ${response.code}")
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val jsonStr = response.body.string()
                val json = JSONObject(jsonStr)
                val filesArray = json.optJSONArray("files")
                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val item = filesArray.getJSONObject(i)
                        val id = item.optString("id")
                        val name = item.optString("name")
                        val mimeType = item.optString("mimeType", "audio/mpeg")
                        val size = item.optLong("size", 0L)
                        val thumbnail = item.optString("thumbnailLink").takeIf { it.isNotBlank() }
                        if (id.isNotBlank() && name.isNotBlank()) {
                            files.add(
                                GoogleDriveFile(
                                    id = id,
                                    name = name,
                                    size = size,
                                    mimeType = mimeType,
                                    thumbnailLink = thumbnail
                                )
                            )
                        }
                    }
                }

                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null && files.size < 1000)

            Result.success(files)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "fetchAudioFiles error")
            Result.failure(e)
        }
    }

    suspend fun syncGoogleDriveSongs(onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val fetchResult = fetchAudioFiles()
            if (fetchResult.isFailure) {
                return@withContext Result.failure(fetchResult.exceptionOrNull() ?: Exception("Failed fetching files"))
            }

            val driveFiles = fetchResult.getOrThrow()
            val existingUnifiedIds = musicDao.getAllGDriveSongIds()

            if (driveFiles.isEmpty()) {
                if (existingUnifiedIds.isNotEmpty()) {
                    musicDao.clearAllGDriveSongs()
                }
                config.updateSyncStats(0)
                return@withContext Result.success(0)
            }

            val songs = ArrayList<SongEntity>(driveFiles.size)
            val artists = LinkedHashMap<Long, ArtistEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()

            val defaultAlbumName = account.folderName.ifBlank { "Google Drive" }
            val albumId = toUnifiedAlbumId(defaultAlbumName)

            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = defaultAlbumName,
                    artistName = "Google Drive",
                    artistId = toUnifiedArtistId("Google Drive"),
                    songCount = driveFiles.size,
                    dateAdded = System.currentTimeMillis(),
                    year = 2024,
                    albumArtUriString = driveFiles.firstOrNull()?.thumbnailLink ?: "gdrive_cover://${driveFiles.first().id}",
                    albumArtist = "Google Drive"
                )
            )

            driveFiles.forEachIndexed { index, file ->
                onProgress(index + 1, driveFiles.size)
                val (artistName, title) = file.parsedArtistAndTitle
                val songId = toUnifiedSongId(file.id)
                val artistId = toUnifiedArtistId(artistName)

                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(
                        id = artistId,
                        name = artistName,
                        trackCount = 0,
                        imageUrl = null
                    )
                )

                crossRefs.add(
                    SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        isPrimary = true
                    )
                )

                val contentUri = "gdrive://${file.id}"
                val albumArtUri = file.thumbnailLink ?: "gdrive_cover://${file.id}"

                songs.add(
                    SongEntity(
                        id = songId,
                        title = title,
                        artistName = artistName,
                        artistId = artistId,
                        albumArtist = "Google Drive",
                        albumArtistId = toUnifiedArtistId("Google Drive"),
                        albumName = defaultAlbumName,
                        albumId = albumId,
                        contentUriString = contentUri,
                        albumArtUriString = albumArtUri,
                        duration = 180_000L, // Default estimated 3m duration until decoded
                        genre = "Cloud",
                        filePath = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
                        parentDirectoryPath = "Google Drive/$defaultAlbumName",
                        isFavorite = false,
                        lyrics = null,
                        trackNumber = index + 1,
                        discNumber = 1,
                        year = 0,
                        dateAdded = System.currentTimeMillis(),
                        mimeType = file.mimeType,
                        bitrate = 320_000,
                        sampleRate = 44_100,
                        sourceType = SourceType.GDRIVE
                    )
                )
            }

            val currentUnifiedIds = songs.map { it.id }.toSet()
            val deletedUnifiedIds = existingUnifiedIds.filter { it !in currentUnifiedIds }

            musicDao.incrementalSyncMusicData(
                songs = songs,
                albums = albums.values.toList(),
                artists = artists.values.toList(),
                crossRefs = crossRefs,
                deletedSongIds = deletedUnifiedIds
            )

            config.updateSyncStats(songs.size)
            Timber.tag(TAG).d("Synced ${songs.size} Google Drive tracks into Pied Piper")
            Result.success(songs.size)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "syncGoogleDriveSongs error")
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        musicDao.clearAllGDriveSongs()
        config.clear()
    }

    private fun toUnifiedSongId(fileId: String): Long {
        return -(GDRIVE_SONG_ID_OFFSET + fileId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedArtistId(name: String): Long {
        return -(GDRIVE_ARTIST_ID_OFFSET + name.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(name: String): Long {
        return -(GDRIVE_ALBUM_ID_OFFSET + name.hashCode().toLong().absoluteValue)
    }

    companion object {
        private const val TAG = "GoogleDriveRepo"
        private const val GDRIVE_SONG_ID_OFFSET = 300_000_000_000L
        private const val GDRIVE_ALBUM_ID_OFFSET = 330_000_000_000L
        private const val GDRIVE_ARTIST_ID_OFFSET = 360_000_000_000L
    }
}
