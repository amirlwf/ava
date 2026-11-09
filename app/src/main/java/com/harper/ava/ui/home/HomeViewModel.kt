package com.harper.ava.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.media.ScanResult
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.data.repository.SongSort
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    /** Time-of-day greeting bucket: 5–12 morning, 12–17 afternoon, 17–20 evening, else night. */
    val greetingHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    val recentlyAdded: StateFlow<List<Song>> = musicRepository
        .observeSongs(SongSort.DATE_ADDED)
        .map { it.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Real recently-played list: songs with a recorded lastPlayedMs. */
    val recentlyPlayed: StateFlow<List<Song>> = musicRepository
        .observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Song>> = musicRepository
        .observeFavorites()
        .map { it.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Playlists with their real song counts + cover art (stage 6 repository flow). */
    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylistsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songCount: StateFlow<Int> = musicRepository
        .observeSongCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    fun onScanStarted() {
        _scanState.value = ScanUiState.Scanning
    }

    fun onScanResult(result: ScanResult) {
        _scanState.value = result.toUiState()
    }

    fun play(song: Song) {
        viewModelScope.launch {
            musicRepository.markPlayed(song.id)
            playerRepository.play(song)
        }
    }

    /** Plays [song] inside its section list, so Next walks the section. */
    fun playInList(song: Song, list: List<Song>) {
        viewModelScope.launch {
            musicRepository.markPlayed(song.id)
            playerRepository.playSongs(list, list.indexOf(song).coerceAtLeast(0))
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { musicRepository.toggleFavorite(song) }
    }

    fun addToQueue(song: Song, playNext: Boolean) {
        playerRepository.addToQueue(song, playNext)
    }

    fun addToPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch { musicRepository.addSongToPlaylist(playlistId, song.id) }
    }

    companion object {
        fun factory(music: MusicRepository, player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(music, player) as T
            }
    }
}
