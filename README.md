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
| [v0.5.4](https://github.com/amirlwf/ava/releases/tag/v0.5.4) | [ava-0.5.4-debug.apk](https://github.com/amirlwf/ava/releases/latest/download/ava-0.5.4-debug.apk) | 57 MB |

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

**0.5.4 is a diagnostic build for a second launch crash.** 0.5.3 fixed the missing library resource classes, but the app still crashes after `onCreate` and before the settings load — deterministically, on the test device. The exact exception is still unknown: in 0.5.2/0.5.3 the safe-mode screen accidentally deleted the crash log (with the stack trace) when it exported the startup trace to the same file. 0.5.4 fixes the evidence chain: the trace now goes to a separate file (`Downloads/Ava/ava-startup-trace.txt`), the crash log (`Downloads/Ava/ava-crash-log.txt`) is kept, the stack trace is also written into the trace itself, and safe mode shows the last crash log with a share button. DataStore is also hardened (a corrupt settings file can no longer kill the app at startup). If you hit a crash, please share either file — or use the safe-mode "Share crash log" button.

## Contributing

Issues and pull requests are welcome. Please keep changes focused and describe what you tested.

## Acknowledgements

- [Media3 / ExoPlayer](https://developer.android.com/media/media3) for playback
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Room](https://developer.android.com/training/data-storage/room) for the UI and data layers
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font, licensed under the SIL Open Font License

## License

Source code is provided as-is for personal use. The Vazirmatn font is licensed under the SIL Open Font License.
