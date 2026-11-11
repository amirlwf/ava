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
