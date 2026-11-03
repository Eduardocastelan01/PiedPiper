package com.lostf1sh.pixelplayeross.data.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Arrays

@OptIn(UnstableApi::class)
class CoilBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {

    companion object {
        // Scaled to 256px to prevent Bluetooth LE buffer saturation and Binder IPC choke
        // when syncing metadata with smartwatches (e.g., Samsung Galaxy Watch 7 via AVRCP/Wear OS)
        private const val MAX_MEDIA_SESSION_ARTWORK_SIZE_PX = 256
        private const val BITMAP_CACHE_SIZE = 32
    }

    private val bitmapCache = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)

    private fun getCacheKey(data: Any): String {
        return when (data) {
            is Uri -> data.toString()
            is ByteArray -> "bytes_" + Arrays.hashCode(data)
            else -> data.toString()
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return loadBitmapInternal(uri)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return loadBitmapInternal(data)
    }

    fun prefetch(uri: Uri) {
        val cacheKey = getCacheKey(uri)
        val cached = synchronized(bitmapCache) { bitmapCache.get(cacheKey) }
        if (cached != null && !cached.isRecycled) {
            return
        }
        loadBitmapInternal(uri)
    }

    private fun loadBitmapInternal(data: Any): ListenableFuture<Bitmap> {
        val cacheKey = getCacheKey(data)
        val cached = synchronized(bitmapCache) { bitmapCache.get(cacheKey) }
        if (cached != null && !cached.isRecycled) {
            return Futures.immediateFuture(cached)
        }

        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(MAX_MEDIA_SESSION_ARTWORK_SIZE_PX, MAX_MEDIA_SESSION_ARTWORK_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()

                val result = context.imageLoader.execute(request)
                val drawable = result.drawable

                if (drawable != null) {
                    val bitmap = drawable.toBitmap()
                    synchronized(bitmapCache) {
                        bitmapCache.put(cacheKey, bitmap)
                    }
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Coil returned null drawable for data: $data"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return true
    }
}
