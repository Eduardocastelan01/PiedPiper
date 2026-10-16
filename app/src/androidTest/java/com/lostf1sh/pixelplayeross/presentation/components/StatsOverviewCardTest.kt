package com.lostf1sh.pixelplayeross.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.data.stats.StatsTimeRange
import com.lostf1sh.pixelplayeross.presentation.viewmodel.HomeStatsOverviewState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsOverviewCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShowsNoListeningDataMessageAndStaysClickable() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                StatsOverviewCard(
                    state = HomeStatsOverviewState.Empty,
                    onClick = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.presentation_batch_g_stats_empty_no_timeline_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.presentation_batch_g_stats_overview_title))
            .performClick()

        composeRule.runOnIdle { assertThat(clicks).isEqualTo(1) }
    }

    @Test
    fun loadingStateShowsNoEmptyMessage() {
        composeRule.setContent {
            MaterialTheme {
                StatsOverviewCard(
                    state = HomeStatsOverviewState.Loading,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.presentation_batch_g_stats_overview_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.presentation_batch_g_stats_empty_no_timeline_title))
            .assertDoesNotExist()
    }

    @Test
    fun readyStateShowsTopTrack() {
        val start = System.currentTimeMillis() - 120_000L
        val summary = PlaybackStatsRepository(composeRule.activity.applicationContext)
            .buildSummaryFromEvents(
                range = StatsTimeRange.WEEK,
                songs = listOf(song("song-1", title = "Stats Test Track")),
                nowMillis = start + 120_000L,
                allEvents = listOf(
                    PlaybackStatsRepository.PlaybackEvent(
                        songId = "song-1",
                        timestamp = start + 60_000L,
                        durationMs = 60_000L,
                        startTimestamp = start,
                        endTimestamp = start + 60_000L
                    )
                )
            )

        composeRule.setContent {
            MaterialTheme {
                StatsOverviewCard(
                    state = HomeStatsOverviewState.Ready(summary),
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Stats Test Track").assertExists()
        composeRule.onNodeWithText(string(R.string.presentation_batch_g_stats_empty_no_timeline_title))
            .assertDoesNotExist()
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun song(id: String, title: String) = Song(
        id = id,
        title = title,
        artist = "Stats Artist",
        artistId = 1L,
        album = "Stats Album",
        albumId = 1L,
        path = "/music/$id.mp3",
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        genre = null,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
