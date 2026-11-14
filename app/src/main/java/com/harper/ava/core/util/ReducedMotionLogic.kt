package com.harper.ava.core.util

/**
 * Reduced-motion detection (improve loop, run 19 — 2026-11-14).
 *
 * Until this run Ava had no app-wide reduced-motion handling at all:
 * the Galaxy sky (36 s loop), the glass aurora (48 s), the Now
 * Playing glow (24/31 s) and the splash typewriter all animated
 * forever, whatever the user asked the system for. Android exposes
 * the user's choice — Settings > Accessibility > Remove animations,
 * or Developer options > Animator duration scale — as
 * [android.provider.Settings.Global.ANIMATOR_DURATION_SCALE]: the
 * setting writes 0 to that scale, so a user who turns animations off
 * (for motion sensitivity, dizziness, or just calm) has a scale of
 * exactly 0. There is no AccessibilityManager API for this; reading
 * the scale is the documented community pattern (the "Remove
 * animations" setting is not exposed through accessibility services).
 *
 * The predicate is pure so the offline suite can pin it; the
 * [android.provider.Settings.Global] read itself lives in
 * [com.harper.ava.ui.theme.rememberReducedMotion] (DecorVisibility.kt),
 * which is not in verify.sh's test-compile list — this file is.
 *
 * What "reduced" freezes: the always-on ambient loops (seasonal
 * decor, glass aurora, Now Playing glow) draw one still frame, and
 * the splash dedication appears whole instead of typing itself out.
 * What it deliberately does NOT touch: the play/pause glow ease
 * (a user-triggered state change, a 2 s fade, not motion), press
 * feedback, and navigation fades — those answer an action, and
 * freezing them would make the app feel dead rather than calm.
 * A scale between 0 and 1 (e.g. 0.5x in Developer options) is NOT
 * reduced motion — the user asked for faster, not for still.
 */
fun isReducedMotion(animatorDurationScale: Float): Boolean =
    animatorDurationScale == 0f
