package com.harper.ava.ui.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harper.ava.data.media.SongFileInfo
import com.harper.ava.data.media.loadSongFileInfo
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SongDetailsUiState {
    data object Loading : SongDetailsUiState
    data class Loaded(val song: Song, val fileInfo: SongFileInfo) : SongDetailsUiState
    /** The song is gone from the library (deleted file, stale id). */
    data object NotFound : SongDetailsUiState
}

/** Song Details: metadata from Room + file facts from MediaStore/Retriever. */
class SongDetailsViewModel(
    private val context: Context,
    private val songId: Long,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SongDetailsUiState>(SongDetailsUiState.Loading)
    val state: StateFlow<SongDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val song = musicRepository.getSongById(songId)
            if (song == null) {
                _state.value = SongDetailsUiState.NotFound
                return@launch
            }
            val fileInfo = loadSongFileInfo(context, song)
            _state.value = SongDetailsUiState.Loaded(song, fileInfo)
        }
    }

    companion object {
        fun factory(
            context: Context,
            songId: Long,
            music: MusicRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SongDetailsViewModel(context.applicationContext, songId, music) as T
            }
    }
}
