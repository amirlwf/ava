package com.harper.ava.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.harper.ava.MainActivity
import com.harper.ava.R
import com.harper.ava.core.util.deriveAccentColors
import com.harper.ava.core.util.formatDuration
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.domain.model.Song
import com.harper.ava.ui.theme.spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ava's fully custom media notification (0.7.2) — a from-scratch
 * implementation of Media3's [MediaNotification.Provider] interface,
 * replacing the 0.6.0 [DefaultMediaNotificationProvider] subclass.
 *
 * Why from scratch: the stock provider's `createNotification()` is
 * `final`, so the layout itself (MediaStyle, large icon, content
 * intent) could never be rebuilt — only icons and the accent colour
 * could change. This provider builds the notification itself with
 * Ava's own [RemoteViews] layouts (compact + expanded, Ava transport
 * drawables, theme accent, big rounded artwork, a progress bar with
 * elapsed/total times) and can therefore also do the one thing the
 * subclass never could: `setOngoing(true)` while playing, so swiping
 * the notification away does not stop the music. While paused the
 * notification is dismissible again (`ongoing = false`), the standard
 * media behaviour.
 *
 * What this provider does NOT do (honest limits, read off the shipped
 * Media3 1.11.1 AAR with `javap`, not guessed):
 *
 * - Custom font: a notification is rendered by SystemUI with the
 *   system font. RemoteViews text cannot carry an app typeface, and
 *   there is no `setTypeface` on the builder. The personality here
 *   comes from colour, layout, icons and artwork — the in-app UI
 *   keeps Vazirmatn / Lalezar.
 * - The shade's built-in media player (MediaStyle seek bar, output
 *   switcher) is gone with MediaStyle: [DecoratedCustomViewStyle]
 *   keeps the standard decorated frame (small icon, app name) but the
 *   body is ours. Our own progress bar is display-only — RemoteViews
 *   has no interactive seek bar — and it only moves when the
 *   notification is rebuilt: on player events (play / pause / track
 *   change / seek) via Media3's manager, plus a 1-second ticker
 *   below while playing.
 * - Rounded artwork corners: the bitmap is rounded HERE (decoded
 *   from the song's album-art Uri and corner-rounded before it is
 *   put in the ImageView). The lock-screen artwork path is untouched:
 *   this provider never touches the session's BitmapLoader, which
 *   still feeds the lock screen exactly as before.
 * - The like icon starts as "not liked" and corrects itself a moment
 *   later: the favorite state is read fresh from the database
 *   ([MusicRepository.getSongById]) because the queue's [Song]
 *   snapshot carries a stale `isFavorite` (round-8 note in
 *   MusicRepository).
 *
 * Wiring (see [AvaPlaybackService]): the service constructs this
 * provider with the accent colour, the repository and a lambda that
 * reads the current song from the shared PlayerRepository state, and
 * passes it to `setMediaNotificationProvider()` BEFORE `addSession()`.
 * Notification id (1001) and channel (`ava_playback`) are unchanged,
 * so the placeholder the service posts in `onStartCommand` is still
 * replaced by this notification instead of stacking a second one.
 * The service must call [release] from its `onDestroy()` so the
 * ticker and the coroutine scope stop with the service.
 */
