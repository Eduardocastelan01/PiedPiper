package com.lostf1sh.pixelplayeross.data.gdrive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleDriveAccount(
    val apiKey: String = "",
    val accessToken: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val accountEmail: String = "",
    val lastSyncTimestamp: Long = 0L,
    val syncedTrackCount: Int = 0
) {
    val isConnected: Boolean
        get() = apiKey.isNotBlank() || accessToken.isNotBlank()
}

@Singleton
class GoogleDriveConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccount(): GoogleDriveAccount {
        return GoogleDriveAccount(
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
            folderId = prefs.getString(KEY_FOLDER_ID, "").orEmpty(),
            folderName = prefs.getString(KEY_FOLDER_NAME, "").orEmpty(),
            accountEmail = prefs.getString(KEY_ACCOUNT_EMAIL, "").orEmpty(),
            lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC, 0L),
            syncedTrackCount = prefs.getInt(KEY_SYNCED_COUNT, 0)
        )
    }

    fun saveAccount(account: GoogleDriveAccount) {
        prefs.edit()
            .putString(KEY_API_KEY, account.apiKey.trim())
            .putString(KEY_ACCESS_TOKEN, account.accessToken.trim())
            .putString(KEY_FOLDER_ID, account.folderId.trim())
            .putString(KEY_FOLDER_NAME, account.folderName.trim())
            .putString(KEY_ACCOUNT_EMAIL, account.accountEmail.trim())
            .putLong(KEY_LAST_SYNC, account.lastSyncTimestamp)
            .putInt(KEY_SYNCED_COUNT, account.syncedTrackCount)
            .apply()
    }

    fun updateSyncStats(count: Int, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(KEY_SYNCED_COUNT, count)
            .putLong(KEY_LAST_SYNC, timestamp)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "pied_piper_gdrive_config"
        private const val KEY_API_KEY = "gdrive_api_key"
        private const val KEY_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_FOLDER_ID = "gdrive_folder_id"
        private const val KEY_FOLDER_NAME = "gdrive_folder_name"
        private const val KEY_ACCOUNT_EMAIL = "gdrive_account_email"
        private const val KEY_LAST_SYNC = "gdrive_last_sync"
        private const val KEY_SYNCED_COUNT = "gdrive_synced_count"
    }
}
