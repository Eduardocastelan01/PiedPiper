package com.lostf1sh.pixelplayeross.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pied Piper -- Secure Key Store
 *
 * Secure storage for AI API keys (Gemini, OpenAI, Groq) using
 * AndroidX EncryptedSharedPreferences backed by Android Keystore.
 * Falls back safely to private preferences if keystore initialization fails.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "EncryptedSharedPreferences initialization failed, falling back to private prefs")
            context.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveKey(provider: AIProvider, key: String) {
        prefs.edit().putString(keyForProvider(provider), key.trim()).apply()
    }

    fun getKey(provider: AIProvider): String? {
        return prefs.getString(keyForProvider(provider), null)?.takeIf { it.isNotBlank() }
    }

    fun hasKey(provider: AIProvider): Boolean {
        return !getKey(provider).isNullOrBlank()
    }

    fun clearKey(provider: AIProvider) {
        prefs.edit().remove(keyForProvider(provider)).apply()
    }

    fun getActiveProvider(): AIProvider {
        val storedName = prefs.getString(KEY_ACTIVE_PROVIDER, AIProvider.GEMINI.name)
        return try {
            AIProvider.valueOf(storedName ?: AIProvider.GEMINI.name)
        } catch (_: IllegalArgumentException) {
            AIProvider.GEMINI
        }
    }

    fun setActiveProvider(provider: AIProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun isOfflineFallbackEnabled(): Boolean {
        return prefs.getBoolean(KEY_OFFLINE_FALLBACK, true)
    }

    fun setOfflineFallbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_FALLBACK, enabled).apply()
    }

    private fun keyForProvider(provider: AIProvider): String = "api_key_${provider.name.lowercase()}"

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val PREFS_FILE_NAME = "pied_piper_secure_keys"
        private const val FALLBACK_PREFS_FILE_NAME = "pied_piper_keys_fallback"
        private const val KEY_ACTIVE_PROVIDER = "active_ai_provider"
        private const val KEY_OFFLINE_FALLBACK = "offline_fallback_enabled"
    }
}
