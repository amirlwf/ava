# Contributing to Ava

Issues and pull requests are welcome. A few rules keep the app clean:

## Issues

- Describe what happened, what you expected, and what you did — plus your
  phone model and Android version.
- If the app crashed, attach `Downloads/Ava/ava-crash-log.txt` (or
  `Downloads/Ava/ava-startup-trace.txt` if it won't start).

## Pull requests

- Keep changes focused: one fix or feature per pull request.
- Describe what you changed and **what you tested** (including what you
  could not test — a real device is not available in every environment).
- `./scripts/verify.sh` must pass: resources, font audit, type-check and
  all unit tests. New pure logic gets a unit test next to it.
- Commits and code comments are in **English**. User-facing strings go in
  `values/strings.xml` **and** `values-fa/strings.xml` — the parity test
  enforces this.
- Don't bump the version or touch `dist/` — releases are cut by the
  maintainer.
- Never claim a bug is fixed until it is verified on a real device.

## Code conventions

- Kotlin + Jetpack Compose, MVVM/Clean Architecture (see `ARCHITECTURE.md`).
- No system fonts anywhere: every text style uses Vazirmatn (body/UI) or
  Lalezar (display). Never bundle an unlicensed font.
- Every screen accounts for loading, empty and error states — not just the
  happy path.
- Motion lives in `ui/common/Motion.kt` (`AvaMotion`): screens never invent
  their own durations or spring constants.
- The notification stays on the Media3 `DefaultMediaNotificationProvider`
  subclass path. A from-scratch custom notification (RemoteViews) crashed
  twice on the test phone and is never coming back.

## Building

```bash
./gradlew assembleDebug   # needs JDK 17 + Android SDK 36
./scripts/verify.sh       # offline check: resources, fonts, types, tests
```

If Gradle cannot run in your environment, `scripts/manual-build.sh`
reproduces the same debug APK by hand — see `ENV.md`.
