package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.compose.runtime.Immutable
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.collections.immutable.ImmutableList

/** A generated selection has no playback or persistence effect until an action is chosen. */
@Immutable
data class MixPreview(
    val requestId: Long,
    val request: String,
    val suggestedName: String,
    val songs: ImmutableList<Song>,
    val requestedCount: Int?,
    val isDailyMix: Boolean,
    val savedName: String? = null,
)
