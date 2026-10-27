package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.ai.AIPlaylistClient
import com.lostf1sh.pixelplayeross.data.ai.AIPlaylistResult
import com.lostf1sh.pixelplayeross.data.ai.AIProvider
import com.lostf1sh.pixelplayeross.data.ai.PlaylistGenerationRequest
import com.lostf1sh.pixelplayeross.data.ai.SecureKeyStore
import com.lostf1sh.pixelplayeross.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AIViewModel @Inject constructor(
    val keyStore: SecureKeyStore,
    val aiClient: AIPlaylistClient,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastResult = MutableStateFlow<AIPlaylistResult?>(null)
    val lastResult: StateFlow<AIPlaylistResult?> = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun generatePlaylist(prompt: String, count: Int = 25, onComplete: ((AIPlaylistResult) -> Unit)? = null) {
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            try {
                val availableSongs = songRepository.getSongs().first()
                if (availableSongs.isEmpty()) {
                    _errorMessage.value = "Library has no songs to curate from"
                    _isGenerating.value = false
                    return@launch
                }

                val request = PlaylistGenerationRequest(
                    prompt = prompt,
                    count = count,
                    availableSongs = availableSongs
                )

                val result = aiClient.generatePlaylist(request)
                if (result.isSuccess) {
                    val playlist = result.getOrThrow()
                    _lastResult.value = playlist
                    onComplete?.invoke(playlist)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Playlist generation failed"
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Playlist generation error")
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearResult() {
        _lastResult.value = null
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "AIViewModel"
    }
}
