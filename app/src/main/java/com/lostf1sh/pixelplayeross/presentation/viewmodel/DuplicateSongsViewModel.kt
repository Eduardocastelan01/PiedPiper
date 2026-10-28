package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.library.DuplicateFinder
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DuplicateSongsUiState(
    val isLoading: Boolean = true,
    val isCleaning: Boolean = false,
    val groups: List<DuplicateFinder.DuplicateGroup> = emptyList(),
    val lastCleanedCount: Int = 0,
) {
    val totalDuplicates: Int get() = groups.sumOf { it.songs.size }
    val totalRedundantSongs: Int get() = groups.sumOf { it.duplicatesToRemove.size }
}

@HiltViewModel
class DuplicateSongsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateSongsUiState())
    val uiState: StateFlow<DuplicateSongsUiState> = _uiState.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groups = withContext(Dispatchers.Default) {
                DuplicateFinder.findDuplicates(musicRepository.getAllSongsOnce())
            }
            _uiState.update { it.copy(isLoading = false, groups = groups) }
        }
    }

    private suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        // 1. Direct file deletion
        try {
            val file = File(song.path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}

        // 2. MediaStore removal
        try {
            song.id.toLongOrNull()?.let { mediaId ->
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {}

        // 3. Database removal
        song.id.toLongOrNull()?.let { musicRepository.deleteById(it) }
    }

    fun removeDuplicatesInGroup(group: DuplicateFinder.DuplicateGroup, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val toRemove = group.duplicatesToRemove
            for (song in toRemove) {
                deleteSong(song)
            }
            scan()
            _uiState.update { it.copy(isCleaning = false, lastCleanedCount = toRemove.size) }
            onComplete()
        }
    }

    fun removeAllDuplicates(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaning = true) }
            val allToRemove = _uiState.value.groups.flatMap { it.duplicatesToRemove }
            for (song in allToRemove) {
                deleteSong(song)
            }
            scan()
            _uiState.update { it.copy(isCleaning = false, lastCleanedCount = allToRemove.size) }
            onComplete(allToRemove.size)
        }
    }
}
