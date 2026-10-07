package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.database.AudioBookmarkEntity
import kotlinx.coroutines.flow.Flow

interface AudioBookmarkRepository {
    fun getAllBookmarksFlow(): Flow<List<AudioBookmarkEntity>>
    suspend fun getBookmarksForSong(songId: String): List<AudioBookmarkEntity>
    suspend fun insertBookmark(bookmark: AudioBookmarkEntity)
    suspend fun deleteBookmark(id: Long)
    suspend fun deleteBookmarks(ids: Collection<Long>)
    suspend fun getBookmarkById(id: Long): AudioBookmarkEntity?
    /** Re-attaches bookmarks whose song id is no longer in the library to its current id. */
    suspend fun relinkOrphanedBookmarks()
}
