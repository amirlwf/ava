[فارسی 🇮🇷](README_fa.md)

# Ava — Premium Offline Music Player for Android

A fully offline music player for Android. No account, no internet, no ads, no tracking — just your music.

[![Release](https://img.shields.io/github/v/release/amirlwf/ava)](https://github.com/amirlwf/ava/releases)
[![Build](https://github.com/amirlwf/ava/actions/workflows/android.yml/badge.svg)](https://github.com/amirlwf/ava/actions/workflows/android.yml)
[![Downloads](https://img.shields.io/github/downloads/amirlwf/ava/total)](https://github.com/amirlwf/ava/releases)

## What is Ava?

Ava is a premium offline music player for Android, built with Kotlin, Jetpack Compose, Media3, Room and DataStore. It plays the music already on your device — no streaming, no account, no ads.

## Features

- 🎵 **Library** — MediaStore scan with Songs, Artists, Albums, Genres and Folders
- ▶️ **Background playback** — MediaSession with notification and lock-screen controls
- 📋 **Queue** — drag-and-drop reorder, shuffle, repeat (off / all / one), speed 0.75–2x
- 🎤 **Synced lyrics** — LRC files with highlighted lines and follow toggle
- 😴 **Sleep timer** — presets and custom durations, with fade-out in the last 10 seconds
- 🎚️ **Equalizer** — system equalizer with presets and per-band controls
- 🎨 **Themes** — Dark, Light, System and AMOLED, with accent colors
- 🌐 **Languages** — full Persian (RTL) and English (LTR), switchable in the app
- 🔄 **Resume playback** — picks up where you left off, including the full queue

## Download

| Version | File | Size |
| ------- | ---- | ---- |
| [v0.7.3](https://github.com/amirlwf/ava/releases/tag/v0.7.3) | [ava-0.7.3-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.3/ava-0.7.3-debug.apk) | 58 MB |
| [v0.7.2](https://github.com/amirlwf/ava/releases/tag/v0.7.2) | [ava-0.7.2-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.2/ava-0.7.2-debug.apk) | 58 MB |
| [v0.7.1](https://github.com/amirlwf/ava/releases/tag/v0.7.1) | [ava-0.7.1-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.1/ava-0.7.1-debug.apk) | 58 MB |
| [v0.7.0](https://github.com/amirlwf/ava/releases/tag/v0.7.0) | [ava-0.7.0-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.7.0/ava-0.7.0-debug.apk) | 57 MB |
| [v0.6.0](https://github.com/amirlwf/ava/releases/tag/v0.6.0) | [ava-0.6.0-debug.apk](https://github.com/amirlwf/ava/releases/latest/download/ava-0.6.0-debug.apk) | 57 MB |
| [v0.5.7](https://github.com/amirlwf/ava/releases/tag/v0.5.7) | [ava-0.5.7-debug.apk](https://github.com/amirlwf/ava/releases/download/v0.5.7/ava-0.5.7-debug.apk) | 57 MB |

## Installation

1. Download the APK from the table above.
2. Open it on your phone and install. If your browser blocks the install, allow "Install unknown apps" for it.
3. On first launch, allow the **Music and audio** permission so Ava can scan your songs.
4. Allow **Notifications** if you want playback controls in the notification shade and on the lock screen.

## Build from source

Requirements: JDK 17 and Android SDK 36 (compileSdk 36; targetSdk stays 34).

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle cannot run in your environment, `scripts/manual-build.sh` reproduces the same debug APK by hand (aapt2, Room KSP, kotlinc, d8, zipalign, apksigner).

## Project status

**0.7.3 fixes the 0.7.2 play crash and upgrades the Galaxy theme.** The 0.7.2 custom notification crashed on play (`Bad notification for startForeground`), so the notification is back on Media3's own provider (Ava icon, Ava transport drawables, theme accent — no custom layout, no like button, dismissible again); the small icon is now a white PNG silhouette instead of a vector. The Galaxy theme now has passing meteors and a visible solar system (sun + three orbiting planets). Everything from 0.7.2 stays: you can now delete a song from the app — the file itself is deleted from your phone (with Android's system confirmation on Android 11+), and it disappears from the library, playlists, favorites and queue; deleting the playing song moves to the next one. **Automatic update check:** Ava checks GitHub once a day when you are online and shows a quiet banner when a newer version is ready (Download / Later / don't remind me for this version), with a toggle and a "Check now" button in Settings. **Seasonal decor + a ninth theme:** green leaves in Spring, sun and greenery in Summer, falling leaves in Autumn, snowflakes in Winter — subtle animated decor behind Home and Now Playing, matching your theme (toggleable in Appearance) — plus a new **Galactic Black** theme: deep black with a purple-pink-indigo nebula and stars. **Notification (0.7.3):** the 0.7.2 fully custom notification crashed on play, so 0.7.3 reverts to Media3's own notification provider with Ava's look layered on (Ava small icon, Ava transport drawables, theme accent, smart artist/album line) — no custom layout, no like button, and it is dismissible while playing again; the dismiss-stops-music bug stays fixed by the round-8 root fix (pause, not stop, on service destroy). **Playlist bug fixed:** adding a song to a playlist finally works — you can now create your first playlist right from a song's menu, and it works from every screen including inside a playlist. Everything from 0.7.1 stays: Harper's own app icon (red default, purple and green variants switchable from Settings); nine complete, distinct themes (Spring, Summer, Autumn, Winter, Emerald, Violet, Ocean, Sunset, Galaxy) — each with its own palette, gradients, shapes and surfaces — selectable in Settings and applied app-wide, including the notification accent. **Zero system font anywhere:** every text style, dialog, menu and field uses the app's own fonts (Vazirmatn body + Lalezar display). **Listening Insights:** a new screen with your top songs, artists, albums and genres, listening time (today / this week / this month), 7-day trends and habits — all computed on-device from a new listening-history table (Room v3). **Motion:** shared-element transitions (song card → Now Playing), skeleton loading, haptics, spring animations, and animated theme switching. Manshoor is still NOT bundled (paid, not bought); Dana is out. The app is still a diagnostic build: if you hit a crash, please share `Downloads/Ava/ava-crash-log.txt` or `Downloads/Ava/ava-startup-trace.txt` — or use the safe-mode "Share crash log" button. This build has not yet been verified on a real device.

## Contributing

Issues and pull requests are welcome. Please keep changes focused and describe what you tested.

## Acknowledgements

- [Media3 / ExoPlayer](https://developer.android.com/media/media3) for playback
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Room](https://developer.android.com/training/data-storage/room) for the UI and data layers
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font, licensed under the SIL Open Font License
- [Lalezar](https://github.com/BornaIz/Lalezar) font, licensed under the SIL Open Font License

## License

Source code is provided as-is for personal use. The Vazirmatn and Lalezar fonts are licensed under the SIL Open Font License.
