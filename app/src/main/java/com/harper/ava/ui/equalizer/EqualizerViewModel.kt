package com.harper.ava.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.harper.ava.data.repository.EqualizerState
import com.harper.ava.data.repository.PlaybackState
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.flow.StateFlow

/** Equalizer screen: a thin ViewModel over the PlayerRepository equalizer. */
class EqualizerViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val equalizer: StateFlow<EqualizerState> = playerRepository.equalizerState

    /** The loaded song (if any) — the no-session empty state needs to know one exists. */
    val playback: StateFlow<PlaybackState> = playerRepository.state

    init {
        playerRepository.refreshEqualizer()
    }

    /**
     * Improve loop, run 21 (UX): the no-session empty state says "play a
     * song first" — and the song is already loaded (the equalizer is only
     * reachable from Now Playing, which hides its menu when nothing is
     * loaded), so the action is right here instead of a trip back to the
     * library. Starting playback creates the audio session, and the
     * player's onAudioSessionIdChanged listener refreshes the equalizer,
     * so this screen goes live on its own. No-op when nothing is loaded
     * or already playing.
     */
    fun playCurrentSong() {
        if (playerRepository.state.value.currentSong == null) return
        if (playerRepository.player.isPlaying) return
        playerRepository.togglePlayPause()
    }

    fun setEnabled(enabled: Boolean) = playerRepository.setEqualizerEnabled(enabled)

    fun applyPreset(presetIndex: Int) = playerRepository.applyEqualizerPreset(presetIndex)

    fun setBand(band: Int, levelDb: Float) = playerRepository.setEqualizerBand(band, levelDb)

    fun resetBands() = playerRepository.resetEqualizerBands()

    companion object {
        fun factory(player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EqualizerViewModel(player) as T
            }
    }
}
