# ARCHITECTURE.md — Ava (آوا)

## Overview

Single-module Android app (`:app`), Kotlin + Jetpack Compose, MVVM +
Repository, with a thin Clean-Architecture layering:

```
UI (Compose screens + ViewModels)
  │  StateFlow / events
  ▼
Domain models (Song, Playlist — plain data classes)
  │
  ▼
Repositories (MusicRepository, PlayerRepository)
  │                 │
  ▼                 ▼
Room (library)   ExoPlayer (playback)
DataStore (settings)
```

No DI framework in stage 1: `AvaApplication` builds an explicit
`AppContainer` (database, repositories, preferences). This keeps the graph
visible and avoids a framework learning curve for a one-module app; Hilt
can replace it later without touching screens.

## Modules / packages

| Package | Contents |
|---|---|
| `com.harper.ava` | `AvaApplication`, `MainActivity` |
| `core.util` | Pure helpers (`formatDuration`) — unit-tested |
| `data.local.db` | Room: `SongEntity`, `PlaylistEntity`, `PlaylistSongCrossRef`, `FavoriteEntity`; DAOs; `AppDatabase` (`ava.db`, v1) |
| `data.preferences` | `UserPreferences` (DataStore): `ThemeMode`, `Accent`, `AppLanguage` |
| `core.util` | `LibraryLogic` — pure scan/queue logic (normalize, dedupe, move, shuffle, drag-target), unit-tested |
| `data.media` | `MediaStoreScanner` (MediaStore → Room sync), `AudioPermission`, `LyricsRepository` (.lrc lookup), `SongFileInfo` (file facts), `SafFolderScanner` (SAF tree walk) |
| `data.audio` | `EqualizerController` (Android Equalizer effect wrapper) |
| `data.repository` | `MusicRepository` (Room), `PlayerRepository` (ExoPlayer + queue ops + sleep timer + equalizer) |
| `domain.model` | `Song`, `Playlist` |
| `playback` | `AvaPlaybackService` (MediaSessionService) |
| `ui.theme` | `AvaTokens`, `Color` (palettes + surfaces), `Type` (Vazirmatn), `Theme` (`AvaTheme`) |
| `ui.navigation` | `Destination`s, `AvaNavGraph` (incl. full-screen Now Playing / Queue) |
| `ui.components` | `EmptyState`, `SongRow` (overflow menu incl. Song Details), `MiniPlayer` (tappable) |
| `ui.home` / `ui.library` / `ui.search` / `ui.playlists` | One screen + ViewModel per feature |
| `ui.nowplaying` / `ui.queue` / `ui.scan` | Now Playing, Queue, scan launcher + scan state |
| `ui.settings` | Settings hub + Appearance / Language / Playback / Storage / About |
| `ui.sleep` / `ui.equalizer` / `ui.lyrics` / `ui.details` | Sleep Timer, Equalizer, Lyrics, Song Details (stage 5) |
| `core.util` | `LocaleHelper` (in-app locale without AppCompat), `SettingsLogic` (pure), `LyricsLogic` (LRC parse), `SleepTimerLogic` (pure) |

## Data flow

- **Library data is a Flow.** DAOs expose `Flow<List<…>>`; repositories
  combine them with the favorites set; ViewModels `stateIn` them with
  `SharingStarted.WhileSubscribed(5_000)`. The UI never holds a mutable
  copy of library data.
- **Playback state is a single `StateFlow<PlaybackState>`** in
  `PlayerRepository` (current song, queue, index, isPlaying, position,
  duration, shuffle, repeat). `MainActivity` collects it once and feeds
  the Mini Player; screens never talk to ExoPlayer directly.
- **Settings are a `Flow<SettingsState>`** from DataStore; `MainActivity`
  collects it and passes theme mode + accent into `AvaTheme`, so a theme
  change recolors the whole app in one recomposition.

## Settings (stage 4)

- **Source of truth:** `UserPreferences.settings` (DataStore). Every
  settings screen reads the same flow and writes through the same
  suspend setters; nothing keeps a local copy of a setting.
- **Language without AppCompat:** the app has no AppCompat dependency.
  `LocaleHelper.wrapContextWithLocale` builds a configuration context
  for the chosen locale; `MainActivity.attachBaseContext` applies it
  before inflation, so strings resolve in the right language and the
  layout flips RTL/LTR from the first frame. DataStore cannot be read
  from `attachBaseContext`, so `setLanguage` also writes a synchronous
  SharedPreferences mirror (`ava_sync_prefs`) — commit first, then the
  DataStore edit — and the screen recreates the Activity only after
  the write completes. On API 33+ the choice is also pushed to the
  platform `LocaleManager` (per-app locale screen).