class AvaMediaNotificationProvider(
    private val context: Context,
    private val accentColorArgb: Int,
    private val musicRepository: MusicRepository,
    private val currentSong: () -> Song?,
) : MediaNotification.Provider {

    companion object {
        /** Custom action string for the like button (expanded view). */
        const val ACTION_TOGGLE_FAVORITE = "com.harper.ava.action.TOGGLE_FAVORITE"

        /** Artwork decode target (px) and corner radius (px at that size). */
        const val ARTWORK_TARGET_PX = 256
        const val ARTWORK_CORNER_RADIUS_PX = 28f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressHandler = Handler(Looper.getMainLooper())

    // Last build, kept so the progress ticker, the async artwork load
    // and the like toggle can ask Media3's manager for a rebuild.
    private var lastSession: MediaSession? = null
    private var lastButtons: ImmutableList<CommandButton>? = null
    private var lastActionFactory: MediaNotification.ActionFactory? = null
    private var lastCallback: MediaNotification.Provider.Callback? = null

    // Favorite state of the current song (fresh DB read, see class doc).
    private var favoriteSongId: Long? = null
    private var favoriteIsFavorite: Boolean = false
    private var favoriteKnown: Boolean = false

    // Rounded artwork bitmap for the current song (decoded off-thread).
    private var artworkSongId: Long? = null
    private var artworkBitmap: Bitmap? = null
    private var artworkLoadingSongId: Long? = null

    private val progressTicker = object : Runnable {
        override fun run() {
            val session = lastSession
            if (session == null || !session.player.isPlaying) return
            refreshViaCallback()
            progressHandler.postDelayed(this, 1000L)
        }
    }

    override fun getNotificationChannelInfo():
        MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            AvaPlaybackService.CHANNEL_ID,
            context.getString(R.string.player_notification_channel),
        )

    override fun createNotification(
        session: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        lastSession = session
        lastButtons = mediaButtons
        lastActionFactory = actionFactory
        lastCallback = callback

        val player = session.player
        val song = currentSong()
            // Fall back to the player's current item when the repository
            // state has not caught up yet (e.g. service just created).
            ?: player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                Song(
                    id = id,
                    title = player.mediaMetadata.title?.toString().orEmpty(),
                    artist = player.mediaMetadata.artist?.toString().orEmpty(),
                    album = player.mediaMetadata.albumTitle?.toString().orEmpty(),
                    durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                    uri = player.currentMediaItem?.localConfiguration?.uri.toString(),
                )
            }

        val isPlaying = player.isPlaying

        if (song == null) {
            // No track at all (idle player): a plain, dismissible
            // notification — never a custom view with dead buttons.
            progressHandler.removeCallbacks(progressTicker)
            val notification = NotificationCompat.Builder(context, AvaPlaybackService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ava_notification)
                .setColor(accentColorArgb)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.now_playing_title))
                .setContentIntent(contentIntent())
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            return MediaNotification(AvaPlaybackService.NOTIFICATION_ID, notification)
        }

        refreshFavoriteState(song)
        refreshArtwork(song)

        val compact = buildRemoteViews(
            layoutRes = R.layout.notification_ava_compact,
            session = session,
            actionFactory = actionFactory,
            song = song,
            player = player,
            isPlaying = isPlaying,
            withLikeButton = false,
        )
        val expanded = buildRemoteViews(
            layoutRes = R.layout.notification_ava_expanded,
            session = session,
            actionFactory = actionFactory,
            song = song,
            player = player,
            isPlaying = isPlaying,
            withLikeButton = true,
        )

        val notification = NotificationCompat.Builder(context, AvaPlaybackService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ava_notification)
            .setColor(accentColorArgb)
            .setContentTitle(song.title)
            .setContentText(notificationSecondLine(song))
            .setContentIntent(contentIntent())
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            // The point of the custom provider: while playing the
            // notification cannot be swiped away (and swiping cannot
            // stop the music); paused it is dismissible again.
            .setOngoing(notificationIsOngoing(isPlaying))
            .build()

        // Progress only moves on rebuilds: Media3 rebuilds on player
        // events, and this ticker rebuilds once a second while playing.
        progressHandler.removeCallbacks(progressTicker)
        if (isPlaying) progressHandler.postDelayed(progressTicker, 1000L)

        return MediaNotification(AvaPlaybackService.NOTIFICATION_ID, notification)
    }

    /**
     * The like button is a custom action: Media3's manager routes its
     * PendingIntent back here (and forwards anything unhandled to the
     * controller as a session custom command — hence `false` for
     * anything that is not ours).
     */
    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean {
        if (action != ACTION_TOGGLE_FAVORITE) return false
        val song = currentSong() ?: return true
        scope.launch {
            val fresh = runCatching { musicRepository.getSongById(song.id) }.getOrNull() ?: song
            runCatching { musicRepository.toggleFavorite(fresh) }
            // toggleFavorite flips the DB state read just above.
            favoriteSongId = song.id
            favoriteIsFavorite = !fresh.isFavorite
            favoriteKnown = true
            refreshViaCallback()
        }
        return true
    }

    /** Stops the ticker and the scope. Called from the service's `onDestroy()`. */
    fun release() {
        progressHandler.removeCallbacks(progressTicker)
        scope.cancel()
        lastSession = null
        lastButtons = null
        lastActionFactory = null
        lastCallback = null
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildRemoteViews(
        layoutRes: Int,
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        song: Song,
        player: Player,
        isPlaying: Boolean,
        withLikeButton: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)

        views.setTextViewText(R.id.notif_title, song.title.ifBlank { context.getString(R.string.app_name) })
        views.setTextViewText(R.id.notif_artist, notificationSecondLine(song))

        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song.durationMs
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        views.setTextViewText(R.id.notif_elapsed, formatDuration(positionMs))
        views.setTextViewText(R.id.notif_total, formatDuration(durationMs))
        views.setProgressBar(R.id.notif_progress, 100, notificationProgressPercent(positionMs, durationMs), false)
        views.setColorStateList(
            R.id.notif_progress,
            "setProgressTintList",
            ColorStateList.valueOf(accentColorArgb),
        )

        val artBitmap = artworkBitmap.takeIf { artworkSongId == song.id }
        if (artBitmap != null) {
            views.setImageViewBitmap(R.id.notif_art, artBitmap)
        } else {
            views.setImageViewResource(R.id.notif_art, R.drawable.ic_notif_art_placeholder)
        }

        // Transport: previous / play-or-pause / next, with Ava's own
        // drawables. The PendingIntents come from Media3's
        // ActionFactory, so the taps are routed to the player exactly
        // like the stock provider's actions (no custom handling).
        val prevAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, R.drawable.ic_notif_prev),
            context.getString(R.string.notif_action_previous),
            Player.COMMAND_SEEK_TO_PREVIOUS,
        )
        val playPauseAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(
                context,
                notificationPlayPauseIconRes(isPlaying),
            ),
            context.getString(
                if (isPlaying) R.string.notif_action_pause else R.string.notif_action_play,
            ),
            Player.COMMAND_PLAY_PAUSE,
        )
        val nextAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, R.drawable.ic_notif_next),
            context.getString(R.string.notif_action_next),
            Player.COMMAND_SEEK_TO_NEXT,
        )
        views.setOnClickPendingIntent(R.id.notif_prev, prevAction.actionIntent)
        views.setOnClickPendingIntent(R.id.notif_play_pause, playPauseAction.actionIntent)
        views.setOnClickPendingIntent(R.id.notif_next, nextAction.actionIntent)
        // Accent circle behind the play/pause button: a white circle
        // drawable tinted with the theme accent. (Prev/next sit on a
        // dark translucent circle so the white icons stay readable on
        // both light and dark notification backgrounds.)
        views.setInt(R.id.notif_play_bg, "setColorFilter", accentColorArgb)

        if (withLikeButton) {
            val likeIcon = notificationLikeIconRes(favoriteKnown && favoriteIsFavorite)
            val likeAction = actionFactory.createCustomAction(
                session,
                IconCompat.createWithResource(context, likeIcon),
                context.getString(R.string.notif_action_like),
                ACTION_TOGGLE_FAVORITE,
                Bundle.EMPTY,
            )
            views.setOnClickPendingIntent(R.id.notif_like, likeAction.actionIntent)
            views.setImageViewResource(R.id.notif_like, likeIcon)
            if (favoriteKnown && favoriteIsFavorite) {
                // Liked: the filled heart takes the theme accent.
                views.setInt(R.id.notif_like, "setColorFilter", accentColorArgb)
            } else {
                // Not liked: plain white outline heart.
                views.setInt(R.id.notif_like, "setColorFilter", 0xFFFFFFFF.toInt())
            }
        }

        return views
    }

    /** Second line: artist, falling back to the album (0.6.0 behaviour). */
    private fun notificationSecondLine(song: Song): String =
        song.artist.takeIf { it.isNotBlank() }
            ?: song.album.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notif_unknown_artist)

    /** Fresh favorite state for [song]; rebuilds once the DB answers. */
    private fun refreshFavoriteState(song: Song) {
        if (favoriteKnown && favoriteSongId == song.id) return
        favoriteSongId = song.id
        favoriteKnown = false
        favoriteIsFavorite = false
        scope.launch {
            val fresh = runCatching { musicRepository.getSongById(song.id) }.getOrNull()
            if (fresh != null && favoriteSongId == song.id) {
                val changed = !favoriteKnown || favoriteIsFavorite != fresh.isFavorite
                favoriteIsFavorite = fresh.isFavorite
                favoriteKnown = true
                if (changed) refreshViaCallback()
            }
        }
    }

    /** Rounded artwork for [song], decoded off the main thread. */
    private fun refreshArtwork(song: Song) {
        val uriString = song.albumArtUri
        if (uriString.isNullOrBlank()) {
            artworkSongId = song.id
            artworkBitmap = null
            return
        }
        if (artworkSongId == song.id && (artworkBitmap != null || artworkLoadingSongId == song.id)) return
        artworkSongId = song.id
        artworkBitmap = null
        artworkLoadingSongId = song.id
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeRoundedArtwork(uriString) }.getOrNull()
            }
            if (artworkLoadingSongId == song.id) {
                artworkLoadingSongId = null
                if (bitmap != null) {
                    artworkBitmap = bitmap
                    refreshViaCallback()
                }
            }
        }
    }

    /**
     * Decodes the album art at [ARTWORK_TARGET_PX] and rounds its
     * corners. Runs on [Dispatchers.IO] (called from [refreshArtwork]).
     * The session's BitmapLoader — which feeds the lock screen — is
     * never touched.
     */
    private fun decodeRoundedArtwork(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sampleSize = 1
        while (width / sampleSize > ARTWORK_TARGET_PX || height / sampleSize > ARTWORK_TARGET_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val output = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(
            RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat()),
            ARTWORK_CORNER_RADIUS_PX * decoded.width / ARTWORK_TARGET_PX,
            ARTWORK_CORNER_RADIUS_PX * decoded.width / ARTWORK_TARGET_PX,
            paint,
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(decoded, 0f, 0f, paint)
        if (decoded != output) decoded.recycle()
        return output
    }

    /** Rebuilds the notification through the last callback, if any. */
    private fun refreshViaCallback() {
        val session = lastSession
        val buttons = lastButtons
        val factory = lastActionFactory
        val callback = lastCallback
        if (session == null || buttons == null || factory == null || callback == null) return
        runCatching { callback.onNotificationChanged(createNotification(session, buttons, factory, callback)) }
    }
}

