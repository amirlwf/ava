package com.harper.ava.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * MediaSession callback — 0.7.6. Owns exactly three things, the first
 * two for the notification buttons (see `NotificationLikeLogic` and
 * `NotificationRepeatShuffleLogic`):
 *
 * - [onConnect]: grants the notification custom commands to every
 *   connecting controller, on top of the platform defaults for that
 *   controller ([availableSessionCommandsWithNotificationActions]).
 *   Without this, the notification tap is dropped by
 *   `MediaNotificationManager.sendCustomCommandIfCommandIsAvailable`
 *   and the button is disabled and filtered out of the notification.
 *   The grant is wrapped in `runCatching` with a fallback to the
 *   stock default: a failure here must never break the connection
 *   (and the notification with it) — the worst case is a notification
 *   without the custom buttons, never a notification that does not post.
 * - [onCustomCommand]: a like tap toggles the current song's
 *   favourite state, a repeat tap cycles the repeat mode, a shuffle
 *   tap toggles shuffle — all through the lambdas the service owns
 *   (the service owns the repositories and the coroutine scope; this
 *   callback only routes). Anything else falls through to the default
 *   (`ERROR_NOT_SUPPORTED`).
 * - [onPlayerCommandRequest]: a play request from a controller (the
 *   notification's play button, a headset, Android Auto) routes
 *   through the repository's [playOrResume] instead of the bare
 *   `player.play()` the session would run — see [onPlay] below.
 *   A pause request is left alone (the default `player.pause()` is
 *   already the right thing, immediate and idempotent).
 *
 * The callback is deliberately thin: the grant logic and the buttons
 * themselves are pure functions in `NotificationLikeLogic` /
 * `NotificationRepeatShuffleLogic` and are unit-tested there. The
 * callback needs a live session to run, so it is covered by the
 * kotlinc type-check and the manual build, not by a unit test.
 *
 * 0.2-fix (Track 1): repeat + shuffle route here too (Harper's 0.2
 * report: no repeat, no shuffle in the notification).
 */
class AvaSessionCallback(
    private val onToggleFavorite: () -> Unit,
    private val onToggleRepeat: () -> Unit,
    private val onToggleShuffle: () -> Unit,
    private val onPlay: () -> Unit = {},
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        return runCatching {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    availableSessionCommandsWithNotificationActions(controller.isTrusted),
                )
                .build()
        }.getOrElse {
            super.onConnect(session, controller)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when {
            isLikeAction(customCommand.customAction) -> onToggleFavorite()
            isRepeatAction(customCommand.customAction) -> onToggleRepeat()
            isShuffleAction(customCommand.customAction) -> onToggleShuffle()
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Improve loop, run 40: a play request from a controller does NOT
     * go through the session's bare `player.play()`. That call is a
     * silent no-op in the two states the repository guards against —
     * a reset player (round 8: nothing loaded, e.g. the service was
     * demoted and the player reset) and STATE_ENDED (run 40: the queue
     * played to the end — playWhenReady
     * stays true, so play() changes nothing and the notification's
     * play button is dead). The repository's playOrResume handles
     * both (ensurePlayable rebuild, rewind-to-0 on ENDED, fade-in). The session still runs its own play() after
     * this returns SUCCESS — a harmless no-op by then, since the
     * player is already playing. A pause request (the player IS
     * playing) is left to the default path: player.pause() is
     * immediate and idempotent, and the fade-out pause belongs to the
     * in-app toggle only.
     */
    // NOTE: onPlayerCommandRequest is deprecated in the pinned Media3
    // 1.11.1, but it is the ONLY interception point in this version —
    // verified in the shipped AARs: PlayerWrapper is final (cannot
    // subclass it) and MediaSession.Builder has no setPlayerWrapper
    // hook, and Callback offers no Commands-based replacement. This
    // override is marked deprecated itself (the compiler requires it
    // for a deprecated member) — nothing calls it directly, only the
    // session does. Remove the annotation when a Media3 upgrade
    // brings a replacement.
    @Deprecated("Mirrors the deprecated MediaSession.Callback hook — the only play-command interception point in Media3 1.11.1.")
    @Suppress("DEPRECATION") // the super call below is to the same deprecated member
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        if (playerCommand == Player.COMMAND_PLAY_PAUSE && !session.player.isPlaying) {
            onPlay()
        }
        return super.onPlayerCommandRequest(session, controller, playerCommand)
    }
}
