package com.lostf1sh.pixelplayeross.data.preferences

/** Shortcuts the user can show or hide in the app sidebar. Settings is always shown. */
object SidebarShortcutKeys {
    const val HOME = "home"
    const val EQUALIZER = "equalizer"
    const val AUDIO_BOOKMARKS = "audio_bookmarks"

    val ALL: List<String> = listOf(HOME, EQUALIZER, AUDIO_BOOKMARKS)
    val DEFAULT: Set<String> = ALL.toSet()
}
