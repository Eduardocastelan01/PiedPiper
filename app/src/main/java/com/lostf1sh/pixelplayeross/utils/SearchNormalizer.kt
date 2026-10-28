package com.lostf1sh.pixelplayeross.utils

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Utility for accent-insensitive (diacritic folding) and fuzzy typo-tolerant matching,
 * enabling Spotify-style search across artists, songs, albums, and playlists.
 */
object SearchNormalizer {

    private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Strips accents/diacritical marks and converts to lowercase.
     * e.g., "José José" -> "jose jose", "Rosalía" -> "rosalia", "Álvaro Díaz" -> "alvaro diaz"
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(DIACRITICS_REGEX, "").replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Splits normalized text into non-empty tokens.
     */
    fun tokenize(text: String): List<String> {
        val norm = normalize(text)
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ").filter { it.isNotEmpty() }
    }

    /**
     * Checks if target text contains the query, ignoring case and accents.
     */
    fun containsNormalized(target: String, query: String): Boolean {
        if (query.isBlank()) return true
        val normTarget = normalize(target)
        val normQuery = normalize(query)
        return normTarget.contains(normQuery)
    }

    /**
     * Calculates fuzzy similarity between two strings in the range [0.0, 1.0].
     * 1.0 means identical, 0.0 means completely different.
     */
    fun similarity(s1: String, s2: String): Float {
        val norm1 = normalize(s1)
        val norm2 = normalize(s2)
        if (norm1.isEmpty() || norm2.isEmpty()) return 0f
        if (norm1 == norm2) return 1.0f

        val distance = levenshteinDistance(norm1, norm2)
        val maxLength = max(norm1.length, norm2.length)
        return (1.0f - (distance.toFloat() / maxLength.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * Comprehensive matching score for search queries against target text (e.g. artist name).
     * Returns a score from 0.0 to 1.0.
     */
    fun matchScore(target: String, query: String): Float {
        val normTarget = normalize(target)
        val normQuery = normalize(query)

        if (normTarget.isEmpty() || normQuery.isEmpty()) return 0f
        if (normTarget == normQuery) return 1.0f
        if (normTarget.startsWith(normQuery)) return 0.95f
        if (normTarget.contains(normQuery)) return 0.88f

        val queryTokens = normQuery.split(" ").filter { it.isNotEmpty() }
        val targetTokens = normTarget.split(" ").filter { it.isNotEmpty() }

        if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0f

        var totalTokenScore = 0f
        for (qToken in queryTokens) {
            var bestScore = 0f
            for (tToken in targetTokens) {
                if (tToken == qToken) {
                    bestScore = max(bestScore, 1.0f)
                } else if (tToken.startsWith(qToken)) {
                    bestScore = max(bestScore, 0.9f)
                } else if (tToken.contains(qToken)) {
                    bestScore = max(bestScore, 0.8f)
                } else {
                    val sim = tokenSimilarity(qToken, tToken)
                    if (sim >= 0.72f) {
                        bestScore = max(bestScore, sim * 0.75f)
                    }
                }
            }
            totalTokenScore += bestScore
        }

        return (totalTokenScore / queryTokens.size).coerceIn(0f, 1f)
    }

    private fun tokenSimilarity(s1: String, s2: String): Float {
        val dist = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1f
        return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

