package com.lostf1sh.pixelplayeross.presentation.screens

import com.lostf1sh.pixelplayeross.data.model.MusicFolder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryFolderLocateScopeTest {
    private val rootFolders = listOf(MusicFolder(path = "/storage/emulated/0/Music", name = "Music"))

    @Test
    fun `locate includes the current folder and its descendants`() {
        assertTrue(isSongParentInFolderScope("/storage/emulated/0/Music", "/storage/emulated/0/Music", rootFolders))
        assertTrue(isSongParentInFolderScope("/storage/emulated/0/Music/Rock/Live", "/storage/emulated/0/Music/", rootFolders))
    }

    @Test
    fun `locate stays hidden in unrelated folders and when moving below the song`() {
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Music/Rock", "/storage/emulated/0/Music/Jazz", rootFolders))
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Music/Rock", "/storage/emulated/0/Music/Jazz/Live", rootFolders))
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Music", "/storage/emulated/0/Music/Rock", rootFolders))
    }

    @Test
    fun `similar folder prefixes are not descendants`() {
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Music2/Rock", "/storage/emulated/0/Music", rootFolders))
    }

    @Test
    fun `root locate is limited to displayed folder trees`() {
        assertTrue(isSongParentInFolderScope("/storage/emulated/0/Music/Rock", null, rootFolders))
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Downloads", null, rootFolders))
        assertFalse(isSongParentInFolderScope("/storage/emulated/0/Music/Rock", null, emptyList()))
        assertFalse(isSongParentInFolderScope(null, null, rootFolders))
    }
}
