# Changelog

All notable changes to Ava are documented here, newest first.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.4.2] — 2026-11-13

A small patch on 0.4.1, from Harper's request after testing 0.4.1: the
app was not full-screen — the top header sat detached below a bare
strip. Now the app is edge-to-edge and the header adapts to the theme.
Version 0.4.2 (versionCode 26) — a patch release: edge-to-edge layout,
no new features.

### Fixed

- **Full-screen edge-to-edge** — the app now draws behind the status
  bar and the navigation bar. The top header adapts to the theme and
  sits under the status bar instead of one status-bar height below a
  bare strip.
- **Status-bar icon contrast follows the app theme** — the icons in
  the status bar are dark on light themes and light on dark/AMOLED
  themes, matching the theme chosen in the app rather than the system
  theme.
- **Bottom dead space removed on nested screens** — screens with their
  own top bar no longer reserve a second navigation-bar inset at the
  bottom.

## [0.4.1] — 2026-11-13

A small patch on 0.4, from Harper's request after testing 0.4.
Version 0.4.1 (versionCode 25) — a patch release: a longer splash
intro and one small UX fix, no new features.

### Changed

- **Splash intro is now about 2 seconds** — the dedication line
  ("تقدیم به آوا رحمانی راد") types at ~70 ms per character
  (~1600 ms for the 23-character line) with a 400 ms hold before
  the hand-over, instead of ~1 second before.

### Fixed

- **Empty Queue and Now Playing screens gain a "Go to library"
  button** — both empty states told the user to play a song from
  the library but offered no way there. The button opens the
  Library root (via the nav reset).

## [0.4] — 2026-11-13

The fix round after 0.3, built from Harper's test of 0.3 on his phone.
Version 0.4 (versionCode 24) — a minor release, because this round
adds features (a reworked iPhone theme, richer Galaxy, softer motion,
nav reset), not just fixes. Harper's screenshots confirmed 0.3's
notification on device: the cover shows for tagged songs, repeat
(incl. repeat-one) and shuffle show and reflect state. One 0.3 claim
is corrected here: even with artwork, the HyperOS media card stays
white — that is the phone's own system media-player style, not an
app bug, and it is out of the app's control.

### Fixed

- **Untagged songs get their cover without a Rescan** — stored
  library rows still holding the broken `albums/` URI used to
  shadow the default cover, so untagged songs showed no art at all.
  Broken stored URIs are now treated as no-art at read time, so the
  Galaxy default cover shows; no Rescan is needed after installing
  0.4.
- **Dark parts recolor in dark themes** — root cause: MaterialTheme
  does not provide `LocalContentColor` (its default is black), so a
  bare `Icon()` rendered black in dark themes. The theme now
  provides the content colour from the scheme, so every dark part —
  icons, and any other dark element — flips light in dark themes,
  plain dark and AMOLED alike, while the background stays true
  black.

### Changed

- **One app icon only** — the purple and green switchable icons are
  gone completely (launcher aliases, resources, and the picker in
  Settings). The boteh-note mark is the only icon. If you had the
  purple or green icon enabled, launch Ava from the installer's
  **Open** button the first time after updating, so Android switches
  the launcher icon back to the main one.
- **Now Playing motion is soft** — entering and leaving Now Playing
  is fade-dominant with a gentle rise and fall (420 ms in, 320 ms
  out), the cover flight and springs are softened, and the
  swipe-down exit shares the same 340 ms spec. No sharp cuts.
- **Nav icons: tap motion + section reset** — tapping a bottom-nav
  icon gives a gentle 0.92x press squash, and tapping a section's
  icon resets that section to its root (e.g. from Settings, tap Home
  and you are back at the main Home screen).
- **iPhone theme, reworked** — stronger and larger aurora, glass
  panels at 0.45 alpha, and glass-aware scaffold and top-bar colours
  on every sub-screen, so the theme is felt everywhere.
- **Galaxy, richer** — more stars (64 small, 42 medium, 14 large),
  more Milky Way dust (36), three meteors with the first one big,
  and a detailed solar system in the upper-right corner.

## [0.3] — 2026-11-13

The fix round after 0.2, built from Harper's test of 0.2 on his phone.
Version 0.3 (versionCode 23) — a minor release, because this round
adds features (repeat/shuffle in the notification, a new logo), not
just fixes. It folds in improve-loop run 9 (the splash dedication
line now uses a dedicated Type.kt style, so it stays centred when it
wraps on a narrow screen).

### Fixed

- **Notification finally shows the cover** — the scanner stored the
  wrong album URI (the `albums/` row URI, whose `openInputStream`
  fails), so Media3's BitmapLoader silently loaded nothing and the
  shade showed no art. The scanner now stores the real `albumart`
  file URI, probed at scan time; stored library rows pick it up on
  the next Rescan.
- **Notification no longer white** — on Android 13+ the shade derives
  its background from the artwork, so the missing cover was also the
  missing theme; with real artwork the shade takes the song's colours
  again, plus Ava's accent.
