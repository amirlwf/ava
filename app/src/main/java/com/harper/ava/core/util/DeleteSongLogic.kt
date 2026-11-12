package com.harper.ava.core.util

/**
 * Pure, Android-free logic for the 0.7.2 song delete.
 * Everything here is unit-tested with plain JUnit (no Robolectric,
 * no device) — see DeleteSongLogicTest.
 *
 * The rule (kept in one place so the UI, the queue and the resume
 * checkpoint all agree): deleting the playing song advances to the
 * next song; deleting the only song stops playback entirely.
 */

/** What happens to the queue/checkpoint when one song is deleted. */
data class DeleteQueuePlan(
    /** Queue ids after the deleted song is taken out. */
    val queueIdsAfter: List<Long>,
    /** True when the deleted song was the one playing. */
    val wasPlaying: Boolean,
    /** True when the queue is empty afterwards (playback must stop). */
    val queueBecomesEmpty: Boolean,
    /**
     * Index playback continues at when [wasPlaying]: the song that
     * followed the deleted one, or -1 when the queue is empty.
     * When the deleted song was not playing this is the (possibly
     * shifted) index of the still-playing song.
     */
    val nextIndex: Int,
)

/**
 * Plans the queue after deleting [deletedId] from [queueIds], where
 * [currentIndex] is the playing position (-1 when nothing plays).
 * A deleted id that is not in the queue leaves everything unchanged.
 */
fun planSongDelete(
    queueIds: List<Long>,
    currentIndex: Int,
    deletedId: Long,
): DeleteQueuePlan {
    val deletedIndex = queueIds.indexOf(deletedId)
    if (deletedIndex < 0) {
        return DeleteQueuePlan(
            queueIdsAfter = queueIds,
            wasPlaying = false,
            queueBecomesEmpty = false,
            nextIndex = currentIndex,
        )
    }
    val after = queueIds.filterIndexed { index, _ -> index != deletedIndex }
    val wasPlaying = deletedIndex == currentIndex
    val nextIndex = when {
        after.isEmpty() -> -1
        wasPlaying -> deletedIndex.coerceAtMost(after.lastIndex)
        deletedIndex < currentIndex -> currentIndex - 1
        else -> currentIndex
    }
    return DeleteQueuePlan(
        queueIdsAfter = after,
        wasPlaying = wasPlaying,
        queueBecomesEmpty = after.isEmpty(),
        nextIndex = nextIndex,
    )
}
