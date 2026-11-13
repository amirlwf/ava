[فارسی 🇮🇷](README_fa.md)

# Ava — Premium Offline Music Player for Android

A fully offline music player for Android. No account, no internet, no ads, no tracking — just your music.

[![Release](https://img.shields.io/github/v/release/amirlwf/ava)](https://github.com/amirlwf/ava/releases)
[![Build](https://github.com/amirlwf/ava/actions/workflows/android.yml/badge.svg)](https://github.com/amirlwf/ava/actions/workflows/android.yml)

## What is Ava?

Ava is a premium offline music player for Android, built with Kotlin, Jetpack Compose, Media3, Room and DataStore. It plays the music already on your device — no streaming, no account, no ads.

## Features

- 🎵 **Library** — MediaStore scan with Songs, Artists, Albums, Genres and Folders
- ▶️ **Background playback** — MediaSession with notification and lock-screen controls, including a like button in the notification
- 📋 **Queue** — drag-and-drop reorder, shuffle, repeat (off / all / one), speed 0.75–2x
- 🎤 **Synced lyrics** — LRC files with highlighted lines and follow toggle
- 😴 **Sleep timer** — presets and custom durations, with fade-out in the last 10 seconds
- 🎚️ **Equalizer** — system equalizer with presets and per-band controls
- 🎨 **Ten themes** — Spring, Summer, Autumn, Winter, Emerald, Violet, Ocean, Sunset, Galactic Black and iPhone (frosted glass), each with its own palette, gradients and shapes, in Dark, Light, System and AMOLED modes
- 🌌 **Galaxy** — a living night sky behind Home and Now Playing: starfield, Milky Way, nebula, constellations, planets and meteors
- ✨ **Now Playing** — blurred cover-art backdrop with a soft ambient glow that breathes while the song plays
- 📝 **Playlists** — create playlists and add songs from inside the playlist section
- ⭐ **Favorites** — heart any song, from the library or the notification
- 📊 **Insights** — your top songs, artists, albums and genres, and listening time
- 🗑️ **Delete songs** — deletes the audio file itself (with Android's system confirmation)
- 🔄 **Resume playback** — picks up where you left off, including the full queue
- 🌐 **Languages** — full Persian (RTL) and English (LTR), switchable in the app
- 🔔 **Update check** — checks GitHub every time you open the app when you are online, with a quiet banner when a newer version is ready

## Download

| Version | File | Size |
| ------- | ---- | ---- |
| [v0.4.2](https://github.com/amirlwf/ava/releases/tag/v0.4.2) | [ava-0.4.2-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.4.2/ava-0.4.2-debug.apk) | ~59 MB |

## Installation

1. Download the APK from the table above.
2. If you have an older Ava installed, install the new APK right over it — 0.4.2 is a normal update after 0.4.1, and the in-app updater offers it as usual.
3. Open the APK on your phone and install. If your browser blocks the install, allow "Install unknown apps" for it.
4. On first launch, allow the **Music and audio** permission so Ava can scan your songs.
5. Allow **Notifications** if you want playback controls in the notification shade and on the lock screen.

## Build from source

Requirements: JDK 17 and Android SDK 36 (compileSdk 36; targetSdk stays 34).

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle cannot run in your environment, `scripts/manual-build.sh` reproduces the same debug APK by hand (aapt2, Room KSP, kotlinc, d8, zipalign, apksigner). `./scripts/verify.sh` runs the full offline check: resources, font audit, type-check and unit tests.

## FAQ

**Is Ava free?**
Yes. No ads, no account, no tracking, no in-app purchases.

**Does Ava need the internet?**
No. Everything — playback, library, lyrics, insights — works fully offline. The only network use is the optional update check each time you open the app, which you can turn off in Settings.

**How do I update from 0.4.1?**
Normally: 0.4.2 is a regular update after 0.4.1, so the in-app updater offers it, and installing the APK over 0.4.1 just works — no need to uninstall first. (The one-time exception was 0.1 itself: it replaced the removed 0.7.x line, so a 0.7.6 install never offered it.) 0.4.2 makes the app full-screen edge-to-edge: the top header adapts to the theme and sits under the status bar instead of below a bare strip.

**I had the purple or green app icon enabled — what happens after updating?**
0.4 has only one app icon now (the boteh-note mark). If you had the purple or green icon enabled, launch Ava from the installer's **Open** button the first time after updating, so Android switches the launcher icon back to the main one.

**My songs have no tags / show the wrong title?**
Ava titles untagged songs with their file name. Rescan from Home (the refresh button) to pick up the fix for songs scanned before.

**The app crashed — how do I report it?**
Share `Downloads/Ava/ava-crash-log.txt` or `Downloads/Ava/ava-startup-trace.txt` from your phone (or use the safe-mode "Share crash log" button if the app won't start).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and pull requests are welcome — please keep changes focused and describe what you tested.

## Acknowledgements

- [Media3 / ExoPlayer](https://developer.android.com/media/media3) for playback
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Room](https://developer.android.com/training/data-storage/room) for the UI and data layers
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font, licensed under the SIL Open Font License
- [Lalezar](https://github.com/BornaIz/Lalezar) font, licensed under the SIL Open Font License

## Dedication

Ava is dedicated to the memory of **Ava Rahmani Rad** (20-year-old actress from Haft Hoz, Tehranpars, 19 Dey 1404 / 9 January 2026).

Harper's website: [amirlwf.ir](https://amirlwf.ir)

## License

Source code is licensed under the [Apache License 2.0](LICENSE). The Vazirmatn and Lalezar fonts are licensed under the SIL Open Font License 1.1.