- **Custom accent:** `SettingsLogic.deriveAccentColors` (pure, tested)
  derives a full palette from one ARGB color; `Color.kt` only converts
  it to Compose `Color`s. `AvaTheme(customAccentArgb)` overrides the
  preset palette when non-null.
- **Fade in/out:** real volume ramps in `PlayerRepository`
  (`fadeVolumeTo`, 14 steps). Applies to in-app play/pause only —
  notification/headset controls drive the player directly.
- **Resume playback:** the player checkpoints song id + position on
  track change / pause / stop into DataStore; `MainActivity` (one-shot
  `LaunchedEffect(Unit)`) prepares the last song paused at its position
  (no autoplay). The queue is *not* restored — only the last song.
  The transition handler skips checkpointing while paused at 0, or a
  restore would be clobbered with position 0 (bug found in review).
- **Storage stats:** `MusicRepository.libraryStats()` (Room `COUNT` /
  `SUM(durationMs)` — queries only, no schema change) + a `cacheDir`
  walk for the cover-cache size; cache clear calls Coil 3
  `diskCache.clear()` and deletes cache-dir files, then reports the
  freed bytes.

## Playback design

- One `ExoPlayer` instance lives in the application-scoped
  `PlayerRepository` (created with the application context).
- `AvaPlaybackService` (a `MediaSessionService`) builds a `MediaSession`
  around **that same player**, so UI playback and system playback
  (notification, lock screen, Bluetooth/headset, audio focus) can never
  diverge.
- `PlayerRepository.playSongs()` starts the foreground service, so the
  session exists from the first play. The service releases only the
  `MediaSession` in `onDestroy`; the player is released with the process.
- Position is polled every 500 ms on the Main dispatcher (cheap, and the
  only reliable way to drive the Mini Player progress bar with ExoPlayer).
- Queue = the list passed to `playSongs()`; `onMediaItemTransition`
  resolves the current `Song` from the queue by media id.

## Database

- `songs.mediaStoreId` is the primary key (identity = MediaStore id).
  `lastPlayedMs` / `playCount` support Recently Played later.
- `favorites` is a standalone table (song id + timestamp) so favorites
  survive a library rescan that replaces `songs` rows.
- `playlist_songs` is a cross-ref with an explicit `position` column
  (reordering support is designed in, UI comes later).
- Foreign keys cascade: deleting a playlist or a song removes its
  cross-refs automatically.

## Navigation

Bottom bar: Home → Library → Search → Playlists (single-top,
state-restore). Playlist detail is a nested route
`playlist/{playlistId}` with a back arrow. Now Playing (`now_playing`)
and Queue (`queue`) are full-screen routes: tapping the Mini Player
opens Now Playing, the Queue button opens Queue, back returns to the
previous tab. On those two routes the bottom bar and Mini Player are
hidden (`MainActivity` checks the current route).

## MediaStore scan

`MediaStoreScanner.rescan()` (Dispatchers.IO) queries
`MediaStore.Audio.Media` with `IS_MUSIC != 0`, normalizes each row with
the pure `normalizeScannedSong` (blank title → file name, zero duration
→ skipped, per-row try/catch so one corrupt row never aborts the scan),
dedupes by MediaStore id, then `MusicRepository.syncLibrary` upserts
and deletes stale songs inside one Room transaction. A null cursor is
treated as a failed scan and never syncs (it would otherwise wipe the
library). Genre is not fetched (per-track join — see the scanner's doc
comment). The permission flow lives in `ui.scan.rememberScanLauncher`
(a shared trigger for Home and Library); `ScanUiState`
(Idle/Scanning/Done/PermissionDenied/Error) is owned by each screen's
ViewModel.

## Queue

Queue state lives in `PlayerRepository.state.queue` and is mirrored into
ExoPlayer with the same calls (`addMediaItem` / `removeMediaItem` /
`moveMediaItem`), so the Queue screen and the player can never disagree
about the order. Pure list transforms live in `LibraryLogic` and are
unit-tested; the drag & drop target is computed from the drag distance
divided by the fixed 72dp row height (`dragTargetIndex`). Shuffle and
Clear keep the currently playing song in place and never restart
playback.

## Threading

- Room + DataStore calls run on their own dispatchers (Room `suspend`
  DAOs, DataStore IO).
- `PlayerRepository` uses a `SupervisorJob + Dispatchers.Main` scope for
  the position poller; ExoPlayer callbacks arrive on the main thread.
