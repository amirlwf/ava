package com.harper.ava.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.core.util.InsightsData
import com.harper.ava.core.util.computeInsights
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Insights screen is showing. */
sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    data object Empty : InsightsUiState
    data class Data(val insights: InsightsData) : InsightsUiState
}

/**
 * Listening insights (0.7.0): aggregates the timestamped play history
 * into top songs / artists / albums / genres, listening time (today /
 * week / month), a 7-day trend and listening habits. The math lives in
 * [computeInsights] (pure, unit-tested); this ViewModel only wires the
 * history flow to it. An empty history is a real state, not an error —
 * the screen explains that stats appear after listening.
 */
class InsightsViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    /**
     * Run 28: the aggregation runs on Dispatchers.Default, not Main.
     * [computeInsights] makes ~10 full passes over the whole listening
     * history (today/week/month filters, the 7-day loop, day-starts,
     * groupings + sorts) and the history is unbounded — Room re-emits
     * the full list on every write, i.e. every ~10 s bank flush while
     * playing plus every track change. That work used to run inside
     * this .map on the main thread, so opening Insights while a song
     * played re-aggregated on Main every 10 s. flowOn moves the
     * upstream map (and the repository's entity→event map) to Default;
     * stateIn still collects on Main, so the screen is unaffected.
     */
    val uiState: StateFlow<InsightsUiState> = musicRepository
        .observeListeningHistory()
        .map { events ->
            if (events.isEmpty()) {
                InsightsUiState.Empty
            } else {
                InsightsUiState.Data(computeInsights(events, System.currentTimeMillis()))
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    /** Plays a top song (tapping a ranked row starts it right away). */
    fun playTopSong(songId: Long) {
        viewModelScope.launch {
            musicRepository.markPlayed(songId)
            musicRepository.getSongById(songId)?.let { playerRepository.play(it) }
        }
    }

    companion object {
        fun factory(music: MusicRepository, player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    InsightsViewModel(music, player) as T
            }
    }
}
