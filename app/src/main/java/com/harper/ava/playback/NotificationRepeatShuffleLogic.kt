package com.harper.ava.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.harper.ava.R
import com.harper.ava.data.repository.RepeatMode

/**
 * The notification repeat + shuffle buttons — 0.2-fix (Track 1).
 *
 * Harper's 0.2 report: the notification had no repeat and no shuffle.
 * The stock provider only emits prev / play / next — repeat and
 * shuffle are session commands, so they ride the same mechanism as
 * the like button ([NotificationLikeLogic]): the service pushes them
 * with `MediaSession.setMediaButtonPreferences`, the provider appends
 * them after the transport buttons ([CommandButton.SLOT_OVERFLOW] —
 * compact view keeps exactly the three transport buttons), and a tap
 * travels as a custom command the session callback routes to
 * `PlayerRepository.cycleRepeatMode()` / `toggleShuffle()`. No
 * RemoteViews, no from-scratch provider — the RemoteViews path
 * crashed twice on Harper's Redmi Note 12 / Android 15 and is never
 * repeated.
 *
 * The buttons are state-reflecting: the service rebuilds the
 * preferences whenever the repeat mode or the shuffle state changes,
 * so the icon always shows the CURRENT mode and the label is the
 * action the tap performs (the next mode for repeat, the toggle for
 * shuffle) — the same label rule as the like button.
 *
 * Test boundary (same as the like button): the Media3-typed assembly
 * here — [repeatSessionCommand], [shuffleSessionCommand],
 * [repeatButton], [shuffleButton] — CANNOT run in the offline JUnit
 * suite. Building a `SessionCommand` / `CommandButton` constructs
 * an `android.os.Bundle`, whose stub constructor throws on this JVM,
 * and building a button reads the media3 R class, which the verify
 * environment does not generate. So those functions are verified by
 * the kotlinc type-check, by dex/javap inspection in the manual
 * build, and by Harper's device. What IS unit-tested here: the pure
 * decisions ([repeatButtonSpec], [shuffleButtonSpec], [isRepeatAction],
 * [isShuffleAction], the action strings).
 */

/** Custom action string for the notification repeat button. Pinned by test. */
const val REPEAT_COMMAND_ACTION = "com.harper.ava.action.TOGGLE_REPEAT"

/** Custom action string for the notification shuffle button. Pinned by test. */
const val SHUFFLE_COMMAND_ACTION = "com.harper.ava.action.TOGGLE_SHUFFLE"

/** True only for the notification repeat button's custom action. */
fun isRepeatAction(action: String?): Boolean = action == REPEAT_COMMAND_ACTION

/** True only for the notification shuffle button's custom action. */
fun isShuffleAction(action: String?): Boolean = action == SHUFFLE_COMMAND_ACTION

/** The repeat custom command (one instance per call; compared by action string). */
fun repeatSessionCommand(): SessionCommand = SessionCommand(REPEAT_COMMAND_ACTION, Bundle.EMPTY)

/** The shuffle custom command (one instance per call; compared by action string). */
fun shuffleSessionCommand(): SessionCommand = SessionCommand(SHUFFLE_COMMAND_ACTION, Bundle.EMPTY)

/**
 * The repeat button for the current [RepeatMode].
 *
 * The decisions (which icon constant, which Ava drawable, which label)
 * live in the pure [repeatButtonSpec] below, so they are pinned by
 * unit test. The assembly here is a thin Media3 builder chain — it
 * cannot run in the offline JUnit suite (see the file KDoc), so it is
 * covered by the kotlinc type-check, by dex/javap inspection in the
 * manual build, and by Harper's device.
 *
 * The icon constant tells controllers what the button is
 * (`ICON_REPEAT_ALL` / `ICON_REPEAT_ONE` / `ICON_REPEAT_OFF`); the
 * drawable the notification actually shows is Ava's own, swapped in
 * with `setCustomIconResId` (same mechanism as the transport
 * drawables and the like heart). The button takes the
 * [CommandButton.SLOT_OVERFLOW] slot, so it appears in the expanded
 * notification only — compact view keeps exactly the three transport
 * buttons. The display name is the mode the tap switches TO
 * (accessibility + controllers that show it).
 */
