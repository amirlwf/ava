package com.harper.ava.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harper.ava.AvaApplication
import com.harper.ava.MainActivity
import com.harper.ava.R

/**
 * Background playback service (Media3 MediaSessionService).
 *
 * Owns the [MediaSession] that exposes the shared [PlayerRepository] player
 * to the system: notification, lock screen, Bluetooth / headset controls and
 * audio-focus handling are all provided by Media3 through this session.
 * The ExoPlayer instance itself lives in the application-scoped
 * PlayerRepository, so the UI and the service never diverge.
 *
 * 0.5.7 — why this service foregrounds itself (do not remove):
 * Media3's MediaSessionService only hands a session to its
 * MediaNotificationManager from addSession() — called from onBind (a
 * MediaController binding) or onStartCommand (a media/custom action).
 * This app drives the shared ExoPlayer directly and never creates a
 * MediaController, and playSongs() starts the service with a plain
 * intent, so before 0.5.7 the manager never saw the session at all:
 * no media notification ever appeared and startForeground() was never
 * called, so every play tap died with
 * ForegroundServiceDidNotStartInTimeException once the platform
 * timeout expired. The fixes: (1) addSession() in onCreate() so
 * Media3 builds the media notification, and (2) startForeground()
 * synchronously in onStartCommand() with a placeholder notification,
 * so the timeout is satisfied on every start even before Media3's
 * async notification arrives.
 */
class AvaPlaybackService : MediaSessionService() {

    companion object {
        /**
         * The notification id MUST match Media3's default
         * ([DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID]):
         * the placeholder posted in onStartCommand and the media
         * notification Media3 posts later are then the SAME notification
         * — Media3 replaces it instead of stacking a second one.
         * Guarded by PlaybackServiceTest.
         */
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ava_playback"
    }

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
        // 0.5.7: register the session with the service (see the class
        // doc). Without this, Media3 never builds the media notification
        // and never calls startForeground() for this session.
        mediaSession?.let { addSession(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 0.5.7: foreground IMMEDIATELY, synchronously, on every start.
        // startForegroundService() starts a platform timeout that nothing
        // in Media3 guarantees: MediaSessionService.onStartCommand with a
        // plain intent does nothing for foreground, and its only explicit
        // foreground trigger (onPlayRequested) fires solely on controller
        // play requests — never on the app's direct player.play(). The
        // placeholder is replaced by Media3's real media notification
        // (same id) as soon as playback is up.
        startForegroundNow()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Posts a minimal placeholder notification and promotes the service
     * to the foreground right now. Media3 replaces this notification
     * (same id) with the real media notification once playback starts,
     * and demotes the service (stopForeground, notification kept) when
     * playback pauses — the standard Media3 behaviour from then on.
     */
    private fun startForegroundNow() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.now_playing_title))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
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
