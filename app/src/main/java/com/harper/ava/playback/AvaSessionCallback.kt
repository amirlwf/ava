package com.harper.ava.playback

import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * MediaSession callback — 0.7.6. Owns exactly two things, both for
 * the notification like button (see `NotificationLikeLogic`):
 *
 * - [onConnect]: grants the like custom command to every connecting
 *   controller, on top of the platform defaults for that controller
 *   ([availableSessionCommandsWithLike]). Without this, the
 *   notification tap is dropped by
 *   `MediaNotificationManager.sendCustomCommandIfCommandIsAvailable`
 *   and the button is disabled and filtered out of the notification.
 *   The grant is wrapped in `runCatching` with a fallback to the
 *   stock default: a failure here must never break the connection
 *   (and the notification with it) — the worst case is a notification
 *   without a like button, never a notification that does not post.
 * - [onCustomCommand]: a like tap toggles the current song's
 *   favourite state through [onToggleFavorite] (the service owns
 *   the repositories and the coroutine scope; this callback only
 *   routes). Anything else falls through to the default
 *   (`ERROR_NOT_SUPPORTED`).
 *
 * The callback is deliberately thin: the grant logic and the button
 * itself are pure functions in `NotificationLikeLogic` and are
 * unit-tested there. The callback needs a live session to run, so
 * it is covered by the kotlinc type-check and the manual build,
 * not by a unit test.
 */
class AvaSessionCallback(
    private val onToggleFavorite: () -> Unit,
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        return runCatching {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    availableSessionCommandsWithLike(controller.isTrusted),
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
        if (!isLikeAction(customCommand.customAction)) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        onToggleFavorite()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
