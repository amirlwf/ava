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
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.harper.ava.AvaApplication
import com.harper.ava.MainActivity
import com.harper.ava.R
import com.harper.ava.data.preferences.Accent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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
 *
 * 0.6.0 — the notification itself is Ava's own now, not the stock
 * Media3 one: [AvaMediaNotificationProvider] is wired in below via
 * `setMediaNotificationProvider()` before `addSession()`. Only the
 * look changed (Ava small icon, Ava action drawables, accent colour,
 * channel name, artist/album fallback); the lifecycle above is
 * untouched.
 *
 * 0.7.6 — the notification gains a like button (heart in the
 * expanded view) via a MediaSession custom command: the session is
 * built with [AvaSessionCallback] (which grants the like command to
 * connecting controllers and routes the tap to
 * `MusicRepository.toggleFavorite`), and a collector below keeps
 * the button's heart in sync with the current song's favourite
 * state via `MediaSession.setMediaButtonPreferences`. Stock Media3
 * plumbing only — see `NotificationLikeLogic` for the full chain
 * and its benign failure modes.
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

    /**
     * 0.7.5: scope for the settings collector that keeps the
     * notification accent live (see [onCreate]). Cancelled in
     * [onDestroy]; it owns no provider — the collector only captures
     * the local provider instance, so there is no `notificationProvider`
     * field and no `release()` call (the 0.7.4 RemoteViews provider
     * and its ticker scope are gone with the revert).
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Accent colour for the notification (placeholder + Media3 media
     * notification), resolved once in [onCreate] from the user's theme
     * and then kept live by the settings collector.
     */
    private var accentColorArgb: Int = notificationAccentArgb(Accent.VIOLET, null)

    /**
     * 0.1-fix1 (Track A): the media-button preferences last pushed to
     * the session (the like button, or empty when no song is loaded).
     * Kept so the accent collector can re-push them after a theme
     * change: re-pushing forces Media3 to rebuild the notification
     * with the new accent (see the accent collector below). The list
     * is the service's own copy — the like collector assigns it on
     * every change before pushing.
     */
    private var lastMediaButtonPreferences: List<CommandButton> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val app = application as AvaApplication
        // 0.6.0: Ava's own notification look (see
        // AvaMediaNotificationProvider). Wired in BEFORE addSession() so
        // the manager uses it from the first notification; lifecycle and
        // playback behaviour are untouched.
        // 0.7.5: reverted to the 0.7.3 DefaultMediaNotificationProvider
        // subclass — the 0.7.4 from-scratch RemoteViews provider crashed
        // on play with the identical exception even with the PNG small
        // icon, so the RemoteViews path itself is the cause and is never
        // repeated (see AvaMediaNotificationProvider). The subclass has
        // no ticker/scope, so there is no provider field and no
        // release() call in onDestroy(); the accent still comes from
        // the user's AppTheme via resolveNotificationAccent(), and is
        // now also kept LIVE by the collector below.
        accentColorArgb = resolveNotificationAccent(app)
        val provider = AvaMediaNotificationProvider(this, accentColorArgb)
        setMediaNotificationProvider(provider)
        val playerRepository = app.container.playerRepository
        val musicRepository = app.container.musicRepository
        val player = playerRepository.player
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            // 0.7.6: session callback for the notification like button
            // (see AvaSessionCallback) — the like custom command must
            // be in a controller's available commands or the tap is
            // dropped and the button filtered out of the notification.
            .setCallback(
                AvaSessionCallback(
                    onToggleFavorite = {
                        serviceScope.launch {
                            playerRepository.state.value.currentSong?.let { song ->
                                musicRepository.toggleFavorite(song)
                            }
                        }
                    },
                ),
            )
            .build()
        // 0.5.7: register the session with the service (see the class
        // doc). Without this, Media3 never builds the media notification
        // and never calls startForeground() for this session.
        mediaSession?.let { addSession(it) }
        // 0.7.5: keep the notification accent live. When the user
        // changes the theme (or a custom accent) while the service is
        // running, push the new colour into the provider AND the
        // placeholder field; Media3's next notification refresh picks
        // it up (see AvaMediaNotificationProvider.updateAccentColor).
        // The settings flow already falls back to defaults on error
        // (UserPreferences.settings), so this never throws; the
        // collector is cancelled with serviceScope in onDestroy.
        // Deliberately only the local `provider` is captured — no
        // field, nothing to release.
        // 0.1-fix1 (Track A): a theme change alone now ALSO re-posts
        // the notification — the 0.7.5 "updates on the next refresh"
        // limit is gone. Re-pushing the current media-button
        // preferences forces the rebuild: verified firsthand in the
        // shipped media3-session 1.11.1 bytecode,
        // `MediaSessionImpl.setMediaButtonPreferences` stores the list
        // unconditionally (no equality guard) and dispatches to
        // controllers, and
        // `MediaNotificationManager$MediaControllerListener
        // .onMediaButtonPreferencesChanged` calls
        // `MediaSessionService.onUpdateNotificationInternal(session,
        // false)` — which rebuilds the notification with the new
        // accent. The first emission is the starting accent (the
        // notification is built with it anyway), so it is skipped.
        serviceScope.launch {
            var firstEmission = true
            app.container.userPreferences.settings
                .map { notificationAccentArgb(it.appTheme, it.customAccentArgb) }
                .distinctUntilChanged()
                .collect { accent ->
                    accentColorArgb = accent
                    provider.updateAccentColor(accent)
                    if (firstEmission) {
                        firstEmission = false
                    } else {
                        mediaSession?.setMediaButtonPreferences(lastMediaButtonPreferences)
                    }
                }
        }
        // 0.7.6: keep the notification like button live. The button
        // shows in the notification's EXPANDED view only (compact
        // view has three slots, all taken by transport); its heart
        // fills when the current song is a favourite. Pushing
        // preferences rebuilds the notification (Media3 refreshes on
        // preferences change), so the icon follows the favourite
        // state. distinctUntilChanged on (songId, isFavorite) so
        // unchanged state never re-posts the notification. No song
        // loaded means no like button at all (empty preferences).
        serviceScope.launch {
            combine(
                playerRepository.state.map { it.currentSong?.id },
                musicRepository.observeFavorites()
                    .map { favorites -> favorites.map { it.id }.toSet() },
            ) { songId, favoriteIds ->
                songId to (songId != null && songId in favoriteIds)
            }
                .distinctUntilChanged()
                .collect { (songId, isFavorite) ->
                    val session = mediaSession ?: return@collect
                    // 0.1-fix1 (Track A): remember the preferences so
                    // the accent collector can re-push them after a
                    // theme change and force a notification rebuild.
                    lastMediaButtonPreferences = if (songId == null) {
                        emptyList()
                    } else {
                        listOf(
                            likeButton(
                                isFavorite = isFavorite,
                                addLabel = getString(R.string.player_favorite_add),
                                removeLabel = getString(R.string.player_favorite_remove),
                            ),
                        )
                    }
                    session.setMediaButtonPreferences(lastMediaButtonPreferences)
                }
        }
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
     * Reads the user's accent for the notification colour. DataStore has
     * no synchronous read; the value is normally already loaded (the app
     * container read it at startup), so this is a fast cached read, capped
     * at 500 ms with a Violet fallback. A changed accent is also picked
     * up live by the settings collector in [onCreate], so this is only
     * the starting value. Never throws (safe mode: the container
     * is not initialised yet).
     */
    private fun resolveNotificationAccent(app: AvaApplication): Int {
        val settings = runCatching {
            runBlocking {
                withTimeoutOrNull(500) { app.container.userPreferences.settings.first() }
            }
        }.getOrNull()
        return settings?.let { notificationAccentArgb(it.appTheme, it.customAccentArgb) }
            ?: notificationAccentArgb(Accent.VIOLET, null)
    }

    /**
     * Posts a minimal placeholder notification and promotes the service
     * to the foreground right now. Media3 replaces this notification
     * (same id) with the real media notification once playback starts,
     * and demotes the service (stopForeground, notification kept) when
     * playback pauses — the standard Media3 behaviour from then on.
     *
     * 0.6.0: the placeholder wears Ava's look too (own small icon,
     * accent colour, playback channel name) so the brief flash before
     * Media3's notification arrives does not look like the stock one.
     * NOTE on the channel name: Android keeps the name a channel was
     * first created with, so installs that already ran 0.5.7 keep the
     * old ("Ava") name; fresh installs get "Music playback".
     */
    private fun startForegroundNow() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 0.1: the channel now carries a description (shown in system
        // Settings > Notifications), so the channel explains itself.
        // Like the name (see the KDoc above), the description only
        // appears on a fresh install, or after the user clears data.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.player_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.player_notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ava_notification)
            .setColor(accentColorArgb)
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
        // Round 8: save the resume checkpoint FIRST (the position is
        // still valid here), then pause — do NOT stop() the player.
        // stop() resets the player to IDLE with no current media item
        // (verified from the Media3 1.11.1 bytecode), and this service
        // is destroyed when the user dismisses the media notification
        // (on some devices the dismiss stops the service): the
        // repository still knew the queue, but the player had no media
        // items, so in-app play did nothing until a new song was
        // picked. Pausing keeps the playlist + position in the shared
        // player, so the next play resumes the same song from the same
        // spot (and PlayerRepository.ensurePlayable rebuilds the
        // playlist if it was reset some other way). The explicit stop
        // path (PlayerRepository.stop()) still calls player.stop().
        val app = application as AvaApplication
        val playerRepository = app.container.playerRepository
        playerRepository.saveCheckpointNow()
        playerRepository.player.pause()
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
