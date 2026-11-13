# Changelog

All notable changes to Ava are documented here, newest first.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
