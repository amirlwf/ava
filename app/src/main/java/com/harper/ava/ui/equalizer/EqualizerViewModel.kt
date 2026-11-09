package com.harper.ava.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.harper.ava.data.repository.EqualizerState
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.flow.StateFlow

/** Equalizer screen: a thin ViewModel over the PlayerRepository equalizer. */
class EqualizerViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val equalizer: StateFlow<EqualizerState> = playerRepository.equalizerState

    init {
        playerRepository.refreshEqualizer()
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
