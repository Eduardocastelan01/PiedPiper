package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.stats.PlaybackStatsRepository
import com.lostf1sh.pixelplayeross.di.DispatcherProvider
import com.lostf1sh.pixelplayeross.presentation.recap.RecapEntry
import com.lostf1sh.pixelplayeross.presentation.recap.RecapSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Decides which recaps the Stats screen and the seasonal Home card offer. Both flows only
 * compute while collected, so a screen that shows one of them never pays for the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapEntryViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    dispatchers: DispatcherProvider
) : ViewModel() {

    /** Source of "today"; replaced in tests before the flows are collected. */
    @VisibleForTesting
    internal var clock: Clock = Clock.systemDefaultZone()

    /** Recaps offered on the Stats screen, most relevant first; empty when none qualifies. */
    val statsEntries: StateFlow<ImmutableList<RecapEntry>> = playbackStatsRepository.refreshFlow
        .mapLatest {
            val totals = playbackStatsRepository.loadRecapYearTotals()
            RecapSchedule.statsEntries(LocalDate.now(clock), totals).toImmutableList()
        }
        .distinctUntilChanged()
        .catch { throwable ->
            Timber.e(throwable, "Failed to load recap entries")
            emit(persistentListOf())
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

    /**
     * The seasonal recap card on Home: December through mid-January, for a year with enough
     * listening, until the user dismisses it for that year. History is only read in season.
     */
    val homeCard: StateFlow<RecapEntry?> = combine(
        playbackStatsRepository.refreshFlow,
        userPreferencesRepository.recapHomeCardDismissedYearFlow
    ) { _, dismissedYear -> dismissedYear }
        .mapLatest { dismissedYear ->
            val today = LocalDate.now(clock)
            val year = RecapSchedule.homeCardYear(today)
            if (year == null || year == dismissedYear) return@mapLatest null
            val totals = playbackStatsRepository.loadRecapYearTotals()
            if (!RecapSchedule.hasEnoughListening(year, totals)) return@mapLatest null
            RecapEntry(year = year, isSoFar = RecapSchedule.isSoFar(year, today))
        }
        .distinctUntilChanged()
        .catch { throwable ->
            Timber.e(throwable, "Failed to load the seasonal recap card")
            emit(null)
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun dismissHomeCard(year: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setRecapHomeCardDismissedYear(year)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
