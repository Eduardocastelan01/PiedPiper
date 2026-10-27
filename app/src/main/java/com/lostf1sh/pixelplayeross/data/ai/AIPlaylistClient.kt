package com.lostf1sh.pixelplayeross.data.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pied Piper -- Robust Multi-Provider AI Playlist Client
 *
 * Supports Gemini, OpenAI, and Groq with structured JSON output,
 * error handling (401 invalid key, 429 rate limit, network failure)
 * and intelligent offline heuristic fallback.
 */
@Singleton
class AIPlaylistClient @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val gson: Gson
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Generates a curated playlist based on user request prompt and the device's library.
     */
    suspend fun generatePlaylist(
        request: PlaylistGenerationRequest,
        providerOverride: AIProvider? = null
    ): Result<AIPlaylistResult> = withContext(Dispatchers.IO) {
        val provider = providerOverride ?: keyStore.getActiveProvider()
        val apiKey = keyStore.getKey(provider)

        if (apiKey.isNullOrBlank()) {
            if (keyStore.isOfflineFallbackEnabled()) {
                Timber.tag(TAG).i("No API key configured for %s; using offline heuristic fallback", provider.displayName)
                return@withContext Result.success(generateOfflineHeuristicPlaylist(request))
            }
            return@withContext Result.failure(AIException.NoKeyConfigured(provider))
        }

        try {
            val structuredPrompt = buildStructuredPrompt(request)
            val jsonResponse = when (provider) {
                AIProvider.GEMINI -> callGemini(apiKey, structuredPrompt)
                AIProvider.OPENAI -> callOpenAI(apiKey, structuredPrompt)
                AIProvider.GROQ -> callGroq(apiKey, structuredPrompt)
            }

            val parsed = parsePlaylistResponse(jsonResponse, request.availableSongs)
            Result.success(parsed)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "AI playlist generation failed for %s", provider.displayName)
            if (keyStore.isOfflineFallbackEnabled() && shouldFallback(e)) {
                Timber.tag(TAG).i("Falling back to local heuristic playlist generation")
                Result.success(generateOfflineHeuristicPlaylist(request))
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Validates an API key with a fast, lightweight ping to the provider.
     */
    suspend fun validateKey(provider: AIProvider, key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.success(false)
        try {
            val pingPrompt = "Respond with JSON: {\"status\": \"ok\"}"
            when (provider) {
                AIProvider.GEMINI -> callGemini(key, pingPrompt)
                AIProvider.OPENAI -> callOpenAI(key, pingPrompt, maxTokens = 15)
                AIProvider.GROQ -> callGroq(key, pingPrompt, maxTokens = 15)
            }
            Result.success(true)
        } catch (e: AIException.InvalidKey) {
            Result.success(false)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Key validation error")
            Result.failure(e)
        }
    }

    private fun callGemini(apiKey: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val bodyJson = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", prompt) })
                    })
                })
            }
            add("contents", contentsArray)
            add("generationConfig", JsonObject().apply {
                addProperty("responseMimeType", "application/json")
            })
        }

        val httpRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(httpRequest, AIProvider.GEMINI) { responseBody ->
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates")
            val firstCandidate = candidates?.firstOrNull()?.asJsonObject
            val content = firstCandidate?.getAsJsonObject("content")
            val parts = content?.getAsJsonArray("parts")
            parts?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: responseBody
        }
    }

    private fun callOpenAI(apiKey: String, prompt: String, maxTokens: Int = 1500): String {
        val url = "https://api.openai.com/v1/chat/completions"
        val bodyJson = JsonObject().apply {
            addProperty("model", AIProvider.OPENAI.defaultModel)
            addProperty("max_tokens", maxTokens)
            add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
        }

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(httpRequest, AIProvider.OPENAI) { responseBody ->
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            val firstChoice = choices?.firstOrNull()?.asJsonObject
            val message = firstChoice?.getAsJsonObject("message")
            message?.get("content")?.asString ?: responseBody
        }
    }

    private fun callGroq(apiKey: String, prompt: String, maxTokens: Int = 1500): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val bodyJson = JsonObject().apply {
            addProperty("model", AIProvider.GROQ.defaultModel)
            addProperty("max_tokens", maxTokens)
            add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
        }

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeRequest(httpRequest, AIProvider.GROQ) { responseBody ->
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            val firstChoice = choices?.firstOrNull()?.asJsonObject
            val message = firstChoice?.getAsJsonObject("message")
            message?.get("content")?.asString ?: responseBody
        }
    }

    private fun executeRequest(
        request: Request,
        provider: AIProvider,
        extractor: (String) -> String
    ): String {
        return try {
            val response = httpClient.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string().orEmpty()

            when (code) {
                200 -> extractor(bodyString)
                401, 403 -> throw AIException.InvalidKey(provider)
                429 -> throw AIException.RateLimited(provider)
                else -> throw IOException("HTTP $code from ${provider.displayName}: $bodyString")
            }
        } catch (e: AIException) {
            throw e
        } catch (e: Exception) {
            throw AIException.NetworkError(provider, e)
        }
    }

    private fun buildStructuredPrompt(request: PlaylistGenerationRequest): String {
        // Sample library tracks to fit token budgets (up to 300 tracks)
        val sampled = request.availableSongs.take(300)
        val catalog = sampled.joinToString("\n") { s ->
            "${s.id}|${s.title}|${s.artist}|${s.genre.orEmpty()}"
        }

        return """
            You are an expert music curator and DJ for the player app Pied Piper.
            Create a cohesive playlist of up to ${request.count} songs from the catalog matching this request:
            "${request.prompt}"

            Catalog format (ID|Title|Artist|Genre):
            $catalog

            Rules:
            1. ONLY pick songs that exist in the catalog above. Use their exact IDs.
            2. Return ONLY a valid JSON object with no markdown formatting.
            
            JSON Schema:
            {
              "playlist_name": "A creative title for this playlist",
              "reason": "Brief 1-sentence curation rationale",
              "song_ids": ["id1", "id2", "id3"]
            }
        """.trimIndent()
    }

    private fun parsePlaylistResponse(
        rawJson: String,
        availableSongs: List<Song>
    ): AIPlaylistResult {
        val cleanJson = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val root = gson.fromJson(cleanJson, JsonObject::class.java)
            val name = root.get("playlist_name")?.asString ?: "AI Mix"
            val reason = root.get("reason")?.asString ?: ""
            val songIdsArray = root.getAsJsonArray("song_ids")

            val idSet = songIdsArray?.mapNotNull { it.asString }?.toSet() ?: emptySet()
            val songMap = availableSongs.associateBy { it.id }
            val matchedSongs = idSet.mapNotNull { songMap[it] }

            if (matchedSongs.isEmpty()) {
                throw AIException.ParseError("AI did not return any matching songs from catalog")
            }

            AIPlaylistResult(
                playlistName = name,
                songs = matchedSongs,
                rationale = reason,
                isOfflineFallback = false
            )
        } catch (e: Exception) {
            if (e is AIException) throw e
            throw AIException.ParseError(rawJson)
        }
    }

    /**
     * Smart local heuristic playlist generation when AI or network is unavailable.
     * Matches keywords from the user prompt against track titles, artists, and genres.
     */
    fun generateOfflineHeuristicPlaylist(request: PlaylistGenerationRequest): AIPlaylistResult {
        val keywords = request.prompt.lowercase()
            .split(Regex("""[\s,;.-]+"""))
            .filter { it.length > 2 && it !in STOP_WORDS }

        val scored = request.availableSongs.map { song ->
            var score = 0
            val titleLower = song.title.lowercase()
            val artistLower = song.artist.lowercase()
            val genreLower = song.genre?.lowercase().orEmpty()

            for (kw in keywords) {
                if (genreLower.contains(kw)) score += 5
                if (artistLower.contains(kw)) score += 4
                if (titleLower.contains(kw)) score += 3
            }
            song to score
        }

        val topMatches = scored.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(request.count)

        val finalSongs = if (topMatches.isNotEmpty()) {
            topMatches
        } else {
            // If no keyword matched, return random sample
            request.availableSongs.shuffled().take(request.count)
        }

        val cleanTitle = request.prompt.replaceFirstChar { it.titlecase() }
        return AIPlaylistResult(
            playlistName = "$cleanTitle Mix",
            songs = finalSongs,
            rationale = "Generated with local smart matching for: \"${request.prompt}\"",
            isOfflineFallback = true
        )
    }

    private fun shouldFallback(e: Exception): Boolean = when (e) {
        is AIException.RateLimited, is AIException.NetworkError, is AIException.ParseError, is IOException -> true
        else -> false
    }

    companion object {
        private const val TAG = "AIPlaylistClient"
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "music", "songs", "playlist",
            "mix", "track", "tracks", "play", "make", "create", "some",
            "give", "want", "like", "vibes", "vibe", "una", "para", "de", "con"
        )
    }
}
