package com.harper.ava.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.local.db.AlbumSummary
import com.harper.ava.data.local.db.ArtistSummary
import com.harper.ava.data.local.db.FolderSummary
import com.harper.ava.data.local.db.GenreSummary
import com.harper.ava.data.media.ScanResult
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.data.repository.SongDeleteCoordinator
import com.harper.ava.data.repository.SongSort
import com.harper.ava.domain.model.Playlist
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.toUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { SONGS, ARTISTS, ALBUMS, FOLDERS, GENRES }

/** Which group (artist/album/folder/genre) is opened inside its tab, if any. */
sealed interface LibraryGroup {
    data class Artist(val name: String) : LibraryGroup
    data class Album(val name: String) : LibraryGroup
    /** [path] is the raw MediaStore RELATIVE_PATH (e.g. "Music/Rock/"). */
    data class Folder(val path: String) : LibraryGroup
    data class Genre(val name: String) : LibraryGroup
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _sort = MutableStateFlow(SongSort.TITLE)
    val sort: StateFlow<SongSort> = _sort

    private val _tab = MutableStateFlow(LibraryTab.SONGS)
    val tab: StateFlow<LibraryTab> = _tab

    private val _openGroup = MutableStateFlow<LibraryGroup?>(null)
    val openGroup: StateFlow<LibraryGroup?> = _openGroup.asStateFlow()

    val songs: StateFlow<List<Song>> = _sort
        .flatMapLatest { musicRepository.observeSongs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Artists/albums are derived from the songs themselves — no separate tables. */
    val artists: StateFlow<List<ArtistSummary>> = musicRepository
        .observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = musicRepository
        .observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Folders derived from MediaStore RELATIVE_PATH (stage 5). */
    val folders: StateFlow<List<FolderSummary>> = musicRepository
        .observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Genres from the batched MediaStore genre join (stage 5). */
    val genres: StateFlow<List<GenreSummary>> = musicRepository
        .observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Songs of the currently opened artist/album/folder/genre, empty when none is open. */
    val groupSongs: StateFlow<List<Song>> = _openGroup
        .flatMapLatest { group ->
            when (group) {
                is LibraryGroup.Artist -> musicRepository.observeSongsByArtist(group.name)
                is LibraryGroup.Album -> musicRepository.observeSongsByAlbum(group.name)
                is LibraryGroup.Folder -> musicRepository.observeSongsByFolder(group.path)
                is LibraryGroup.Genre -> musicRepository.observeSongsByGenre(group.name)
                null -> kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = musicRepository
        .observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    fun onScanStarted() {
        _scanState.value = ScanUiState.Scanning
    }

    fun onScanResult(result: ScanResult) {
        _scanState.value = result.toUiState()
    }

    fun setSort(sort: SongSort) {
        _sort.value = sort
    }

    fun setTab(tab: LibraryTab) {
        _tab.value = tab
        _openGroup.value = null
    }

    fun openGroup(group: LibraryGroup) {
        _openGroup.value = group
    }

    fun closeGroup() {
        _openGroup.value = null
    }

    fun play(song: Song, allSongs: List<Song>) {
        viewModelScope.launch {
            musicRepository.markPlayed(song.id)
            playerRepository.playSongs(allSongs, allSongs.indexOf(song).coerceAtLeast(0))
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { musicRepository.toggleFavorite(song) }
    }

    /** 0.7.2: deletes the song's file (system consent dialog on API 30+) + library rows. */
    fun deleteSong(song: Song) {
        viewModelScope.launch { SongDeleteCoordinator.requestDelete(song, musicRepository, playerRepository) }
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

    companion object {
        fun factory(music: MusicRepository, player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(music, player) as T
            }
    }
}