/**
 * Notification accent colour (ARGB) for the user's theme choice.
 *
 * A user-picked custom colour wins (same derivation as the in-app theme,
 * [deriveAccentColors]); otherwise the palette primary. The hex values
 * are the primaries of [com.harper.ava.ui.theme.accentPalette] in
 * `ui/theme/Color.kt` — if a palette primary ever changes there, this
 * must change with it (there is no Compose-free way to read the palette
 * from the service; the test below pins the values).
 *
 * Public (not `internal`): the offline `verify.sh` compiles the tests
 * as a separate module, where `internal` main declarations are not
 * visible.
 */
fun notificationAccentArgb(accent: Accent, customAccentArgb: Int?): Int =
    customAccentArgb?.let { deriveAccentColors(it).primary }
        ?: when (accent) {
            Accent.VIOLET -> 0xFF7C5CFF.toInt()
            Accent.ELECTRIC_BLUE -> 0xFF2E7CF6.toInt()
            Accent.EMERALD -> 0xFF10B981.toInt()
            Accent.CRIMSON -> 0xFFE11D48.toInt()
            Accent.AMBER -> 0xFFF59E0B.toInt()
            Accent.ROSE -> 0xFFF43F5E.toInt()
            Accent.MONOCHROME -> 0xFFE4E4E7.toInt()
        }

