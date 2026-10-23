package com.lostf1sh.pixelplayeross.data.image

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteArtworkCacheTest {

    @TempDir
    lateinit var cacheDir: File

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val subsonicError =
        """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}"""

    @Test
    fun `subsonic error answered with HTTP 200 is not cached as artwork`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val error = runCatching {
            RemoteArtworkCache.download(clientReturning(subsonicError.toByteArray(), "application/json"), request(), target)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(target.exists()).isFalse()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `HTTP error status is not cached even when the body looks like an image`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val error = runCatching {
            RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg", code = 500), request(), target)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `downloaded image replaces the cached file without leaving temp files`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")
        target.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1))

        val result = RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg"), request(), target)

        assertThat(target.readBytes()).isEqualTo(jpegBytes)
        assertThat(cacheDir.listFiles()!!.map { it.name }).containsExactly(target.name)
        assertThat(result.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `PNG cover from a Gonic server is cached and then served from disk`() = runTest {
        val target = File(cacheDir, "navidrome_cover_al-12_500.jpg")

        val result = RemoteArtworkCache.download(clientReturning(pngBytes, "image/png"), request(), target)

        assertThat(result.mimeType).isEqualTo("image/png")
        assertThat(target.readBytes()).isEqualTo(pngBytes)
        assertThat(RemoteArtworkCache.cachedResult(target)).isNotNull()
    }

    @Test
    fun `previously cached error body is not served and the next download replaces it`() = runTest {
        val poisoned = File(cacheDir, "navidrome_cover_al-1_500.jpg").apply { writeText(subsonicError) }

        assertThat(RemoteArtworkCache.cachedResult(poisoned)).isNull()

        RemoteArtworkCache.download(clientReturning(jpegBytes, "image/jpeg"), request(), poisoned)
        assertThat(RemoteArtworkCache.cachedResult(poisoned)).isNotNull()
    }

    @Test
    fun `cached image is served from disk`() = runTest {
        val cached = File(cacheDir, "jellyfin_cover_item_500.jpg").apply { writeBytes(jpegBytes) }

        assertThat(RemoteArtworkCache.cachedResult(cached)).isNotNull()
        assertThat(cached.exists()).isTrue()
    }

    @Test
    fun `cancelling a cover download cancels the HTTP call and caches nothing`() = runTest {
        val server = StalledServer()
        val target = File(cacheDir, "navidrome_cover_al-1_500.jpg")

        val download = launch(Dispatchers.Default) { RemoteArtworkCache.download(server.client, request(), target) }
        assertThat(server.requestStarted.await(5, TimeUnit.SECONDS)).isTrue()

        download.cancel()
        download.join()

        assertThat(server.callCancelled.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(download.isCancelled).isTrue()
        assertThat(cacheDir.listFiles()).isEmpty()
    }

    @Test
    fun `image signatures of formats servers return are recognized`() {
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.ISO_8859_1)
        val avif = "\u0000\u0000\u0000\u001CftypAVIF".toByteArray(Charsets.ISO_8859_1)

        assertThat(RemoteArtworkCache.hasImageSignature(jpegBytes)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(pngBytes)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(webp)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature(avif)).isTrue()
        assertThat(RemoteArtworkCache.hasImageSignature("<?xml version".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray())).isFalse()
        assertThat(RemoteArtworkCache.hasImageSignature(ByteArray(0))).isFalse()
    }

    private fun request() = Request.Builder().url("https://music.example/rest/getCoverArt.view?id=al-1").build()

    private fun clientReturning(body: ByteArray, contentType: String, code: Int = 200) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .header("Content-Type", contentType)
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        }
        .build()
}

/** A server that never answers: the request stays in flight until its call is cancelled. */
internal class StalledServer {
    val requestStarted = CountDownLatch(1)
    val callCancelled = CountDownLatch(1)

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requestStarted.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            if (chain.call().isCanceled()) callCancelled.countDown()
            throw IOException("Canceled")
        }
        .build()
}
