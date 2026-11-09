package com.harper.ava.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Favorites screen (stage 6): the spec's Favorites section was only a Home
 * list before — no dedicated screen, no Play All / Shuffle All. This
 * ViewModel exposes the full favorite list plus those two actions.
 */
class FavoritesViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val favorites: StateFlow<List<Song>> = musicRepository
        .observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playAll(songs: List<Song>) {
        if (songs.isNotEmpty()) playerRepository.playSongs(songs, 0)
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isNotEmpty()) playerRepository.playSongs(songs.shuffled(), 0)
    }

    fun play(song: Song, list: List<Song>) {
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
                    FavoritesViewModel(music, player) as T
            }
    }
}
