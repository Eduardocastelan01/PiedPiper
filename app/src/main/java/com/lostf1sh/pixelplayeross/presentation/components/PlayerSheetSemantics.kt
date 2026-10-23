package com.lostf1sh.pixelplayeross.presentation.components

import android.content.res.Resources
import androidx.annotation.StringRes
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerSheetState

/**
 * TalkBack description of the player sheet card, naming [songTitle] when there is one.
 *
 * Takes the caller's [resources] so it follows the in-app language. The English text starts with
 * "PixelPlayer player sheet collapsed/expanded", which the baseline profile benchmarks match.
 */
internal fun playerSheetContentDescription(
    resources: Resources,
    state: PlayerSheetState,
    songTitle: String?
): String {
    val title = songTitle?.takeIf { it.isNotBlank() }
    val descriptionRes = playerSheetContentDescriptionRes(state, hasTitle = title != null)
    return if (title != null) {
        resources.getString(descriptionRes, title)
    } else {
        resources.getString(descriptionRes)
    }
}

/** The variants with a title take it as their only format argument. */
@StringRes
internal fun playerSheetContentDescriptionRes(state: PlayerSheetState, hasTitle: Boolean): Int =
    when (state) {
        PlayerSheetState.COLLAPSED ->
            if (hasTitle) R.string.cd_player_sheet_collapsed else R.string.cd_player_sheet_collapsed_no_track
        PlayerSheetState.EXPANDED ->
            if (hasTitle) R.string.cd_player_sheet_expanded else R.string.cd_player_sheet_expanded_no_track
    }
