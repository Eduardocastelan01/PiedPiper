package com.lostf1sh.pixelplayeross.data.gdrive

import android.net.Uri
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamProxy
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveStreamProxy @Inject constructor(
    private val repository: GoogleDriveRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String> = setOf("googleapis.com", "googleusercontent.com")

    override val cacheExpirationMs: Long = 60L * 60 * 1000 // 1 hour

    override val proxyTag: String = "GDriveStreamProxy"
    override val routePath: String = "/gdrive/{fileId}"
    override val routeParamName: String = "fileId"
    override val uriScheme: String = "gdrive"
    override val routePrefix: String = "/gdrive"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateGDriveFileId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(proxyTag).w(e, "Failed to resolve stream URL for Google Drive file $id")
            null
        }
    }

    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    override fun upstreamHeaders(): Map<String, String> =
        repository.getAuthorizationHeader()?.let { mapOf("Authorization" to it) } ?: emptyMap()

    fun resolveGDriveUri(uriString: String): String? = resolveUri(uriString)

    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "gdrive") return
        val fileId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateGDriveFileId(fileId)) return
        try {
            getOrFetchStreamUrl(fileId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(proxyTag).w(e, "warmUpStreamUrl failed for $fileId")
        }
    }
}
