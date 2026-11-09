package com.harper.ava.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.harper.ava.core.util.SleepTimerState
import com.harper.ava.core.util.clampCustomSleepMinutes
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.flow.StateFlow

/** Sleep Timer screen: a thin ViewModel over the PlayerRepository timer. */
class SleepTimerViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val sleepTimer: StateFlow<SleepTimerState> = playerRepository.sleepTimerState

    fun setTimer(minutes: Int) {
        playerRepository.setSleepTimer(minutes)
    }

    fun setCustomTimer(minutes: Int) {
        playerRepository.setSleepTimer(clampCustomSleepMinutes(minutes))
    }

    fun cancelTimer() {
        playerRepository.cancelSleepTimer()
    }

    companion object {
        fun factory(player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SleepTimerViewModel(player) as T
            }
    }
}
