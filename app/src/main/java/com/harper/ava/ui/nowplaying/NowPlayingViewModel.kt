package com.harper.ava.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlaybackState
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Playback speeds offered by the speed control, in cycle order. */
val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

class NowPlayingViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val playback: StateFlow<PlaybackState> = playerRepository.state

    /** Live sleep-timer state, for the Now Playing overflow menu label. */
    val sleepTimer: StateFlow<com.harper.ava.core.util.SleepTimerState> =
        playerRepository.sleepTimerState

    /** Live favorite state of the current song (refreshes when toggled). */
    val isCurrentFavorite: StateFlow<Boolean> = combine(
        playerRepository.state,
        musicRepository.observeFavorites().map { songs -> songs.map { it.id }.toSet() },
    ) { state, favoriteIds ->
        state.currentSong?.id in favoriteIds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePlayPause() = playerRepository.togglePlayPause()

    fun playNext() = playerRepository.playNext()

    fun playPrevious() = playerRepository.playPrevious()

    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)

    fun toggleShuffle() = playerRepository.toggleShuffle()

    fun cycleRepeatMode() = playerRepository.cycleRepeatMode()

    fun toggleFavorite() {
        val song = playerRepository.state.value.currentSong ?: return
        viewModelScope.launch { musicRepository.toggleFavorite(song) }
    }

    /** Cycles 0.75x → 1x → 1.25x → 1.5x → 2x → back to 0.75x. */
    fun cycleSpeed() {
        val current = playerRepository.state.value.playbackSpeed
        val index = PLAYBACK_SPEEDS.indexOf(current)
        val next = PLAYBACK_SPEEDS[(index + 1) % PLAYBACK_SPEEDS.size]
        playerRepository.setPlaybackSpeed(next)
    }

    companion object {
        fun factory(music: MusicRepository, player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NowPlayingViewModel(music, player) as T
            }
    }
}
