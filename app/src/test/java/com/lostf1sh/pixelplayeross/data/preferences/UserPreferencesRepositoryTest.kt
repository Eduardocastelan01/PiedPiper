package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UserPreferencesRepositoryTest {

    @Test
    fun `keep screen awake while playing is opt-in and persists`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertEquals(false, repository.keepScreenAwakeWhilePlayingFlow.first())

            repository.setKeepScreenAwakeWhilePlaying(true)

            assertTrue(repository.keepScreenAwakeWhilePlayingFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `clearPreferencesExceptKeys preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.clearPreferencesExceptKeys(emptySet())

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals(NavBarStyle.DEFAULT, repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup clearExisting preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_style",
                        type = "string",
                        stringValue = "restored"
                    )
                ),
                clearExisting = true
            )

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals("restored", repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navBarCornerRadiusFlow clamps values outside the supported UI range`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setNavBarCornerRadius(-1)
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.setNavBarCornerRadius(999)
            assertEquals(MAX_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_corner_radius",
                        type = "int",
                        intValue = -1
                    )
                ),
                clearExisting = false
            )
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navidrome selected music folders persist and clear`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertTrue(repository.navidromeSelectedMusicFolderIdsFlow.first().isEmpty())

            repository.setNavidromeSelectedMusicFolderIds(setOf("flac", "mp3"))
            assertEquals(
                setOf("flac", "mp3"),
                repository.navidromeSelectedMusicFolderIdsFlow.first()
            )

            repository.clearNavidromeSelectedMusicFolderIds()
            assertTrue(repository.navidromeSelectedMusicFolderIdsFlow.first().isEmpty())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `playback speed persists exact hundredths and clamps out of range values`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            // Exact float equality: 1.15 must be stored as the literal 1.15f.
            repository.setPlaybackSpeed(1.15f)
            assertEquals(1.15f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(1.1499999f)
            assertEquals(1.15f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(10f)
            assertEquals(3f, repository.playbackSpeedFlow.first())

            repository.setPlaybackSpeed(0f)
            assertEquals(0.25f, repository.playbackSpeedFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy noisy or invalid stored playback speed is normalized on read`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)
            val speedKey = floatPreferencesKey("playback_speed")

            dataStore.edit { it[speedKey] = 0.74999994f }
            assertEquals(0.75f, repository.playbackSpeedFlow.first())

            dataStore.edit { it[speedKey] = Float.NaN }
            assertEquals(1f, repository.playbackSpeedFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `playbackSpeedFlow ignores unrelated preference writes`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.playbackSpeedFlow.test {
                assertEquals(1f, awaitItem())

                repository.setNavBarStyle("compact")
                testScheduler.advanceUntilIdle()
                expectNoEvents()

                repository.setPlaybackSpeed(1.15f)
                assertEquals(1.15f, awaitItem())
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `autoplay on skip is on by default and persists`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            assertTrue(repository.autoplayOnSkipFlow.first())

            repository.setAutoplayOnSkip(false)
            assertEquals(false, repository.autoplayOnSkipFlow.first())

            repository.setAutoplayOnSkip(true)
            assertTrue(repository.autoplayOnSkipFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