- **AMOLED black-on-black** — in the AMOLED theme some black elements
  (track rows, dividers, icons) vanished on the pure-black
  background. A shared lifted-container colour and a shared
  inactive-track alpha now keep every element visible while the
  background stays true black.

### Added

- **Repeat + shuffle in the notification** — repeat (off / all / one)
  and shuffle buttons in the expanded notification, wired to the
  player's real repeat mode and shuffle mode, mirroring the
  like-button session-command pattern.
- **New app logo** — the boteh-note mark (a Persian boteh paisley
  flowing into a music note, violet with a gold accent line) is now
  the main launcher icon and the intro logo. The purple/green
  switchable icons stay available in Settings.

### Changed

- **Update check on every entry** — the 24-hour cooldown is gone; the
  app checks GitHub every time you open it (cold start and return
  from background, at most once per 5 minutes), quietly, never
  blocking startup.

After installing 0.3, tap Rescan once so stored library rows pick up
the fixed artwork URI.

## [0.2] — 2026-11-13

The fix round after 0.1, built from Harper's test of 0.1 on his phone.
Version 0.2 (versionCode 22) — a minor release, because this round
adds features, not just fixes. It folds in improve-loop run 8
(playback position on its own flow, so the 500 ms tick no longer
recomposes the whole app).

### Added

- **Ringtone editing** — from a song's menu or Now Playing, trim a
  segment of the song, preview the trimmed segment, and set it as the
  phone ringtone. Real trim (Media3 Transformer re-encodes to AAC/MP4)
  and a real system ringtone (MediaStore + RingtoneManager, with the
  WRITE_SETTINGS grant flow explained in the app).
- **1-second launch intro** — on every cold start the new logo and
  the app name ease in softly, and at the bottom, small, the
  dedication «تقدیم به آوا رحمانی راد» types itself out in the Almas
  handwritten face, then the app opens (onboarding on first run, Home
  afterwards).
- **New app logo** — the purple neon squircle artwork is now the main
  launcher icon (and the intro logo). The purple/green switchable
  icons stay available in Settings.

### Changed

- **Notification feels like Ava's** — every media item now carries
  artwork (the song's album art, or Ava's branded default cover when
  a song has none), so the shade's media player never shows a blank
  tile; on Android 13+ the shade derives its colours from the
  artwork. A theme change now also re-posts the notification with the
  new accent immediately (it used to wait for the next play/pause).
  Still the safe MediaStyle path — a fully custom notification layout
  crashed twice on this phone and is never coming back.
- **iPhone theme is unmistakable now** — a slow glass aurora (blue /
  cyan / violet colour fields) drifts behind every screen, so the
  frosted panels finally have something real to blur; the bottom bar
  is more translucent (0.55) with a 1 dp hairline, like iOS's tab bar.
  The 0.1 problem was real: the glass sat over a flat opaque
  background, so selecting the theme changed almost nothing visible.
- **Galaxy is calm and realistic, not crowded** — one soft Milky Way
  band, sparse stars, a few faint nebula clouds, at most one rare
  shooting star. No solar system, no constellations, no meteor
  shower.
- **Softer motions everywhere** — one easing family app-wide
  (FastOutSlowIn), longer fades/slides, soft springs that settle
  instead of bouncing (damping 0.7–0.9); one 350 ms cross-fade for
  every swap; one soft spring for list rows.
- **Swipe down on Now Playing** — drag the screen down and it follows
  your finger; past the threshold it exits back to Home (playback
  keeps going, the mini player stays), below it springs back. It
  never fights lyrics scrolling: it engages from the top / when the
  list is already at the top.

### Fixed

- **Galaxy background no longer flashes away on navigation** — the
  sky used to live inside Home and Now Playing, tied to each
  destination's resume state, so tapping an icon (e.g. Settings)
  dropped the destination below RESUMED and the Canvas left
  composition mid-transition. The backdrop is now one persistent
  layer behind the NavHost (and outside every nav transition); going
  to the background freezes the last frame instead of blanking it.

## [0.1] — 2026-11-13

0.1 starts a new version line and replaces the 0.7.x releases, which were
removed from GitHub. It folds in improve-loop runs 4–7 and is the first
standard, clean release: version 0.1 (versionCode 21), a rewritten README,
and the standard repo files below.

### Added

- **iPhone theme (10th theme)** — Apple's design language as a full theme:
  frosted-glass surfaces, pure-black dark background, airy `#F2F2F7` light
  background, large continuous corners. The floating mini-player pill, the
  bottom navigation bar and the Now Playing backdrop turn to frosted glass
  when it is active. It shows no seasonal decor — the glass itself is its
  identity. Fonts stay Vazirmatn + Lalezar (no SF Pro, Apple's proprietary
  font, is ever bundled).
