# Ava — آوا | Premium Offline Music Player for Android

**App name (final): Ava / آوا** — chosen by Harper on 2026-11-08.
Package: `com.harper.ava`. Version: **0.5.2** (diagnostic build: startup tracer + safe mode — see below; stage 6: Autonomous Improvement Loop — two cycles; Splash + Onboarding, Favorites screen, search history, playlist covers, lyrics follow toggle).

A fully offline, premium music player for Android: Kotlin + Jetpack Compose
+ Material 3 + Media3/ExoPlayer + MediaSessionService + Room + DataStore.
No account, no internet, no ads, no tracking.

> **Stage 6 status (2026-11-08):** two full Autonomous Improvement Loop
> cycles (review → P0–P3 → fix → retest → design review) with no open
> P0/P1 left in code review, verified with `./scripts/verify.sh`
> (`aapt2` + `kotlinc` type-check + JUnitCore, 51 tests OK — see
> `AGENT_PROGRESS.md`).
>
> **First real APK — 2026-11-09:** `dist/ava-0.5.0-debug.apk`
> (55,182,507 bytes, sha256 `3eaae833f…528e`), built **by hand without
> Gradle and without GitHub** (`./scripts/manual-build.sh`: aapt2 →
> KSP → kotlinc → d8 → zipalign → apksigner; verified with
> `apksigner verify` + `aapt2 dump badging`). `./gradlew assembleDebug`
> still cannot run in this sandbox (the sandbox's TCP filter blocks
> Gradle's daemon protocol), but the APK no longer depends on it. The
> project is still a standard Gradle project — `./scripts/gradle.sh
> assembleDebug` works on any machine without that filter. See `ENV.md`
> and `AGENT_PROGRESS.md` for the full evidence. **Install/run on a
> real device was NOT tested** (no device/emulator in the sandbox) —
> next step is Harper's Redmi Note 12.

## Build

Prerequisites: JDK 17, Android SDK 34 (see `ENV.md` for how they were
installed here, including the sandbox proxy/TLS workaround).

```bash
./scripts/gradle.sh assembleDebug   # on a machine without the sandbox TCP filter
./scripts/manual-build.sh           # in this sandbox: no Gradle, no GitHub (reproduces assembleDebug by hand)
```

APK (manual build): `dist/ava-0.5.2-debug.apk`
APK (Gradle build): `app/build/outputs/apk/debug/app-debug.apk`

## What works in 0.5.2 (diagnostic build — launch crash still unresolved)

- 0.5.1 also crashed instantly on launch, even though the services-file
  fix was verified real (both files in the APK, correct content,
  `AndroidDispatcherFactory` in dex). A full round-2 startup audit
  found no second cause statically (see `AGENT_PROGRESS.md` — incl. a
  coroutines 1.8.1 vs 1.9.0 mismatch ruled out as binary-compatible).
  So 0.5.2 is a **diagnostic** build, not a claimed fix.
- `StartupTracer` writes a step-by-step startup trace (Application
  start → container components → Activity attach/create → first
  composition) and, on any uncaught crash, exports the trace +
  stacktrace to the public file `Downloads/Ava/ava-crash-log.txt`
  (MediaStore; nothing sent automatically).
- **Safe mode:** if the previous run crashed during startup, the next
  launch skips Room/ExoPlayer/DataStore and shows a minimal plain-Views
  screen with the startup log, a Share button, and a "Try normal start
  again" button — so the app at least opens and the log can be sent.
- What we need from the phone: check the installed version in
  Settings > Apps > Ava, install 0.5.2, and if it crashes or opens in
  safe mode, send `Downloads/Ava/ava-crash-log.txt`.

## What works in 0.5.1 (launch-crash fix)

- 0.5.0 installed but crashed on launch: the manual build had dropped
  every `META-INF/services/*` file, so kotlinx-coroutines could not
  find its Android Main dispatcher (`Dispatchers.Main` threw in
  `Application.onCreate`). 0.5.1 packages the service files
  (`scripts/package-services.py`, step 5 of `manual-build.sh`).
- Crash diagnostics: an uncaught-exception handler installed first in
  `Application.onCreate` writes the trace to the app's internal
  storage; About → Crash reports lists them and can share the latest
  one as text (nothing is sent automatically).

## What works in 0.5.0 (new in stage 6)

- Splash + first-run Onboarding: a branded launch screen while settings load (no fake delay), then a 3-page onboarding (welcome / personalization / music access) that runs the first scan and opens Home when it finishes; skippable, shown once.
- Favorites screen (from the Home section header): Play All / Shuffle All over the full favorite list.
- Search history: recent searches persisted (max 8, recorded only when a result is opened), shown as chips with per-item remove + clear-all.
- Playlist covers + real counts on Home and in the Playlists tab; playlist delete now asks for confirmation; playlist detail has Shuffle.
- Lyrics follow toggle: hand-scrolling pauses auto-follow, a pill resumes it.
- Now Playing cover adapts to narrow screens (no more 320px overflow) and has a soft shadow; Mini Player opens Now Playing from anywhere on the row and cross-fades on track change.
- Sleep timer survives a restart even with resume playback off, and its preset chip highlights while running.

## What works in 0.4.0 (new in stage 5)

- Sleep Timer (from the Now Playing overflow menu): 5/10/15/30/45/60/90 min presets + custom 1–480 min, live countdown, cancel; the deadline is an absolute timestamp owned by the PlayerRepository, so it keeps running when the app is closed (foreground playback service) and can be re-armed after a restart; the volume fades out over the last 10 seconds before playback pauses.
- Equalizer (same menu): real Android Equalizer effect on ExoPlayer's audio session — device presets + manual band sliders (dB), persisted; when the device has no equalizer (or no song has played yet) the screen says so honestly instead of showing fake sliders.
- Lyrics (same menu): local `.lrc` files (same name as the song file), looked up via MediaStore Files, the song's own folder, and the user's SAF-picked music folders; timed lines highlight and auto-scroll with playback, untimed files show as plain text, missing files get an honest empty state.
- Song Details (song overflow menu on Home / Library / Search / playlist, and the Now Playing menu): file name, format, size, duration, bitrate and sample rate when extractable (MediaMetadataRetriever), plus metadata (album, genre, year, track, folder); unknowable fields show "Unknown", never a made-up value.
- Library Folders + Genres tabs: folders grouped by MediaStore RELATIVE_PATH, genres from a batched genre join (one query per genre, not per track); tap a folder/genre to see its songs.
- SAF music folders (Settings → Storage): pick folders with the system picker; each picked folder shows a real audio-file count, and a lost permission is reported plainly with a re-pick path (no crash).
- Full queue restore: the resume checkpoint now stores the whole queue (ids + index) in DataStore; restarting the app brings the queue back paused at its position (songs deleted since then are dropped, index clamped). Room is now v2 (migration 1→2 adds `folderPath` / `fileName` columns).

## What works in 0.3.0

- Settings (gear icon on Home): Appearance (theme mode Dark / Light /
  System / AMOLED + 7 accent palettes + a real HSV color picker for a
  custom accent, applied instantly everywhere), Language (fa / en /
  system, applied immediately with Activity recreate and full RTL/LTR
  flip), Playback (default speed 0.5x–2x, resume playback, real fade
  in/out volume ramps on in-app controls), Storage & library (real song
  count, total play time, playlist/favorite counts, cover-cache size,
  working rescan and cache clear with freed-bytes feedback), About
  (real installed version from PackageManager, offline/privacy notes,
  no external links).
- App shell: bottom navigation (Home / Library / Search / Playlists),
  Material 3 theme (Dark / Light / AMOLED + 7 accent palettes, Violet
  default), Vazirmatn font (fa + en strings).
- MediaStore scan: `READ_MEDIA_AUDIO` permission flow (explanation card,
  system dialog, Settings shortcut when denied), scan on Dispatchers.IO
  with progress + count, dedupe by MediaStore id, stale-file cleanup,
  corrupt/zero-duration rows skipped without aborting the scan.
- Home with a real time-of-day greeting and live sections: Recently
  Played (from `lastPlayedMs`), Recently Added, Favorites, Playlists
  (with real song counts, tap to open).
- Library: Songs / Artists / Albums tabs (artists and albums derived
  from the songs themselves), sortable songs, tap an artist/album to see
  its songs; tap to play, favorite toggle (persisted).
- Search: live search over title / artist / album (debounced, Room-backed).
- Playlists: create (real dialog → Room), open detail, play all, remove
  song, delete playlist; songs can be added to a playlist from the
  overflow menu on Home / Library / Search rows.
- Now Playing: big cover with track-change animation, blurred adaptive
  background from the cover art, seekbar with elapsed/remaining time,
  play/pause/prev/next, shuffle, repeat (off/all/one), favorite,
  playback speed (0.75x–2x), queue button.
- Queue: current song + upcoming, tap to play, remove, clear queue,
  shuffle queue (current song keeps playing), long-press drag & drop
  reorder in sync with ExoPlayer.
- Mini Player is tappable and opens Now Playing; the bottom bar hides on
  the full-screen Now Playing / Queue screens.
- Real playback: Media3 ExoPlayer in an application-scoped
  `PlayerRepository`; `AvaPlaybackService` (MediaSessionService) exposes
  the same player to the system (notification / lock screen / headset).
- Data: Room (`songs`, `playlists`, `playlist_songs`, `favorites`) +
  DataStore settings (theme mode, accent, language).

## Not yet built (later stages)

- Sleep Timer, Equalizer, Lyrics (LRC), Song Details.
- See `AGENT_PROGRESS.md` for the stage plan.

## Project layout

```
app/src/main/java/com/harper/ava/
  AvaApplication.kt            manual DI container
  MainActivity.kt              Scaffold + bottom nav + Mini Player
  core/util/                   pure helpers (formatDuration, LibraryLogic)
  data/local/db/               Room entities, DAOs, AppDatabase
  data/media/                  MediaStoreScanner, AudioPermission
  data/preferences/            DataStore settings (+ sync language mirror)
  data/repository/             MusicRepository, PlayerRepository (ExoPlayer)
  domain/model/                Song, Playlist
  playback/                    AvaPlaybackService (MediaSessionService)
  ui/theme/                    Design tokens, colors, typography, AvaTheme
  ui/navigation/               destinations + NavHost
  ui/components/               EmptyState, SongRow, MiniPlayer
  ui/home|library|search|playlists|nowplaying|queue|scan|settings/  screens + ViewModels
scripts/
  java-env.sh                  JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME
  gen-gradle-properties.py     regenerates Gradle proxy/TLS settings
  gradle.sh                    build wrapper (proxy-aware)
  maven-resolve.py             offline dependency resolver (verification)
  verify.sh                    aapt2 + kotlinc type-check + JUnitCore (sandbox-safe)
```

Docs: `ARCHITECTURE.md`, `DESIGN_SYSTEM.md`, `AGENT_PROGRESS.md`,
`BUGS_AND_POLISH.md`, `ENV.md`.

## Fonts & license

Vazirmatn v33.003 (SIL Open Font License 1.1, `app/src/main/assets/OFL.txt`),
Regular / Medium / SemiBold / Bold in `app/src/main/res/font/`.

## Rules of this project (from the master spec)

- No demo buttons, no fake features, no untested test claims.
- Stable versions only (no alpha/beta).
- Every user-facing string lives in `values/strings.xml` (en) and
  `values-fa/strings.xml` (fa); nothing hard-coded in UI code.
