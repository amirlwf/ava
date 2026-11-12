package com.harper.ava.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.data.repository.SongDeleteCoordinator
import com.harper.ava.data.repository.SongSort
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    /** Stage 6: real song counts + cover art (the base flow reports 0). */
    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylistsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.7.4: library source for the in-playlist add-songs picker. */
    val allSongs: StateFlow<List<Song>> = musicRepository
        .observeSongs(SongSort.TITLE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.7.4: add several songs at once from the picker; returns how many were new. */
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Int =
        musicRepository.addSongsToPlaylist(playlistId, songIds)

    /**
     * 0.7.4: one-shot read of the songs already in a playlist, so the
     * picker can mark them as already added before it opens.
     */
    suspend fun playlistSongIds(playlistId: Long): Set<Long> =
        musicRepository.observePlaylistSongs(playlistId).first().map { it.id }.toSet()

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            runCatching { musicRepository.createPlaylist(name) }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { musicRepository.deletePlaylist(id) }
    }

    fun playPlaylist(id: Long) {
        viewModelScope.launch {
            // One-shot read: take the first emission, then play it.
            val list = musicRepository.observePlaylistSongs(id).first()
            if (list.isNotEmpty()) playerRepository.playSongs(list, 0)
        }
    }

    companion object {
        fun factory(music: MusicRepository, player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistsViewModel(music, player) as T
            }
    }
}

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val playlist: StateFlow<Playlist?> = musicRepository
        .observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: StateFlow<List<Song>> = musicRepository
        .observePlaylistSongs(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.7.2 playlist-add fix: songs inside a playlist had no add-to-playlist action at all. */
    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.7.4: library source for the in-playlist add-songs picker. */
    val allSongs: StateFlow<List<Song>> = musicRepository
        .observeSongs(SongSort.TITLE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 0.7.4: add several songs at once from the picker; returns how many were new. */
    suspend fun addSongsToPlaylist(songIds: List<Long>): Int =
        musicRepository.addSongsToPlaylist(playlistId, songIds)

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

    fun playAll(songs: List<Song>) {
        if (songs.isNotEmpty()) playerRepository.playSongs(songs, 0)
    }

    /** Stage 6: Shuffle Playlist (spec §7) — the detail screen had Play All only. */
    fun shuffleAll(songs: List<Song>) {
        if (songs.isNotEmpty()) playerRepository.playSongs(songs.shuffled(), 0)
    }

    fun removeSong(song: Song) {
        viewModelScope.launch { musicRepository.removeSongFromPlaylist(playlistId, song.id) }
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
            playlistId: Long,
            music: MusicRepository,
            player: PlayerRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistDetailViewModel(playlistId, music, player) as T
            }
    }
}
