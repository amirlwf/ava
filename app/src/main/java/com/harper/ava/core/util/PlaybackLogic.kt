package com.harper.ava.core.util

/**
 * Pure, Android-free playback-recovery logic (round 8). Everything here
 * is unit-tested with plain JUnit (no Robolectric, no device) — see
 * PlaybackRecoveryTest.
 */

/**
 * Whether the player must be rebuilt from the repository's saved queue
 * before play() can do anything.
 *
 * ExoPlayer.stop() resets the player to IDLE with no current media
 * item (verified from the Media3 1.11.1 bytecode: stopInternal sets
 * state IDLE and currentMediaItemIndex -1). AvaPlaybackService used to
 * call stop() in onDestroy, and the service is destroyed when the user
 * dismisses the media notification (on some devices the dismiss stops
 * the service) — so after a dismiss the repository still knew the
 * queue, the index and the last position, but the player had no media
 * items and play() was a silent no-op until a new song was picked.
 *
 * @param mediaItemCount the player's current media-item count
 * @param isIdle true when the player's playback state is IDLE
 * @param queueSize the repository queue size (0 = nothing to resume)
 */
fun needsPlayerRebuild(mediaItemCount: Int, isIdle: Boolean, queueSize: Int): Boolean =
    queueSize > 0 && (mediaItemCount == 0 || isIdle)

// ---------------------------------------------------------------------------
// Playing-index tracking (improve loop, run 15)
//
// A queue may hold the same song twice (add-to-queue / play-next /
// restored checkpoint), and Song is a data class — so a value lookup
// like queue.indexOf(currentSong) always returns the FIRST copy even
// when the second copy is the one playing. Every index below is pure
// position arithmetic, so duplicates can never misplace it.
// ---------------------------------------------------------------------------

/**
 * Where the playing index lands after the item at [removedIndex] is
 * taken out of the queue. -1 means "nothing is playing" — either
 * nothing played before, or the playing song itself was removed (then
 * ExoPlayer's media-item transition re-points the index at the song
 * that follows it).
 */
fun queueIndexAfterRemoval(queueIndex: Int, removedIndex: Int): Int = when {
    queueIndex < 0 -> -1
    removedIndex == queueIndex -> -1
    removedIndex < queueIndex -> queueIndex - 1
    else -> queueIndex
}

/**
 * Where the playing index lands after the item at [fromIndex] is moved
 * to [toIndex] (ExoPlayer.moveMediaItem / moveItem semantics: remove,
 * then insert at the destination). Moving the playing song itself
 * carries the index with it; moving an item across the playing song
 * shifts it by one; anything else leaves it alone.
 */
fun queueIndexAfterMove(queueIndex: Int, fromIndex: Int, toIndex: Int): Int = when {
    queueIndex < 0 -> -1
    fromIndex == queueIndex -> toIndex
    fromIndex < queueIndex && toIndex >= queueIndex -> queueIndex - 1
    fromIndex > queueIndex && toIndex <= queueIndex -> queueIndex + 1
    else -> queueIndex
}
