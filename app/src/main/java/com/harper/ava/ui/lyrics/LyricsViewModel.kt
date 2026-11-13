package com.harper.ava.ui.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.core.util.ParsedLyrics
import com.harper.ava.data.media.LyricsLoadResult
import com.harper.ava.data.media.LyricsRepository
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data class Loaded(val lyrics: ParsedLyrics) : LyricsUiState
    /** No .lrc file was found for this song. */
    data object NotFound : LyricsUiState
    /** Nothing is playing. */
    data object NoSong : LyricsUiState
}

/** Lyrics screen: loads the current song's local .lrc file when the track changes. */
class LyricsViewModel(
    private val lyricsRepository: LyricsRepository,
    private val userPreferences: UserPreferences,
    playerRepository: PlayerRepository,
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playerRepository.state
        .map { it.currentSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live playback position (ms), for highlighting the current line. */
    val positionMs: StateFlow<Long> = playerRepository.positionMs

    private val _lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.NoSong)
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            // Collect the song itself (not just its id): the loaded lyrics
            // always belong to exactly the song that triggered the load.
            // (StateFlow already only emits on change, so no distinctUntilChanged.)
            currentSong
                .collect { song ->
                    if (song == null) {
                        _lyrics.value = LyricsUiState.NoSong
                        return@collect
                    }
                    _lyrics.value = LyricsUiState.Loading
                    val folderUris = userPreferences.settings.first().musicFolderUris
                    val result: LyricsLoadResult = lyricsRepository.loadLyrics(song, folderUris)
                    _lyrics.value = if (result.found && result.lyrics != null) {
                        LyricsUiState.Loaded(result.lyrics)
                    } else {
                        LyricsUiState.NotFound
                    }
                }
        }
    }

    companion object {
        fun factory(
            lyrics: LyricsRepository,
            prefs: UserPreferences,
            player: PlayerRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LyricsViewModel(lyrics, prefs, player) as T
            }
    }
}
