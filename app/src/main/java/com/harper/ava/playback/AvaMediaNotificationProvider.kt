package com.harper.ava.playback

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.harper.ava.R
import com.harper.ava.core.util.deriveAccentColors
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.ui.theme.spec

/**
 * Ava's own media notification (0.6.0 design round).
 *
 * 0.7.3 — REVERTED to this 0.6.0 subclass: the from-scratch custom
 * RemoteViews provider shipped in 0.7.2 crashed on play on Harper's
 * Redmi Note 12 / Android 15
 * (`CannotPostForegroundServiceNotificationException: Bad notification
 * for startForeground`, no app frame in the stack — the system
 * rejected the notification). The two never-device-verified suspects
 * were the vector small icon and the custom RemoteViews path, so both
 * risk factors are removed together: this file is the 0.6.0 subclass
 * verbatim (Media3's final `createNotification()`, MediaStyle,
 * system rendering — the same path that played fine on this phone in
 * 0.5.7), plus the `notificationAccentArgb(AppTheme, ...)` overload
 * added in 0.7.0, and the small icon is now a raster PNG silhouette
 * (`res/drawable-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi/ic_ava_notification.png`)
 * instead of the vector.
 *
 * 0.7.4 — the verdict, and 0.7.5 — this file, reverted AGAIN. The
 * 0.7.4 retry rebuilt the from-scratch RemoteViews provider with the
 * 0.7.3 PNG small icon (the vector icon was the suspect). It crashed
 * on play on Harper's Redmi Note 12 / Android 15 with the IDENTICAL
 * exception (`CannotPostForegroundServiceNotificationException: Bad
 * notification for startForeground`, +44066ms, crash log
 * `workspace/user/files/ava-crash-log__14__1_p9ak.txt`). The vector
 * hypothesis is therefore DISPROVEN: **the RemoteViews path itself is
 * the cause**, on this phone (HyperOS / Android 15). The 0.7.4 KDoc
 * revert rule applies: this file is the 0.7.3 subclass again, and a
 * from-scratch RemoteViews notification provider must NEVER be
 * repeated for this app — two different icons, same crash, same
 * phone. Do not "retry with one more tweak".
 *
 * 0.7.5 — what Ava's design DOES get, inside the safe subclass path:
 *
 * - Live accent: the accent is no longer read only at service
 *   creation (the 0.6.0 flaw). [updateAccentColor] lets the service
 *   push the current theme's accent whenever the settings change, and
 *   `createNotification()` is called by Media3 on every notification
 *   refresh, so later notifications take the new colour. Honest
 *   limit: a theme change alone does NOT re-post the notification —
 *   there is no public Media3 hook to force a re-post from the
 *   subclass — so the visible notification picks the new accent up
 *   on the next refresh (play / pause / track change), not the
 *   instant the theme changes.
 * - Rounded artwork: the reason it was NOT done in 0.7.5 was verified,
 *   not assumed — `PlayerRepository.Song.toMediaItem()` set NO
 *   `MediaMetadata` at all (uri + media id only), so Media3 had no
 *   `artworkData` / `artworkUri` to load a large icon from. Improve
 *   loop, run 1 (2026-11-12) is the separate change the 0.7.5 note
 *   deferred: `core/util/MediaItemLogic.songToMediaItem()` now puts
 *   title / artist / album / artwork uri on every media item, so the
 *   notification and the lock screen finally show the song. Rounding
 *   the bitmap is STILL deliberately not done: the large icon is
 *   loaded inside the final `createNotification()` from the session's
 *   `BitmapLoader`, which also feeds the lock-screen artwork (rounded
 *   corners there would show as transparent wedges on a full-bleed
 *   image), and SystemUI draws the notification's large icon in its
 *   own frame on modern Android.
 * - `setColorized(true)`: NOT set, and NOT needed — corrected in
 *   0.7.6 with evidence (the 0.7.5 note below got the semantics
 *   backwards). Per developer.android.com (`Notification.MediaStyle`):
 *   "Starting at Android O any notification that has a media session
 *   attached with `setMediaSession(MediaSession.Token)` will be
 *   colorized. You can opt-out of this behavior by using
 *   `Notification.Builder.setColorized(boolean)`." So a MediaStyle
 *   notification is colorized BY DEFAULT: `setColorized(true)` is a
 *   no-op and `setColorized(false)` would REMOVE the tint. The
 *   accent tint from `setColor` in [addNotificationActions] is
 *   therefore already live wherever the system uses the notification
 *   colour. On Android 13+ the shade's media player additionally
 *   derives its background from the artwork, which the app cannot
 *   override. Personality comes from `setColor` (small-icon tint)
 *   + Ava's own action drawables + the PNG small icon instead.
 *   Revisit only with a device check.
 *
 * 0.7.6 — the notification is Ava's own, still inside this safe
 * subclass path (nothing here is RemoteViews, nothing is a
 * from-scratch provider):
 *
 * - Artwork: since improve loop run 1 every media item carries
 *   title / artist / album / artwork uri, so the notification and
 *   the lock screen show the song. Verified from the 1.11.1
 *   SOURCES (not bytecode): the session's default BitmapLoader is
 *   `CacheBitmapLoader(DataSourceBitmapLoader)` (DefaultDataSource
 *   — content:// album-art URIs load), and the provider wraps it
 *   in `CacheBitmapLoader(SizeLimitedBitmapLoader(loader,
 *   maxNotificationIconSize, makeShared=true))`, so the large icon
 *   is capped to the system icon size and made shared for Binder
 *   transfer — the huge-bitmap Binder risk is already handled by
 *   the stock provider. A failed artwork load is caught (log
 *   warning, no large icon) and can never crash the post. No
 *   custom BitmapLoader is needed or added.
 * - Title: never blank. [songMediaMetadata] guarantees a title
 *   (file-name fallback), and [getNotificationContentTitle] falls
 *   back to the album as a last defence before the notification
 *   posts. The scanner (0.7.6, `normalizeScannedSong`) also
 *   prefers the file's DISPLAY_NAME over the numeric MediaStore id
 *   segment, so untagged songs are titled with their file name
 *   instead of "123" (fixed on the next rescan for songs already
 *   in the library).
 * - Like button: a heart action in the notification's EXPANDED
 *   view (compact view has only three slots, all taken by
 *   transport) via a MediaSession custom command — see
 *   `NotificationLikeLogic` / `AvaSessionCallback`. Stock Media3
 *   plumbing only: the button is built with
 *   `CommandButton.ICON_HEART_FILLED/UNFILLED` + Ava's own heart
 *   drawables, and tapping it toggles the current song's
 *   favourite state. Its failure modes are benign by
 *   construction: if the command is not in the controller's
 *   available commands the button is disabled and filtered out
 *   (absent, not broken), and an unhandled tap is a no-op — the
 *   notification itself is still built by the same final
 *   `createNotification()` and cannot fail over the like button.
 *
 * Honest trade-off of this revert: the subclass CANNOT do
 * `setOngoing(true)` while playing — Media3's final
 * `createNotification()` forces ongoing false — so swiping the
 * notification away while playing is possible again. Playback loss
 * on dismiss is still guarded by the round-8 fix (pause, never
 * stop(), in the service's `onDestroy()` + queue rebuild), not by
 * ongoing. The custom provider's progress bar and rounded artwork
 * go away with it.
 *
 * The 0.5.7 notification was the stock Media3 one. This subclass keeps
 * Media3's behaviour (same three buttons, same compact-view slots, same
 * MediaStyle rendering, same notification id / channel) and only changes
 * the look, through the hooks Media3 1.11.1 actually offers. The API
 * surface was read off the shipped 1.11.1 AAR
 * (`.build-tools/aar-manifests/media3-session-1.11.1.aar`, `javap`),
 * not guessed:
 *
 * - `DefaultMediaNotificationProvider` is subclassable; its
 *   `createNotification()` is `final`, so the layout itself (MediaStyle,
 *   large icon, content intent, delete intent) cannot be rebuilt — only
 *   the `protected` hooks below can change anything.
 * - `MediaSessionService.setMediaNotificationProvider(provider)` is the
 *   (protected, final) wiring point; the service calls it in `onCreate()`
 *   before `addSession()`.
 * - `setSmallIcon(int)` (public, final) swaps the stock Media3 note for
 *   Ava's own mark.
 * - `getMediaButtons(...)` (protected) decides which buttons exist and
 *   which drawable each one uses; the icons are taken from
 *   `CommandButton.iconResId`, so rebuilding a button with an Ava
 *   drawable is the supported way to restyle the actions. The set is
 *   deliberately unchanged — previous / play-or-pause / next, no stop
 *   button (the stock provider has none; adding one would change what
 *   dismissing / stopping does, which is the parallel bug-fix agent's
 *   area, not this design round's).
 * - `addNotificationActions(...)` (protected) receives the
 *   `NotificationCompat.Builder` before it is built, which is the only
 *   place `setColor(accent)` can be applied — the stock provider never
 *   calls `setColor` itself, so the default notification is uncoloured.
 * - `getNotificationContentTitle/Text(...)` (protected) format the two
 *   lines; the stock title (`metadata.title`) is already right, the
 *   stock text is `metadata.artist` with no fallback.
 *
 * What is deliberately NOT customised here, because Media3 1.11.1 gives
 * no hook for it:
 *
 * - Custom font (Dana / Vazirmatn): a standard notification is rendered
 *   by SystemUI with the system font. `RemoteViews` text cannot carry an
 *   app typeface in a `MediaStyle` notification, and there is no
 *   `setTypeface` on the builder. The in-app UI keeps Vazirmatn; the
 *   notification uses the system font like every other media app.
 * - Rounded artwork corners: the large icon is loaded inside the final
 *   `createNotification()` from the session's `BitmapLoader` and passed
 *   to `setLargeIcon()`; SystemUI draws it in its own frame (rounded by
 *   the system on modern Android). Rounding the bitmap in a custom
 *   `BitmapLoader` was rejected: that loader also feeds the lock-screen
 *   artwork, where rounded corners would show as transparent wedges on
 *   a full-bleed image. Since improve loop run 1 (2026-11-12) artwork
 *   does arrive in the notification (via `songToMediaItem`), so this
 *   is now a live decision, not a missing-input one — and the decision
 *   stands.
 * - A fully custom layout: `MediaStyle` is rendered by SystemUI, not by
 *   the app; a `DecoratedCustomViewStyle` replacement would lose the
 *   shade's media-player integration (seek bar, output switcher).
 *
 * The accent colour starts from the user's theme at service creation
 * ([notificationAccentArgb]) and is then kept live by the service via
 * [updateAccentColor] (see the 0.7.5 note above). `setColor` tints the
 * small icon and, because MediaStyle is colorized by default (see
 * the 0.7.6 `setColorized` note above), tints the notification where
 * the system uses the notification colour. On Android 13+ the
 * shade's media player additionally derives its own colours from
 * the artwork, which the app cannot override.
 */
