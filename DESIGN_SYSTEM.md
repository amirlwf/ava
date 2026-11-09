# DESIGN_SYSTEM.md — Ava (آوا)

Single source of truth: `ui/theme/` (`Tokens.kt`, `Color.kt`, `Type.kt`,
`Theme.kt`). No screen hard-codes colors, spacing, radii or fonts.

## Identity

Premium offline music player. Modern minimalism, cinematic surfaces,
immersive artwork, elegant gradients, layered surfaces, subtle glass
effects, adaptive color. Original — not a Spotify/Apple Music copy.

## Color

- **Background (dark):** charcoal `#12121A` (not pure black — keeps
  elevation visible). **AMOLED:** true black `#000000`.
- **Surface (dark):** `#1C1C28`; surface variant `#262636`.
- **Light:** background `#FAFAFC`, surface white, variant `#F0F0F5`.
- **Text:** dark `onBackground #F4F4F8`, `onSurfaceVariant #9C9CAD`;
  light `onBackground #17171C`, `onSurfaceVariant #5F5F6E`.
- **Accent (default Violet `#7C5CFF`):** 7 palettes, each with
  `primary / onPrimary / secondary / tertiary / primaryContainer /
  onPrimaryContainer` — see `accentPalette()` in `Color.kt`:
  Violet, Electric Blue, Emerald, Crimson, Amber, Rose, Monochrome.
- `AvaTheme(themeMode, accent)` builds the Material 3 `ColorScheme`
  (dark / AMOLED / light) from the palette; every screen reads the
  scheme, never a literal color.

## Typography

One typeface for fa + en: **Vazirmatn v33.003 (SIL OFL)** —
Regular 400, Medium 500, SemiBold 600, Bold 700
(`res/font/vazirmatn_*.ttf`). `AvaTypography` maps the Material 3
type scale onto Vazirmatn (display 32 → label 12). Long song titles
ellipsize at 1 line; nothing overflows.

## Tokens (`AvaTokens`)

- Spacing on a 4dp grid: 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64.
- Radii: small 8, medium 12, large 16, extra-large 24, full 100.
- Sizes: song row 64dp, queue row 72dp (fixed — the drag math relies
  on it), cover small 48dp / medium 56dp, Now Playing cover 300dp,
  mini player 64dp, empty-state icon disc 96dp, min touch target 48dp.

## Components

- **EmptyState** — radial-gradient accent disc (96dp) + icon, title,
  body, optional honest note. Shared by Home / Library / Search /
  Playlists; emptiness never looks like a blank page.
- **SongRow** — 48dp cover (Coil artwork or tonal placeholder with a
  music note), title + `artist • duration`, favorite toggle.
  64dp tall, 12dp radius, 12dp horizontal padding.
- **MiniPlayer** — surface-container-high strip above the bottom bar:
  44dp cover, title/artist, play-pause + next, thin linear progress.
  Shown only while a song is loaded. Tapping the song info opens Now
  Playing; hidden on the Now Playing / Queue routes.
- **Now Playing** — full-screen: cover art heavily blurred as the
  adaptive background under a dark scrim (accent gradient fallback
  when there is no art), 300dp cover with a fade+scale track-change
  animation, title/artist/album, seekbar with elapsed/remaining,
  72dp primary play/pause button, shuffle/repeat/favorite/speed/queue
  controls. Cover art that fails to load falls back to the music-note
  placeholder (same rule as SongRow / MiniPlayer / Queue rows).
- **Queue rows** — fixed 72dp rows: cover, title, artist • duration,
  equalizer icon on the playing song, drag handle (long-press to
  reorder), remove button. The playing row is tinted
  `primaryContainer` at 45% alpha.
- **Bottom navigation** — Material 3 `NavigationBar`, 4 destinations,
  labels from strings (fa/en).
- **Library tabs** — `PrimaryScrollableTabRow`, 5 tabs
  (Songs / Artists / Albums / Folders / Genres); scrollable so labels
  never ellipsize on narrow screens.
- **Sleep Timer** — large countdown (`displayMedium`), ready-made
  duration chips + numeric custom field (1–480 min), cancel button;
  the Now Playing overflow item shows the live remaining time while
  a timer runs.
- **Equalizer** — enable switch, device preset chips (+ a disabled
  "Custom" status chip when bands are manual), one slider per band
  with frequency label (`60 Hz` / `1.2 kHz`) and dB value, reset
  button. Unsupported devices get an `EmptyState` with the honest
  reason instead of fake sliders.
- **Lyrics** — centered lines; the playing line is `titleLarge` in
  primary on a 45%-alpha `primaryContainer` disc, other lines are
  `bodyLarge` in `onSurfaceVariant`; auto-scroll keeps 2 lines of
  context above the current line. Untimed files render as plain text.
- **Song Details** — 96dp cover header, then `SettingsInfoRow`s in
  two sections (File / Metadata); unknowable values show the
  localized "Unknown".
- **SAF folder rows** — folder icon + name + audio-file count (or a
  red "access lost" note) + remove button; pick button below the list.
- **Settings rows** — shared components in `ui/settings/
  SettingsComponents.kt`: `SettingsNavRow` (icon + title + subtitle +
  chevron), `SettingsChoiceRow` (radio), `SettingsSwitchRow`,
  `SettingsInfoRow` (label + value), `SettingsNote`. All screens use
  the same 20dp horizontal padding and `titleSmall` primary-colored
  section headers.
- **Accent swatches** — 48dp circles in a `FlowRow`; the selected one
  gets a 2dp `onBackground` ring + check. **Color picker** — HSV
  sliders (hue 0–360, saturation/value 0–1) + live preview circle +
  hex; sliders only change the preview, Apply persists one DataStore
  value.

## New components (stage 6)

- **Splash** — charcoal background, 96dp primaryContainer disc with a
  music note, "Ava" in `displayMedium` + subtitle. Shown only while
  settings load; no fake delay.
- **Onboarding** — 3-page `HorizontalPager` (welcome / personalization /
  permission) with dots, Next / Start buttons and a skip action on
  every page; the permission page runs a real scan with progress and
  denied/error states.
- **Favorites screen** — Play All (filled) + Shuffle All (outlined)
  side by side above the full favorite list.
- **Playlist rows** — 48dp cover (first song's art or tonal
  queue-music placeholder) + name + real song count, on Home and in
  the Playlists tab.
- **Lyrics follow pill** — a `FilledTonalButton` ("Follow the playing
  line") appears at the bottom when the user has scrolled away from
  the playing line; hand-scrolling pauses following.
- **Search history chips** — `InputChip`s for recent searches with a
  per-chip remove (×) and a clear-all action.
- **Now Playing cover** — adaptive size (min of 300dp, available width,
  45% of height) with a soft 16dp shadow over the blurred background.

## Motion (stage 1 — foundation only)

No custom animation system yet; Material 3 defaults (ripple, navigation
transitions) apply. The full Motion Design System (shared-element
transitions, animated mini player, skeleton loading, haptics) is a
later stage — see `AGENT_PROGRESS.md`. All motion must respect
`prefers-reduced-motion` / system animator settings when added.

Stage 6 additions: the Mini Player cross-fades song info on track
change (`AnimatedContent`), the onboarding pager animates between
pages, and the lyrics list animates to the playing line — all with
Material defaults otherwise.

## RTL / LTR

`supportsRtl=true`. All layouts use start/end padding and Material 3
components, so fa (RTL) and en (LTR) mirror automatically. Strings are
fully separated: `values/strings.xml` (en), `values-fa/strings.xml`
(fa). No user-facing string is hard-coded in Kotlin code.
