package com.harper.ava.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.data.repository.SongDeleteCoordinator
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val userPreferences: UserPreferences? = null,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<Song>> = _query
        .debounce(200L)
        .flatMapLatest { musicRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recent searches (persisted in DataStore, most recent first) — stage 6. */
    val recentSearches: StateFlow<List<String>> = (userPreferences?.settings
        ?.map { it.recentSearches }
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearSearchHistory() {
        viewModelScope.launch { userPreferences?.clearSearchHistory() }
    }

    fun removeSearchFromHistory(query: String) {
        viewModelScope.launch { userPreferences?.removeSearchQuery(query) }
    }

    fun addToQueue(song: Song, playNext: Boolean) {
        playerRepository.addToQueue(song, playNext)
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch { musicRepository.addSongToPlaylist(playlistId, song.id) }
    }

    /** 0.7.2 playlist-add fix: create a playlist from the song menu and add this song to it. */
    fun createPlaylistAndAdd(song: Song, name: String) {
        viewModelScope.launch {
            runCatching {
                val id = musicRepository.createPlaylist(name)
                musicRepository.addSongToPlaylist(id, song.id)
            }
        }
    }

    fun play(song: Song, allResults: List<Song>) {
        viewModelScope.launch {
            musicRepository.markPlayed(song.id)
            // A search counts as completed when the user opens a result —
            // typing alone never pollutes the history.
            userPreferences?.recordSearchQuery(_query.value)
            playerRepository.playSongs(allResults, allResults.indexOf(song).coerceAtLeast(0))
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { musicRepository.toggleFavorite(song) }
    }

    /** 0.7.2: deletes the song's file (system consent dialog on API 30+) + library rows. */
    fun deleteSong(song: Song) {
        viewModelScope.launch { SongDeleteCoordinator.requestDelete(song, musicRepository, playerRepository) }
    }

    companion object {
        fun factory(
            music: MusicRepository,
            player: PlayerRepository,
            prefs: UserPreferences? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(music, player, prefs) as T
            }
    }
}
