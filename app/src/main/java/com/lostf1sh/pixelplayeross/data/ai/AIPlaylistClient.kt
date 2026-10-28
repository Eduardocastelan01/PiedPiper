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

    @Volatile
    private var cachedGeminiModel: String? = null

    /**
     * Resolves an active Gemini model supporting generateContent for the given key.
     * Queries Google's ModelService.ListModels (GET /v1beta/models) dynamically.
     */
    fun resolveActiveGeminiModel(apiKey: String): String {
        cachedGeminiModel?.let { return it }

        val cleanKey = apiKey.trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", cleanKey)
            .get()
            .build()

        try {
            val responseBody = executeRequest(request, AIProvider.GEMINI) { it }
            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val modelsArray = root.getAsJsonArray("models")
            if (modelsArray != null) {
                val availableModels = mutableListOf<String>()
                for (elem in modelsArray) {
                    val obj = elem.asJsonObject
                    val name = obj.get("name")?.asString.orEmpty().removePrefix("models/")
                    val methods = obj.getAsJsonArray("supportedGenerationMethods")?.mapNotNull { it.asString }.orEmpty()
                    if (methods.contains("generateContent")) {
                        availableModels.add(name)
                    }
                }

                // Prioritize models: modern Flash models first
                val bestModel = availableModels.firstOrNull { it.contains("3.5-flash-lite", ignoreCase = true) }
                    ?: availableModels.firstOrNull { it.contains("3.6-flash", ignoreCase = true) }
                    ?: availableModels.firstOrNull { it.contains("3.8-flash", ignoreCase = true) }
                    ?: availableModels.firstOrNull { it.contains("2.5-flash-lite", ignoreCase = true) }
                    ?: availableModels.firstOrNull { it.contains("2.5-flash", ignoreCase = true) }
                    ?: availableModels.firstOrNull { it.contains("flash", ignoreCase = true) }
                    ?: availableModels.firstOrNull()

                if (bestModel != null) {
                    cachedGeminiModel = bestModel
                    Timber.tag(TAG).i("Discovered active Gemini model: %s", bestModel)
                    return bestModel
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Dynamic Gemini model resolution failed; using modern fallbacks")
            if (e is AIException.InvalidKey || e is AIException.RateLimited) throw e
        }

        return "gemini-2.5-flash"
    }

    /**
     * Validates an API key with a fast, lightweight ping to the provider.
     */
    suspend fun validateKey(provider: AIProvider, key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) return@withContext Result.failure(AIException.NoKeyConfigured(provider))
        try {
            when (provider) {
                AIProvider.GEMINI -> {
                    // Validates directly with Google's ListModels endpoint
                    resolveActiveGeminiModel(cleanKey)
                }
                AIProvider.OPENAI -> {
                    val pingPrompt = "Respond with JSON: {\"status\": \"ok\"}"
                    callOpenAI(cleanKey, pingPrompt, maxTokens = 15)
                }
                AIProvider.GROQ -> {
                    val pingPrompt = "Respond with JSON: {\"status\": \"ok\"}"
                    callGroq(cleanKey, pingPrompt, maxTokens = 15)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Key validation error")
            Result.failure(e)
        }
    }

    private val geminiCandidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemini-3.8-flash",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    )

    private fun callGemini(apiKey: String, prompt: String): String {
        val cleanKey = apiKey.trim()
        val resolvedModel = runCatching { resolveActiveGeminiModel(cleanKey) }.getOrNull()
        val candidateModels = if (!resolvedModel.isNullOrBlank()) {
            listOf(resolvedModel) + geminiCandidateModels.filter { it != resolvedModel }
        } else {
            geminiCandidateModels
        }

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

        var lastException: Exception? = null

        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
            val httpRequest = Request.Builder()
                .url(url)
                .header("x-goog-api-key", cleanKey)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                return executeRequest(httpRequest, AIProvider.GEMINI) { responseBody ->
                    val root = gson.fromJson(responseBody, JsonObject::class.java)
                    val candidates = root.getAsJsonArray("candidates")
                    val firstCandidate = candidates?.firstOrNull()?.asJsonObject
                    val content = firstCandidate?.getAsJsonObject("content")
                    val parts = content?.getAsJsonArray("parts")
                    parts?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: responseBody
                }
            } catch (e: AIException.ApiError) {
                if (e.errorDetails.contains("not found", ignoreCase = true) ||
                    e.errorDetails.contains("not supported", ignoreCase = true) ||
                    e.errorDetails.contains("restricted", ignoreCase = true)
                ) {
                    lastException = e
                    continue
                }
                throw e
            } catch (e: Exception) {
                lastException = e
                if (e is AIException.InvalidKey || e is AIException.RateLimited) {
                    throw e
                }
            }
        }

        throw lastException ?: AIException.ApiError(AIProvider.GEMINI, "Failed to connect with any supported Gemini model")
    }

    private fun callOpenAI(apiKey: String, prompt: String, maxTokens: Int = 1500): String {
        val cleanKey = apiKey.trim()
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
            .header("Authorization", "Bearer $cleanKey")
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
        val cleanKey = apiKey.trim()
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
            .header("Authorization", "Bearer $cleanKey")
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
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw AIException.NetworkError(provider, e)
        }

        val code = response.code
        val bodyString = response.body?.string().orEmpty()

        if (code == 200) {
            return extractor(bodyString)
        }

        // Try extracting specific error message from provider's JSON response
        val parsedErrorMessage = runCatching {
            val root = gson.fromJson(bodyString, JsonObject::class.java)
            when (provider) {
                AIProvider.GEMINI -> {
                    val err = root.getAsJsonObject("error")
                    err?.get("message")?.asString ?: err?.get("status")?.asString
                }
                AIProvider.OPENAI, AIProvider.GROQ -> {
                    val err = root.getAsJsonObject("error")
                    err?.get("message")?.asString
                }
            }
        }.getOrNull()

        when (code) {
            400 -> {
                if (parsedErrorMessage?.contains("API_KEY_INVALID", ignoreCase = true) == true ||
                    parsedErrorMessage?.contains("API key not valid", ignoreCase = true) == true ||
                    bodyString.contains("API_KEY_INVALID", ignoreCase = true) ||
                    bodyString.contains("API key not valid", ignoreCase = true)
                ) {
                    throw AIException.InvalidKey(provider, parsedErrorMessage ?: "Clave de API no válida para ${provider.displayName}.")
                }
                val details = parsedErrorMessage ?: "Solicitud incorrecta (HTTP 400): $bodyString"
                throw AIException.ApiError(provider, details)
            }
            401, 403 -> {
                val details = parsedErrorMessage ?: "Clave no autorizada o sin permisos (HTTP $code)"
                throw AIException.InvalidKey(provider, "${provider.displayName}: $details")
            }
            429 -> throw AIException.RateLimited(provider)
            else -> {
                val details = parsedErrorMessage ?: "HTTP $code: $bodyString"
                throw AIException.ApiError(provider, details)
            }
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
