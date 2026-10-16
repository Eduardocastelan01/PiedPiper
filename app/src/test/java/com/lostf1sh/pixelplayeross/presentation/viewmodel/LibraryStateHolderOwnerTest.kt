package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.StorageFilter
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStateHolderOwnerTest {

    @Test
    fun `stale owner clearing keeps the newer binding`() = runTest {
        val holder = holder()
        val staleOwner = ownerScope()
        val currentOwner = ownerScope()
        holder.initialize(staleOwner)
        holder.initialize(currentOwner)
        advanceUntilIdle()

        holder.onCleared(staleOwner)
        holder.sortSongs(SortOption.SongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.SongTitleZA, holder.currentSongSortOption.value)
        staleOwner.cancel()
        currentOwner.cancel()
    }

    @Test
    fun `current owner clearing unbinds the holder`() = runTest {
        val holder = holder()
        val owner = ownerScope()
        holder.initialize(owner)
        advanceUntilIdle()
        assertEquals(SortOption.SongTitleAZ, holder.currentSongSortOption.value)

        holder.onCleared(owner)
        holder.sortSongs(SortOption.SongTitleZA, persist = false)
        advanceUntilIdle()

        assertEquals(SortOption.SongTitleAZ, holder.currentSongSortOption.value)
        owner.cancel()
    }

    // Separate Job so each owner has its own identity, while still running on the test
    // dispatcher that advanceUntilIdle drives.
    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    private fun holder(): LibraryStateHolder {
        val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
            every { songsSortOptionFlow } returns flowOf(SortOption.SongTitleAZ.storageKey)
            every { albumsSortOptionFlow } returns flowOf(SortOption.AlbumTitleAZ.storageKey)
            every { artistsSortOptionFlow } returns flowOf(SortOption.ArtistNameAZ.storageKey)
            every { foldersSortOptionFlow } returns flowOf(SortOption.FolderNameAZ.storageKey)
            every { likedSongsSortOptionFlow } returns flowOf(SortOption.LikedSongDateLiked.storageKey)
            every { lastStorageFilterFlow } returns flowOf(StorageFilter.ALL)
        }
        return LibraryStateHolder(mockk<MusicRepository>(relaxed = true), preferences)
    }
}
