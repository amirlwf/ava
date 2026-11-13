package com.harper.ava.playback

import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * MediaSession callback — 0.7.6. Owns exactly two things, both for
 * the notification buttons (see `NotificationLikeLogic` and
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
}
