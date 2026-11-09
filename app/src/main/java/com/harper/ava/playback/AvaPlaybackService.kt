package com.harper.ava.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harper.ava.AvaApplication
import com.harper.ava.MainActivity

/**
 * Background playback service (Media3 MediaSessionService).
 *
 * Owns the [MediaSession] that exposes the shared [PlayerRepository] player
 * to the system: notification, lock screen, Bluetooth / headset controls and
 * audio-focus handling are all provided by Media3 through this session.
 * The ExoPlayer instance itself lives in the application-scoped
 * PlayerRepository, so the UI and the service never diverge.
 */
class AvaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as AvaApplication
        val player = app.container.playerRepository.player
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.stop()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
