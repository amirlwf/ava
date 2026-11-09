package com.harper.ava.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.harper.ava.data.repository.PlaybackState
import com.harper.ava.data.repository.PlayerRepository
import kotlinx.coroutines.flow.StateFlow

class QueueViewModel(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val playback: StateFlow<PlaybackState> = playerRepository.state

    fun playAt(index: Int) = playerRepository.playQueueItem(index)

    fun removeAt(index: Int) = playerRepository.removeFromQueue(index)

    fun moveItem(fromIndex: Int, toIndex: Int) = playerRepository.moveQueueItem(fromIndex, toIndex)

    fun shuffleQueue() = playerRepository.shuffleQueue()

    fun clearQueue() = playerRepository.clearQueue()

    companion object {
        fun factory(player: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QueueViewModel(player) as T
            }
    }
}
