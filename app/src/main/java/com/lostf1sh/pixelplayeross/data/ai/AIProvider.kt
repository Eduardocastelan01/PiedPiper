package com.lostf1sh.pixelplayeross.data.ai

/**
 * Supported AI Providers in Pied Piper
 */
enum class AIProvider(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    GROQ("Groq", "llama-3.3-70b-versatile")
}

sealed class AIException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoKeyConfigured(val provider: AIProvider) :
        AIException("No API key configured for ${provider.displayName}")

    class InvalidKey(val provider: AIProvider) :
        AIException("Invalid API key for ${provider.displayName}. Check your key in Settings.")

    class RateLimited(val provider: AIProvider) :
        AIException("Rate limit reached for ${provider.displayName}. Please try again later.")

    class NetworkError(val provider: AIProvider, cause: Throwable) :
        AIException("Network error connecting to ${provider.displayName}", cause)

    class ParseError(val rawResponse: String) :
        AIException("Could not parse AI playlist response")
}
