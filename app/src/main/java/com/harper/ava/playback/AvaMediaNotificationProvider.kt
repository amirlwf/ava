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

/**
 * Ava's own media notification (0.6.0 design round).
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
 *   a full-bleed image.
 * - A fully custom layout: `MediaStyle` is rendered by SystemUI, not by
 *   the app; a `DecoratedCustomViewStyle` replacement would lose the
 *   shade's media-player integration (seek bar, output switcher).
 *
 * The accent colour is resolved once, when the service is created
 * ([notificationAccentArgb]); a changed accent is picked up on the next
 * service creation. `setColor` tints the small icon — on Android 13+
 * the shade's media player additionally derives its own colours from
 * the artwork, which the app cannot override.
 */
class AvaMediaNotificationProvider(
    context: Context,
    private val accentColorArgb: Int,
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
     * `setColor` tints the small icon with Ava's accent. Deliberately no
     * `setColorized(true)` — a fully tinted background would fight the
     * system dark/light theme and the artwork.
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
     * Stock text is the artist and nothing else; a file whose tags carry
     * only an album fell back to a blank line. Title is left at the
     * stock default (`metadata.title`).
     */
    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
        metadata.artist?.takeIf { it.isNotBlank() } ?: metadata.albumTitle
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
