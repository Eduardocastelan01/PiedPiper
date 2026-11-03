package androidx.media3.session

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.legacy.MediaSessionCompat
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import timber.log.Timber

@OptIn(UnstableApi::class)
object LegacySessionBridge {

    fun getLegacySessionCompat(session: MediaSession): MediaSessionCompat? {
        return runCatching {
            val impl = session.impl
            val stubField = impl.javaClass.getDeclaredField("sessionLegacyStub").apply { isAccessible = true }
            val stub = stubField.get(impl) as? MediaSessionLegacyStub
            stub?.sessionCompat
        }.onFailure {
            Timber.tag("LegacySessionBridge").w(it, "Failed to access sessionLegacyStub")
        }.getOrNull()
    }

    /**
     * Synchronously pushes the current track's metadata (title, artist, album, duration)
     * down to [MediaSessionCompat] so Bluetooth AVRCP, Wear OS, and Galaxy Watch controllers
     * receive instant updates without waiting for asynchronous artwork decoding.
     */
    fun syncMetadata(
        session: MediaSession,
        context: Context,
        mediaItem: MediaItem?,
        artworkBitmap: Bitmap? = null
    ) {
        val compat = getLegacySessionCompat(session) ?: return
        val rawItem = mediaItem ?: session.player.currentMediaItem ?: return

        runCatching {
            val durationMs = session.player.duration.let { if (it > 0) it else C.TIME_UNSET }

            // Ensure any internal custom scheme is mapped to an externally shareable content:// URI
            val rawArtUri = rawItem.mediaMetadata.artworkUri
            val exposedArtUri = rawArtUri?.let {
                MediaItemBuilder.externalControllerArtworkUri(
                    context = context,
                    rawArtworkUri = it.toString()
                )
            } ?: rawArtUri

            val itemMetadata = if (exposedArtUri != null && exposedArtUri != rawArtUri) {
                rawItem.mediaMetadata.buildUpon()
                    .setArtworkUri(exposedArtUri)
                    .build()
            } else {
                rawItem.mediaMetadata
            }

            val metadataCompat = LegacyConversions.convertToMediaMetadataCompat(
                itemMetadata,
                rawItem.mediaId,
                rawItem.requestMetadata.mediaUri,
                durationMs,
                artworkBitmap
            )
            compat.setMetadata(metadataCompat)
            Timber.tag("LegacySessionBridge").d(
                "Synchronously pushed metadata to MediaSessionCompat: title=%s, artist=%s, hasArt=%s",
                itemMetadata.title,
                itemMetadata.artist,
                artworkBitmap != null
            )
        }.onFailure {
            Timber.tag("LegacySessionBridge").e(it, "Failed to synchronously update MediaSessionCompat metadata")
        }
    }
}
