# Ava — Premium Offline Music Player for Android

Ava is a fully offline, premium music player for Android. No account, no internet, no ads, no tracking. Just your music.

- **Stack:** Kotlin, Jetpack Compose (Material 3), Media3 / ExoPlayer, MediaSession, Room, DataStore
- **Package:** `com.harper.ava`
- **Min SDK:** 26 · **Target SDK:** 34
- **Version:** 0.5.2 (diagnostic build, see Status)

## Features

- **Library** — MediaStore scan with Artists, Albums, Genres and Folders views
- **Playback** — background playback with MediaSession, queue with drag-and-drop reorder, shuffle, repeat (off / all / one), playback speed 0.75–2x
- **Now Playing** — adaptive blurred background, seek bar, synced LRC lyrics with follow toggle
- **Playlists and Favorites** — create and manage playlists, mark favorites
- **Sleep timer** — presets and custom durations, with fade-out in the last 10 seconds
- **Equalizer** — system equalizer with presets and per-band controls
- **Themes** — Dark, Light, System and AMOLED, with accent colors
- **Languages** — full Persian (RTL) and English (LTR), switchable in the app
- **Resume playback** — picks up where you left off, including the full queue

## Install

Download the APK from the [Releases](../../releases) page and install it on your phone. No account or setup is needed.

## Build

**GitHub Actions (recommended).** Every push to `main` builds a debug APK automatically. Download it from the Actions tab.

```bash
./gradlew assembleDebug
```

**Manual build (no Gradle).** If Gradle cannot run in your environment, `scripts/manual-build.sh` reproduces the same debug APK by hand (aapt2, Room KSP, kotlinc, d8, zipalign, apksigner). See the script for details.

```bash
./scripts/manual-build.sh
```

The Gradle-built APK is at `app/build/outputs/apk/debug/app-debug.apk`. The manual build writes to `dist/ava-<version>-debug.apk`.

## Status

**0.5.2 is a diagnostic build.** The app currently crashes on launch on some devices, and the cause is still under investigation. This build records a startup trace and writes the crash log to `Downloads/Ava/ava-crash-log.txt` on the device, and opens a safe-mode screen if a previous launch crashed during startup. Please share that log file if you hit the crash.

## License

Source code is provided as-is for personal use. The Vazirmatn font is licensed under the SIL Open Font License.

---

## آوا — موزیک‌پلیر آفلاین و حرفه‌ای اندروید

آوا یک موزیک‌پلیر کاملاً آفلاین برای اندروید است؛ بدون حساب کاربری، بدون اینترنت، بدون تبلیغ و بدون ردیابی.

- کتابخانه کامل (آهنگ‌ها، خواننده‌ها، آلبوم‌ها، ژانرها و پوشه‌ها)، پخش پس‌زمینه، صف پخش با جابه‌جایی، تکرار و پخش تصادفی
- متن ترانه همگام (LRC)، تایمر خواب با محو تدریجی صدا، اکولایزر، پلی‌لیست و علاقه‌مندی‌ها
- تم تیره، روشن و AMOLED، و رابط کامل فارسی (راست‌به‌چپ) و انگلیسی

**نصب:** فایل APK را از صفحه Releases دانلود و نصب کنید.

**وضعیت:** نسخه ۰٫۵٫۲ یک نسخه تشخیصی است؛ کرش موقع باز شدن روی بعضی گوشی‌ها هنوز در دست بررسی است و این نسخه دلیل کرش را در فایل `Downloads/Ava/ava-crash-log.txt` ذخیره می‌کند.