/**
 * 0.7.0: notification accent from the chosen [AppTheme] — the theme's
 * dark-mode primary, so the notification matches the in-app theme. A
 * custom colour still wins. The [Accent] overload above is kept
 * unchanged (PlaybackServiceTest pins it); the service should call this
 * one with `settings.appTheme` (see `AvaPlaybackService` wiring — the
 * service file itself is owned by another agent in this round).
 */
fun notificationAccentArgb(appTheme: AppTheme, customAccentArgb: Int?): Int =
    customAccentArgb?.let { deriveAccentColors(it).primary }
        ?: appTheme.spec.primaryDark.toInt()

/**
 * Ava's own drawable for a Media3 media-button icon constant, or null to
 * keep Media3's stock icon. Only the three buttons the stock provider
 * emits are mapped (previous / play / pause / next); everything else
 * (stop, skip, shuffle, ...) intentionally keeps the stock icon.
 *
 * Public (not `internal`): see [notificationAccentArgb] — the offline
 * `verify.sh` compiles the tests as a separate module.
 */
fun avaNotificationIconRes(iconConstant: Int): Int? = when (iconConstant) {
    CommandButton.ICON_PREVIOUS -> R.drawable.ic_notif_prev
    CommandButton.ICON_PLAY -> R.drawable.ic_notif_play
    CommandButton.ICON_PAUSE -> R.drawable.ic_notif_pause
    CommandButton.ICON_NEXT -> R.drawable.ic_notif_next
    else -> null
}

/**
 * Ongoing rule for the custom notification: while playing the
 * notification cannot be swiped away (and swiping cannot stop the
 * music); paused it is dismissible again — the standard media
 * behaviour. Pure, unit-tested in CustomNotificationLogicTest.
 */
fun notificationIsOngoing(isPlaying: Boolean): Boolean = isPlaying

/** Play/pause button icon: pause while playing, play while paused. */
fun notificationPlayPauseIconRes(isPlaying: Boolean): Int =
    if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play

/** Like button icon: filled heart when liked, outline otherwise. */
fun notificationLikeIconRes(isFavorite: Boolean): Int =
    if (isFavorite) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart

/** Progress percentage (0..100) for the notification progress bar. */
fun notificationProgressPercent(positionMs: Long, durationMs: Long): Int {
    if (durationMs <= 0L) return 0
    val clamped = positionMs.coerceIn(0L, durationMs)
    return ((clamped * 100L) / durationMs).toInt().coerceIn(0, 100)
}