class AvaMediaNotificationProvider(
    context: Context,
    private var accentColorArgb: Int,
) : DefaultMediaNotificationProvider(
    context,
    // Same id as the service placeholder, so Media3 replaces it instead
    // of stacking a second notification (guarded by PlaybackServiceTest).
    NotificationIdProvider { AvaPlaybackService.NOTIFICATION_ID },
    AvaPlaybackService.CHANNEL_ID,
    // Existing channel name ("Music playback"); see the service — Android
    // keeps the name a channel was first created with, so installs that
    // already ran 0.5.7 keep the old name until the channel is recreated.
    R.string.player_notification_channel,
) {

    init {
        setSmallIcon(R.drawable.ic_ava_notification)
    }

    /**
     * 0.7.5: replaces the accent with the user's CURRENT theme accent.
     * Called by the service whenever the settings change (see
     * `AvaPlaybackService`); Media3 calls `createNotification()` on
     * every notification refresh, so the next notification posted
     * after this call uses the new colour via `setColor` in
     * [addNotificationActions]. A theme change alone does not re-post
     * the notification, so the visible one updates on the next
     * refresh (play / pause / track change) — see the class KDoc.
     * Plain setter on purpose: the provider has no coroutine scope
     * of its own (the 0.7.4 ticker scope died with the RemoteViews
     * provider), and the service owns the settings collection.
     */
    fun updateAccentColor(newArgb: Int) {
        accentColorArgb = newArgb
    }

    /**
     * Same buttons as the stock provider, in the same order with the same
     * slots and compact-view indices — only the drawables are Ava's.
     * Buttons that carry a session command (custom layout from the
     * system's media-button preferences) are left untouched.
     */
    override fun getMediaButtons(
        session: MediaSession,
        commands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPlayButtonIfPlaybackIsSuppressed: Boolean,
    ): ImmutableList<CommandButton> {
        val buttons = super.getMediaButtons(
            session,
            commands,
            customLayout,
            showPlayButtonIfPlaybackIsSuppressed,
        )
        return ImmutableList.copyOf(buttons.map { button ->
            val avaIcon = avaNotificationIconRes(button.icon)
            if (avaIcon == null || button.sessionCommand != null) return@map button
            // Rebuild the button field-for-field; Builder(int) keeps the
            // Media3 icon constant (so getDefaultSlot() still agrees with
            // the stock provider) while setCustomIconResId() swaps in the
            // Ava drawable — verified in the 1.11.1 bytecode, it stores
            // the resource id verbatim (setIconResId merely delegates to
            // it and is deprecated).
            // Slots are copied, so the compact view shows the same
            // buttons as before; build() would re-derive them anyway.
            CommandButton.Builder(button.icon)
                .setPlayerCommand(button.playerCommand)
                .setCustomIconResId(avaIcon)
                .setDisplayName(button.displayName)
                .setExtras(button.extras)
                .setEnabled(button.isEnabled)
                .apply {
                    if (button.slots.length() > 0) setSlots(*button.slots.toArray())
                }
                .build()
        })
    }

    /**
     * The stock actions, then the one accent hook Media3 offers:
     * `setColor` tints the small icon with Ava's accent (and, because
     * MediaStyle is colorized by default, tints the notification
     * wherever the system uses the notification colour — see the
     * 0.7.6 `setColorized` note in the class KDoc).
     */
    override fun addNotificationActions(
        session: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        val compactViewIndices =
            super.addNotificationActions(session, mediaButtons, builder, actionFactory)
        builder.setColor(accentColorArgb)
        return compactViewIndices
    }

    /**
     * Stock title is `metadata.title` with no fallback — an untagged
     * song would post an empty first line. The metadata layer
     * ([songMediaMetadata]) already guarantees a title, but this is
     * the last line of defence before the notification posts: fall
     * back to the album (see [notificationContentTitle]).
     */
    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
        notificationContentTitle(metadata)

    /**
     * Stock text is the artist and nothing else; a file whose tags carry
     * only an album fell back to a blank line. The title is handled by
     * [getNotificationContentTitle] above.
     */
    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
        metadata.artist?.takeIf { it.isNotBlank() } ?: metadata.albumTitle
}

/**
 * Notification title for [metadata] — never blank (0.7.6).
 *
 * The stock title is `metadata.title` with no fallback, so an
 * untagged song would post an empty first line. The metadata layer
 * ([songMediaMetadata], via `notificationTitle`) already guarantees
 * a title, but this is the last line of defence before the
 * notification posts: a blank title falls back to the album, and
 * only a song with neither shows no title at all.
 *
 * Public (not `internal`): see [notificationAccentArgb] — the
 * offline `verify.sh` compiles the tests as a separate module.
 */
fun notificationContentTitle(metadata: MediaMetadata): CharSequence? =
    metadata.title?.takeIf { it.isNotBlank() }
        ?: metadata.albumTitle?.takeIf { it.isNotBlank() }

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
 * unchanged (PlaybackServiceTest pins it); the service calls this one
 * with `settings.appTheme` (see `AvaPlaybackService`).
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
