package com.harper.ava.core.util

/**
 * Pure playlist-name logic. Everything here is unit-tested with plain
 * JUnit (no Robolectric, no device) — see PlaylistLogicTest.
 *
 * The ViewModels ([com.harper.ava.ui.playlists.PlaylistsViewModel] and
 * [com.harper.ava.ui.playlists.PlaylistDetailViewModel]) run every
 * user-typed playlist name through [normalizePlaylistName], so no code
 * path — the create dialog, a song menu's "create and add", or a
 * future caller — can ever store a blank or whitespace-padded name.
 * The create dialog already disables its button for blank names;
 * this is the second line of defence, at the data boundary.
 */

/**
 * Trims surrounding whitespace from a playlist name; returns null
 * when nothing is left (blank or whitespace-only input). Internal
 * spaces and non-Latin scripts (Persian names) are kept untouched.
 */
fun normalizePlaylistName(name: String): String? =
    name.trim().takeIf { it.isNotEmpty() }