fun repeatButton(
    mode: RepeatMode,
    offLabel: String,
    allLabel: String,
    oneLabel: String,
): CommandButton {
    val spec = repeatButtonSpec(mode, offLabel, allLabel, oneLabel)
    return CommandButton.Builder(spec.icon)
        .setSessionCommand(repeatSessionCommand())
        .setCustomIconResId(spec.iconResId)
        .setDisplayName(spec.label)
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()
}

/** The repeat button's decisions, as pure data (unit-tested). */
data class RepeatButtonSpec(
    /** Media3 icon constant (`ICON_REPEAT_ALL` / `ONE` / `OFF`). */
    val icon: Int,
    /** Ava drawable the notification actually draws. */
    val iconResId: Int,
    /** Display name = the mode the tap switches to. */
    val label: String,
)

/**
 * Pure decisions for [repeatButton]: which repeat constant, which Ava
 * drawable, which label — the label names the mode the tap switches
 * TO (OFF → all, ALL → one, ONE → off), so the button always says
 * what tapping it will do.
 */
fun repeatButtonSpec(
    mode: RepeatMode,
    offLabel: String,
    allLabel: String,
    oneLabel: String,
): RepeatButtonSpec = when (mode) {
    RepeatMode.OFF -> RepeatButtonSpec(
        icon = CommandButton.ICON_REPEAT_OFF,
        iconResId = R.drawable.ic_notif_repeat_off,
        label = allLabel,
    )
    RepeatMode.ALL -> RepeatButtonSpec(
        icon = CommandButton.ICON_REPEAT_ALL,
        iconResId = R.drawable.ic_notif_repeat,
        label = oneLabel,
    )
    RepeatMode.ONE -> RepeatButtonSpec(
        icon = CommandButton.ICON_REPEAT_ONE,
        iconResId = R.drawable.ic_notif_repeat_one,
        label = offLabel,
    )
}

/**
 * The shuffle button for the current shuffle state.
 *
 * The decisions live in the pure [shuffleButtonSpec] below, so they
 * are pinned by unit test. The assembly is a thin Media3 builder
 * chain — same offline-JUnit boundary as [repeatButton] (see the file
 * KDoc). The icon constant tells controllers what the button is
 * (`ICON_SHUFFLE_ON` / `ICON_SHUFFLE_OFF`); the drawable is Ava's
 * own, swapped in with `setCustomIconResId`. SLOT_OVERFLOW, so the
 * button appears in the expanded notification only. The display name
 * is the action the tap performs.
 */
fun shuffleButton(
    enabled: Boolean,
    onLabel: String,
    offLabel: String,
): CommandButton {
    val spec = shuffleButtonSpec(enabled, onLabel, offLabel)
    return CommandButton.Builder(spec.icon)
        .setSessionCommand(shuffleSessionCommand())
        .setCustomIconResId(spec.iconResId)
        .setDisplayName(spec.label)
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()
}

/** The shuffle button's decisions, as pure data (unit-tested). */
data class ShuffleButtonSpec(
    /** Media3 icon constant (`ICON_SHUFFLE_ON` / `ICON_SHUFFLE_OFF`). */
    val icon: Int,
    /** Ava drawable the notification actually draws. */
    val iconResId: Int,
    /** Display name = the action the tap performs. */
    val label: String,
)

/**
 * Pure decisions for [shuffleButton]: which shuffle constant, which
 * Ava drawable, which label — shuffle on shows the plain shuffle
 * mark and offers to turn it off; shuffle off shows the mark with a
 * slash and offers to turn it on.
 */
fun shuffleButtonSpec(
    enabled: Boolean,
    onLabel: String,
    offLabel: String,
): ShuffleButtonSpec = if (enabled) {
    ShuffleButtonSpec(
        icon = CommandButton.ICON_SHUFFLE_ON,
        iconResId = R.drawable.ic_notif_shuffle,
        label = offLabel,
    )
} else {
    ShuffleButtonSpec(
        icon = CommandButton.ICON_SHUFFLE_OFF,
        iconResId = R.drawable.ic_notif_shuffle_off,
        label = onLabel,
    )
}
