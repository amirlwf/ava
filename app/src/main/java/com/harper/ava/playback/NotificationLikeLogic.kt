package com.harper.ava.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.harper.ava.R

/**
 * The notification like (favourite) button — 0.7.6.
 *
 * A heart action in the notification's EXPANDED view, built entirely
 * from stock Media3 plumbing (no RemoteViews, no from-scratch
 * provider, no custom XML — the RemoteViews path crashed twice on
 * Harper's Redmi Note 12 / Android 15 and is never repeated). The
 * chain, verified in the shipped media3-session 1.11.1 SOURCES:
 *
 * 1. The service pushes this button with
 *    `MediaSession.setMediaButtonPreferences(listOf(likeButton(...)))`.
 *    The session forwards the preferences to its connected
 *    controllers — including the internal notification controller
 *    that `MediaNotificationManager.addSession` builds — and the
 *    manager rebuilds the notification on
 *    `onMediaButtonPreferencesChanged`, so the heart follows the
 *    favourite state live.
 * 2. The provider's stock `getMediaButtons` appends buttons whose
 *    slot is [CommandButton.SLOT_OVERFLOW] after the three
 *    transport buttons (compact view has only three slots, all
 *    taken), and `addNotificationActions` turns the button into a
 *    standard notification action via
 *    `DefaultActionFactory.createCustomActionFromCustomCommandButton`
 *    (icon = `iconResId`, set here with `setCustomIconResId` to
 *    Ava's own heart drawable — the same mechanism as the 0.6.0
 *    transport drawables). The provider's own `getMediaButtons`
 *    remap leaves session-command buttons untouched.
 * 3. A tap travels as a PendingIntent to the service, the manager
 *    forwards it only if the command is in the controller's
 *    available session commands (`sendCustomCommandIfCommandIsAvailable`),
 *    and the session's [AvaSessionCallback.onCustomCommand] toggles
 *    the current song's favourite state.
 *
 * That availability check is why [availableSessionCommandsWithLike]
 * exists: without the like command in the available commands, the
 * tap is dropped silently AND the button is disabled and filtered
 * out of the notification (absent, not broken). The failure modes
 * are benign by construction — a missing or dead like button never
 * breaks the notification post, which is still built by Media3's
 * final `createNotification()` with the MediaStyle path.
 *
 * Test boundary (honest, not a gap to paper over): the Media3-typed
 * assembly in this file — [likeSessionCommand], [likeButton] and
 * [availableSessionCommandsWithLike] — CANNOT run in the offline
 * JUnit suite. Building a `SessionCommand` / `CommandButton`
 * constructs an `android.os.Bundle`, whose stub constructor throws
 * on this JVM, and reading the `DEFAULT_SESSION_COMMANDS` statics
 * initialises `Player.Commands`, which touches
 * `android.util.SparseBooleanArray` (same stub problem); building
 * a button also reads the media3 R class, which the verify
 * environment does not generate. So those three functions are
 * verified by the kotlinc type-check, by dex/javap inspection in
 * the manual build (the grant must call `addSessionCommands`, the
 * button must call `setCustomIconResId` / `setSlots`), and by
 * Harper's device. What IS unit-tested here: the pure decisions
 * ([likeButtonSpec], [isLikeAction], the action string).
 */

/** Custom action string for the notification like button. Pinned by test. */
const val LIKE_COMMAND_ACTION = "com.harper.ava.action.TOGGLE_FAVORITE"

/** True only for the notification like button's custom action. */
fun isLikeAction(action: String?): Boolean = action == LIKE_COMMAND_ACTION

/** The like custom command (one instance per call; compared by action string). */
fun likeSessionCommand(): SessionCommand = SessionCommand(LIKE_COMMAND_ACTION, Bundle.EMPTY)

/**
 * Available session commands for a connecting controller: the
 * platform defaults for that controller PLUS the like custom
 * command.
 *
 * The defaults MUST be kept, not replaced: `AcceptedResultBuilder`
 * starts with the right defaults for the controller (trusted ->
 * [MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS],
 * untrusted -> `DEFAULT_UNTRUSTED_SESSION_COMMANDS`), but
 * `setAvailableSessionCommands` REPLACES them — building a set with
 * only the like command would silently strip the controller of
 * every default session command. This function mirrors the
 * builder's trusted/untrusted choice and adds the like command on
 * top. Pinned by test for both branches.
 */
fun availableSessionCommandsWithLike(isTrusted: Boolean): SessionCommands {
    val base = if (isTrusted) {
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
    } else {
        MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_COMMANDS
    }
    return SessionCommands.Builder()
        .addSessionCommands(base.commands)
        .add(likeSessionCommand())
        .build()
}

/**
 * The like button for the current song.
 *
 * The decisions (which icon constant, which Ava drawable, which
 * label) live in the pure [likeButtonSpec] below, so they are
 * pinned by unit test. The assembly here is a thin Media3 builder
 * chain — it cannot run in the offline JUnit suite (building a
 * `CommandButton` touches `android.os.Bundle`, whose stub
 * constructor throws, and the media3 R class, which the verify
 * environment does not generate), so it is covered by the kotlinc
 * type-check, by dex/javap inspection in the manual build, and by
 * Harper's device — see `NotificationLikeTest`.
 *
 * The icon constant tells controllers what the button is
 * (`ICON_HEART_FILLED` / `ICON_HEART_UNFILLED`); the drawable the
 * notification actually shows is Ava's own heart, swapped in with
 * `setCustomIconResId` (same mechanism as the transport drawables).
 * The button takes the [CommandButton.SLOT_OVERFLOW] slot, so it
 * appears in the expanded notification only — compact view keeps
 * exactly the three transport buttons. The display name is the
 * action the tap performs (accessibility + controllers that show
 * it), using the app's existing favourite strings.
 */
fun likeButton(isFavorite: Boolean, addLabel: String, removeLabel: String): CommandButton {
    val spec = likeButtonSpec(isFavorite, addLabel, removeLabel)
    return CommandButton.Builder(spec.icon)
        .setSessionCommand(likeSessionCommand())
        .setCustomIconResId(spec.iconResId)
        .setDisplayName(spec.label)
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()
}

/** The like button's decisions, as pure data (unit-tested). */
data class LikeButtonSpec(
    /** Media3 icon constant (`ICON_HEART_FILLED` / `ICON_HEART_UNFILLED`). */
    val icon: Int,
    /** Ava drawable the notification actually draws. */
    val iconResId: Int,
    /** Display name = the action the tap performs. */
    val label: String,
)

/**
 * Pure decisions for [likeButton]: which heart constant, which Ava
 * drawable, which label — for a favourited song the filled heart
 * and the "remove" label, otherwise the outline heart and the
 * "add" label.
 */
fun likeButtonSpec(isFavorite: Boolean, addLabel: String, removeLabel: String): LikeButtonSpec =
    LikeButtonSpec(
        icon = if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        iconResId = if (isFavorite) R.drawable.ic_notif_heart else R.drawable.ic_notif_heart_outline,
        label = if (isFavorite) removeLabel else addLabel,
    )
