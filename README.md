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

**0.6.0 is the design round.** The UI went minimal and cute (redesigned MiniPlayer, Now Playing, song rows, Home and Library screens, softer tokens and colours), and the stock Media3 notification is replaced by Ava's own media notification — Ava's icon, Ava's play/pause/previous/next drawables, and the accent colour. It builds on 0.5.7's playback-service fix (the session is registered, the notification appears, the service foregrounds immediately). **Fonts: Vazirmatn stays the body/UI face, and Lalezar joins as the display face** (big titles, app name, section headers) — both free under the SIL Open Font License. Manshoor was prepared but deliberately NOT bundled: it is a paid commercial font and has not been bought; Dana is out as well. The app is still a diagnostic build: if you hit a crash, please share `Downloads/Ava/ava-crash-log.txt` or `Downloads/Ava/ava-startup-trace.txt` — or use the safe-mode "Share crash log" button. This build has not yet been verified on a real device.

## Contributing

Issues and pull requests are welcome. Please keep changes focused and describe what you tested.

## Acknowledgements

- [Media3 / ExoPlayer](https://developer.android.com/media/media3) for playback
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Room](https://developer.android.com/training/data-storage/room) for the UI and data layers
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font, licensed under the SIL Open Font License
- [Lalezar](https://github.com/BornaIz/Lalezar) font, licensed under the SIL Open Font License

## License

Source code is provided as-is for personal use. The Vazirmatn and Lalezar fonts are licensed under the SIL Open Font License.