- Search debounces 200 ms (`debounce` + `flatMapLatest`) so typing never
  hammers the database.

## Stage 5 — advanced features

- **Sleep Timer:** an absolute deadline (`endsAtMs`) owned by
  `PlayerRepository` (two jobs: a 1 s ticker updating
  `sleepTimerState.remainingMs`, and a waiter that fades the volume
  out over `SLEEP_TIMER_FADE_MS` via the shared suspend `rampVolume`
  and then pauses). The deadline is persisted in DataStore and
  re-armed by `MainActivity` on restart; cancelling mid-fade restores
  full volume. The timer survives the app being closed because
  playback runs in a foreground service.
- **Equalizer:** `EqualizerController` wraps Android's `Equalizer`
  effect, attached to `player.audioSessionId`. The repository owns
  `equalizerState` (supported/reason/bands/presets/levels) and
  re-attaches on `onAudioSessionIdChanged` and on every play/prepare,
  applying the persisted settings. Preset/manual choices are persisted
  in DataStore; a device without the effect reports
  `supported = false` and the UI says so.
- **Lyrics:** `LyricsLogic.parseLrc` (pure, tested) handles timed
  lines, multiple tags, `[offset:]` and metadata; `LyricsRepository`
  finds the `.lrc` via MediaStore Files → sibling file (DATA column)
  → SAF-picked folders (`SafFolderScanner.findNamedFile`). The
  ViewModel collects the current song itself, so loaded lyrics always
  belong to the triggering song.
- **Song Details:** `loadSongFileInfo` reads MediaStore columns +
  `MediaMetadataRetriever` (bitrate/sample rate), all best-effort and
  null-safe; the screen shows localized "Unknown" for unknowable fields.
- **Folders / Genres:** the scanner stores `RELATIVE_PATH` +
  `DISPLAY_NAME` (Room v2, `MIGRATION_1_2` adds nullable `folderPath`
  / `fileName` columns) and a batched genre join (one query per
  genre). The Library uses `GROUP BY` queries and a scrollable
  5-tab row (`PrimaryScrollableTabRow`).
- **SAF folders:** picked with `OpenDocumentTree`, persistable read
  permission taken at pick time, URIs stored in DataStore. The
  Storage screen re-inspects each folder (audio-file count via a
  DocumentsContract tree walk) and detects lost access through
  `persistedUriPermissions`; removing a folder releases its permission.
- **Full queue resume:** the checkpoint stores song id + position +
  the whole queue (ids + index) on track change / pause / stop / every
  queue mutation. `MainActivity` restores it with
  `PlayerRepository.prepareQueue()` (songs deleted since then are
  dropped, the index is clamped); pre-stage-5 checkpoints fall back
  to the single song.

## Stage 6 — Autonomous Improvement Loop (two cycles, done)

- **Splash gate:** `MainActivity` collects settings with `initial =
  null` and shows the branded `SplashScreen` until the first DataStore
  emission — the graph never composes with default settings, so there
  is no first-frame wrong-theme/start-destination flash and no fake
  timed delay.
- **Onboarding:** `onboardingSeen` in DataStore; the NavGraph takes a
  `startDestination` (Onboarding on first run, Home after). The
  onboarding's permission page reuses `rememberScanLauncher` and opens
  Home when the first scan finishes; the seen flag is persisted before
  navigation.
- **Playlists with counts + cover:** `MusicRepository
  .observePlaylistsWithCounts()` (flatMapLatest + combine of
  `observeSongCount` and the new `PlaylistDao.observeFirstArtUri`)
  is the single source both Home and the Playlists tab use — the
  count enrichment no longer lives in one ViewModel.
- **Search history:** persisted in DataStore (`recentSearches`, max 8);
  pure `SearchHistoryLogic` (record/remove/encode/decode) is unit-tested.
  A search is recorded only when the user opens a result.
- **Sleep timer duration:** `SleepTimerState.durationMinutes` survives
  a restart via pure `restoreSleepTimerMinutes` (rounded up from the
  persisted deadline); the timer re-arm in `MainActivity` is
  independent of the resume-playback setting.
- **Scan guard:** `rememberScanLauncher` ignores a second trigger while
  a scan is in flight.
- **Favorites:** a dedicated `favorites` route + screen (Play All /
  Shuffle All), reached from the Home section header.

## What changes in later stages

- Release build + installable APK on an unfiltered machine, Compose UI
  tests (need Gradle + a device/emulator), and the full Motion Design
  System (spec §13: shared-element transitions, skeleton loading,
  haptics).