- **Now Playing ambient glow** — three soft theme-colour orbs drift slowly
  across the screen on two long loops (24 s and 31 s) and a halo behind the
  cover breathes gently. Full strength while the song plays, a calm resting
  glow when paused. Ambient motion only: it never pretends to follow the
  music — there is no fake audio-reactive visualizer.
- **Motion everywhere** — navigation now rises gently (24 dp) while fading
  in, instead of a flat fade; list rows animate on insert/remove/reorder on
  Home, Library, Search, Favorites, Playlist detail, Playlists and Queue;
  the play/pause icon cross-fades on the mini player and Now Playing; the
  press squash is one shared helper now. The vertical rise is deliberate:
  a horizontal slide would fight the RTL layout direction.
- **Galaxy is richer** — a fifth meteor, 44 Milky Way dust grains (was 34),
  eight nebula clouds (was six), and a third constellation (a tight
  Pleiades-like cluster, upper right). Still faint by design, so lists stay
  readable.
- **Playlists empty state** gains a real "New playlist" button next to the
  words that ask for it (the floating action button was the only way in).
  Playlist names are trimmed and blank names are rejected at the data
  boundary, so no code path can store a blank playlist name.
- **Notification channel description** — the playback channel now explains
  itself in system Settings. A blank album no longer shows as an empty
  second line in the notification.
- **Standard repo files** — this changelog, `CONTRIBUTING.md`, and
  `LICENSE` (Apache 2.0).
- **Tests** — 206 unit tests (was 193): ambient-glow math, playlist-name
  normalization, the glass theme, and the version-reset behaviour below.

### Changed

- **Version 0.1 (versionCode 21)** — see the note below.
- README rewritten (English primary, Persian mirror): short, simple and
  clear, with one download link and an FAQ. No screenshots yet — there are
  no real screenshots to show.
- The notification stays on the proven safe path (Media3 provider,
  MediaStyle): metadata, like button, live theme accent, PNG small icon.
  No custom RemoteViews, ever — it crashed twice on the test phone.

### Fixed

- The Queue screen's now-playing row was a dead tap — it now opens
  Now Playing, like every other now-playing surface.
- Folded in from improve-loop runs 4–7: Insights over-counted listening
  time on a forward scrub; a whitespace-only file name became a whitespace
  song title; Home had no Rescan button in its empty state and Search had
  no clear-search button on no results.

### Notes

- **Upgrading from 0.7.6:** install 0.1 manually from the release link
  (uninstall the old version first). The in-app updater compares version
  numbers, so 0.7.6 will not offer 0.1 automatically; future updates
  (0.2, 0.3, …) compare normally against 0.1.
- **Not device-verified yet:** this build has not been tested on a real
  phone. Crash-free playback, the notification, the like button, the glass
  theme and the ambient glow all still need Harper's phone to prove them.

## [0.7.6] — 2026-11-12

- Notification gains a like button (heart in the expanded notification).
- Song titles never blank: scanner prefers the file name; notification
  falls back to title → file name → `track-<id>`.
- MediaMetadata (title/artist/album/artwork) on every media item, so the
  notification and lock screen show the song.
- Improve-loop runs 1–3 folded in (metadata, dead-string cleanup,
  decor pause when hidden).

## [0.7.5] — 2026-11-12

- Notification reverted to the proven Media3 provider for the second and
  final time, designed in Ava's style inside the safe path: PNG small icon,
  custom transport drawables, live theme accent, artist-or-album second line.
- The 0.7.4 fully custom notification crashed on play with the same error
  as 0.7.2 — the RemoteViews layout itself was the cause, and it is dropped
  for good.

## [0.7.4] — 2026-11-12

- Galaxy theme rebuilt in depth: three-layer starfield, Milky Way band,
  six nebula layers, two constellations, five planets with moons and a
  Saturn-like ring, brighter meteors with glowing trails.
- Add songs from inside a playlist (multi-select searchable picker,
  + on each playlist row, clear button in the empty state).
- App dedicated to the memory of Ava Rahmani Rad.

## [0.7.3] — 2026-11-12

- Notification crash fix: Media3 provider + PNG small icon (the 0.7.2
  custom notification crashed on play on the test phone).

## [0.7.2] — 2026-11-12

- Delete a song from the app (the audio file itself, with Android's
  system confirmation); deleting the playing song moves to the next one.
- Automatic update check (once a day, quiet banner).
- Seasonal decor + Galactic Black theme.

## [0.7.1] — 2026-11-12

- Harper's own app icon (red default; purple and green variants from
  Settings). Nine complete themes. Zero system font anywhere.
- Listening Insights. Motion: shared-element transitions, skeleton
  loading, haptics, spring animations, animated theme switching.

## [0.7.0] — 2026-11-11 and earlier

- The 0.5.x–0.7.0 line built the app from the master brief: offline
  library, background playback, queue, lyrics, sleep timer, equalizer,
  themes, Persian/English, resume playback. Full history is in git
  (`git log`); the old release binaries were removed when 0.1 shipped.
