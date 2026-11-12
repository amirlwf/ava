#!/usr/bin/env bash
# Zero-system-font audit for Ava (0.7.0 design round).
#
# Goal: no surface of the app renders text in the phone's system font —
# only Vazirmatn (body/UI) and Lalezar (display), both SIL OFL 1.1.
# Run: ./scripts/font-audit.sh        (exit 0 = clean, exit 1 = leak found)
#
# Checks:
#   1. No system FontFamily constant (Default/SansSerif/Serif/Monospace/
#      Cursive) anywhere in main sources.
#   2. No TextStyle( outside ui/theme/Type.kt, and every TextStyle in
#      Type.kt sets a fontFamily.
#   3. Every `fontFamily =` assignment is Vazirmatn or Lalezar.
#   4. All fifteen Material3 Typography styles are defined in Type.kt —
#      any style left out silently falls back to the Material3 default,
#      whose fontFamily is FontFamily.Default (system font). This was a
#      real leak before 0.7.0: displayMedium, titleSmall and labelSmall
#      were undefined, so the splash app name, the sleep-timer countdown,
#      every settings section header and the accent-swatch labels
#      rendered in the system font.
#   5. No android.graphics.Typeface system constant (DEFAULT/MONOSPACE/
#      ...) outside the one documented fallback (below).
#   6. res/font holds only the two licensed faces (never dana/manshoor).
#   7. No android:fontFamily / android:typeface attribute in res XML.
#   8. Every OutlinedTextField/BasicTextField passes an explicit
#      textStyle from MaterialTheme.typography (its default is
#      LocalTextStyle, which the theme covers — the explicit style makes
#      the wiring grep-able and immune to a future LocalTextStyle change).
#
# DOCUMENTED EXCEPTIONS (do not "fix" — they are not leaks the app can
# remove; keep them listed here so a future grep hit is a real signal):
#   E1. Media notification: rendered by SystemUI with the system font.
#       A MediaStyle notification's RemoteViews text cannot carry an app
#       typeface and NotificationCompat.Builder has no setTypeface.
#       See the KDoc in playback/AvaMediaNotificationProvider.kt.
#       (No code hits: the provider never references a system font.)
#   E2. Android Toast: rendered by the system with the system font.
#       The app currently shows no Toast at all (grep finds none); if
#       one is ever added it stays system-font by platform design.
#   E3. Safe-mode fallback: MainActivity.avaTypeface() loads Vazirmatn
#       from res/font and falls back to Typeface.DEFAULT(_BOLD) only if
#       that load throws — so the crash-reporting screen itself can never
#       crash on a font load. That single fallback line is allow-listed
#       in check 5 below.
set -uo pipefail
cd "$(dirname "$0")/.."

SRC="app/src/main/java"
TYPE="app/src/main/java/com/harper/ava/ui/theme/Type.kt"
FAIL=0

fail() { printf 'FAIL: %b\n' "$1"; FAIL=1; }
pass() { echo "ok: $1"; }

# 1. System FontFamily constants (KDoc/comment lines excluded — they only
#    document the default, they do not use it).
if HITS=$(grep -rn --include='*.kt' -E 'FontFamily\.(Default|SansSerif|Serif|Monospace|Cursive)' "$SRC" | grep -vE '^\s*[^:]+:[0-9]+:\s*\*'); then
    fail "system FontFamily in use:\n$HITS"
else
    pass "no system FontFamily constant"
fi

# 2. TextStyle( only in Type.kt, and each one there sets a fontFamily.
if HITS=$(grep -rn --include='*.kt' 'TextStyle(' "$SRC" | grep -v "$TYPE"); then
    fail "TextStyle( outside ui/theme/Type.kt:\n$HITS"
else
    pass "TextStyle( only in Type.kt"
fi
STYLE_COUNT=$(grep -c 'TextStyle(' "$TYPE")
FAMILY_COUNT=$(grep -c 'fontFamily =' "$TYPE")
if [ "$STYLE_COUNT" != "$FAMILY_COUNT" ]; then
    fail "Type.kt has $STYLE_COUNT TextStyle( but only $FAMILY_COUNT fontFamily assignments"
else
    pass "every TextStyle in Type.kt sets a fontFamily ($STYLE_COUNT)"
fi

# 3. Only Vazirmatn / Lalezar as fontFamily values.
if HITS=$(grep -rn --include='*.kt' 'fontFamily =' "$SRC" | grep -vE 'fontFamily = (Vazirmatn|Lalezar),'); then
    fail "fontFamily other than Vazirmatn/Lalezar:\n$HITS"
else
    pass "all fontFamily assignments are Vazirmatn or Lalezar"
fi

# 4. All fifteen Material3 Typography styles defined.
MISSING=""
for STYLE in displayLarge displayMedium displaySmall headlineLarge headlineMedium headlineSmall titleLarge titleMedium titleSmall bodyLarge bodyMedium bodySmall labelLarge labelMedium labelSmall; do
    if ! grep -q "^    $STYLE = TextStyle(" "$TYPE"; then
        MISSING="$MISSING $STYLE"
    fi
done
if [ -n "$MISSING" ]; then
    fail "Type.kt is missing Typography styles:$MISSING (they would fall back to FontFamily.Default)"
else
    pass "all 15 Material3 Typography styles defined in Type.kt"
fi

# 5. System Typeface constants — except the safe-mode fallback (E3).
#    The allow-listed line is the `?:` fallback inside avaTypeface().
if HITS=$(grep -rn --include='*.kt' -E 'Typeface\.(DEFAULT_BOLD|DEFAULT|MONOSPACE|SANS_SERIF|SERIF)' "$SRC" | grep -vF 'getOrNull() ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT' | grep -vE '^\s*[^:]+:[0-9]+:\s*\*'); then
    fail "system Typeface in use (outside the documented safe-mode fallback):\n$HITS"
else
    pass "no system Typeface outside the safe-mode fallback"
fi

# 6. res/font: only the two licensed faces.
if HITS=$(ls app/src/main/res/font/ | grep -vE '^(vazirmatn_(regular|medium|semibold|bold)\.ttf|lalezar_regular\.ttf)$'); then
    fail "unexpected file in res/font (only Vazirmatn + Lalezar are licensed for bundling):\n$HITS"
else
    pass "res/font holds only Vazirmatn + Lalezar"
fi

# 7. No font attributes in res XML.
if HITS=$(grep -rn -E 'android:(fontFamily|typeface)' app/src/main/res/); then
    fail "font attribute in res XML:\n$HITS"
else
    pass "no android:fontFamily / android:typeface in res XML"
fi

# 8. Every text field passes an explicit textStyle.
FIELD_FAIL=""
while IFS=: read -r FILE LINE _; do
    if ! sed -n "${LINE},$((LINE + 20))p" "$FILE" | grep -q 'textStyle = MaterialTheme.typography'; then
        FIELD_FAIL="$FIELD_FAIL\n  $FILE:$LINE"
    fi
done <<EOF
$(grep -rn --include='*.kt' -E '(OutlinedTextField|BasicTextField)\(' "$SRC" | cut -d: -f1,2)
EOF
if [ -n "$FIELD_FAIL" ]; then
    fail "text field without explicit textStyle:$FIELD_FAIL"
else
    pass "all text fields pass an explicit MaterialTheme textStyle"
fi

if [ "$FAIL" -ne 0 ]; then
    echo "font-audit: FAILED — system-font leak(s) above"
    exit 1
fi
echo "font-audit: PASS — zero system font (exceptions E1–E3 documented above)"
