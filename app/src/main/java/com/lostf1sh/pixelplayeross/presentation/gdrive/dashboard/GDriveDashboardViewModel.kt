package com.lostf1sh.pixelplayeross.presentation.gdrive.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.gdrive.GoogleDriveConfig
import com.lostf1sh.pixelplayeross.data.gdrive.GoogleDriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GDriveDashboardViewModel @Inject constructor(
    private val repository: GoogleDriveRepository,
    private val config: GoogleDriveConfig
) : ViewModel() {

    val isConfigured: StateFlow<Boolean> = repository.isConfiguredFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, repository.isConfigured)

    val lastSyncTimestamp: StateFlow<Long> = repository.lastSyncTimestampFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val lastSyncSongCount: StateFlow<Int> = repository.lastSyncSongCountFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val currentFolderId: StateFlow<String> = repository.folderIdFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val currentFolderName: StateFlow<String> = repository.folderNameFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _oauthTokenInput = MutableStateFlow("")
    val oauthTokenInput: StateFlow<String> = _oauthTokenInput.asStateFlow()

    private val _folderIdInput = MutableStateFlow("")
    val folderIdInput: StateFlow<String> = _folderIdInput.asStateFlow()

    private val _folderNameInput = MutableStateFlow("")
    val folderNameInput: StateFlow<String> = _folderNameInput.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testSuccessMessage = MutableStateFlow<String?>(null)
    val testSuccessMessage: StateFlow<String?> = _testSuccessMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val syncProgress: StateFlow<Pair<Int, Int>?> = _syncProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            config.apiKeyFlow.collect { key ->
                if (_apiKeyInput.value.isEmpty()) _apiKeyInput.value = key
            }
        }
        viewModelScope.launch {
            config.oauthTokenFlow.collect { token ->
                if (_oauthTokenInput.value.isEmpty()) _oauthTokenInput.value = token
            }
        }
        viewModelScope.launch {
            config.folderIdFlow.collect { fid ->
                if (_folderIdInput.value.isEmpty()) _folderIdInput.value = fid
            }
        }
        viewModelScope.launch {
            config.folderNameFlow.collect { fn ->
                if (_folderNameInput.value.isEmpty()) _folderNameInput.value = fn
            }
        }
    }

    fun onApiKeyChanged(value: String) {
        _apiKeyInput.value = value
    }

    fun onOauthTokenChanged(value: String) {
        _oauthTokenInput.value = value
    }

    fun onFolderIdChanged(value: String) {
        _folderIdInput.value = value
    }

    fun onFolderNameChanged(value: String) {
        _folderNameInput.value = value
    }

    fun saveConfig() {
        var apiKey = _apiKeyInput.value.trim()
        var oauthToken = _oauthTokenInput.value.trim()
        val folderId = _folderIdInput.value.trim()
        val folderName = _folderNameInput.value.trim().ifEmpty { "Drive Music" }

        // Auto-detect and swap if user pasted OAuth token into API key field or vice versa
        if (apiKey.startsWith("ya29.") && oauthToken.isBlank()) {
            oauthToken = apiKey
            apiKey = ""
            _oauthTokenInput.value = oauthToken
            _apiKeyInput.value = ""
        } else if (oauthToken.startsWith("AIza") && apiKey.isBlank()) {
            apiKey = oauthToken
            oauthToken = ""
            _apiKeyInput.value = apiKey
            _oauthTokenInput.value = ""
        }

        if (apiKey.isEmpty() && oauthToken.isEmpty()) {
            _errorMessage.value = "Ingresa una Clave de API de Google Cloud o un Token OAuth Bearer"
            return
        }

        repository.saveConfig(
            apiKey = apiKey,
            oauthToken = oauthToken,
            folderId = folderId,
            folderName = folderName
        )
        _statusMessage.value = "Configuración guardada correctamente"
        _errorMessage.value = null
    }

    fun testConnection() {
        saveConfig()
        viewModelScope.launch {
            _isTesting.value = true
            _testSuccessMessage.value = null
            _errorMessage.value = null
            try {
                val result = repository.testConnection()
                result.fold(
                    onSuccess = { count ->
                        _testSuccessMessage.value = "Connected! Found $count audio file(s) in Drive folder."
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Connection test failed: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error testing GDrive connection")
                _errorMessage.value = "Connection error: ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun syncLibrary() {
        saveConfig()
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = null
            _errorMessage.value = null
            _statusMessage.value = "Starting Google Drive sync..."
            try {
                val result = repository.syncAllSongs { scanned, total ->
                    _syncProgress.value = Pair(scanned, total)
                }
                result.fold(
                    onSuccess = { syncedCount ->
                        _statusMessage.value = "Sync complete! $syncedCount songs in your library."
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Sync failed: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error syncing GDrive library")
                _errorMessage.value = "Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
                _syncProgress.value = null
            }
        }
    }

    fun clearSongs() {
        viewModelScope.launch {
            try {
                repository.clearAllSongs()
                _statusMessage.value = "Cleared all Google Drive songs from local library"
            } catch (e: Exception) {
                Timber.e(e, "Error clearing GDrive songs")
                _errorMessage.value = "Failed to clear songs: ${e.message}"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                repository.disconnect()
                _apiKeyInput.value = ""
                _oauthTokenInput.value = ""
                _folderIdInput.value = ""
                _folderNameInput.value = ""
                _statusMessage.value = "Disconnected from Google Drive"
                _errorMessage.value = null
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting GDrive")
                _errorMessage.value = "Disconnect error: ${e.message}"
            }
        }
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
        _testSuccessMessage.value = null
    }
}
