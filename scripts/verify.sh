#!/usr/bin/env bash
# Offline verification for Ava — used because the Gradle daemon cannot run
# in this sandbox (see ENV.md). Three steps, all honest:
#   1. aapt2 compile + link (resources + manifest -> fresh R.java)
#   2. kotlinc type-check of every main + test source (bundled Compose plugin)
#   3. Unit tests that need no Android runtime, run with JUnitCore.
# Toolchain + deps live under .build-tools/ (durable, survives /tmp wipes):
#   .build-tools/kotlinc-dist/  kotlin-compiler 2.3.21
#   .build-tools/ava-deps/      jars from scripts/maven-resolve.py
#   .build-tools/ava-cp.txt     colon-separated classpath from the resolver
# Usage: ./scripts/verify.sh
set -uo pipefail
cd "$(dirname "$0")/.."
source "$(dirname "$0")/java-env.sh"

TOOLS="$PWD/.build-tools"
AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
KOTLINC="$TOOLS/kotlinc-dist/bin/kotlinc"
# NOTE: android.jar is NOT a Maven artifact — the resolver cannot provide
# it. The old /tmp/ava-cp2.txt had it appended; append it here too, or
# kotlinc cannot resolve any android.* class (469 errors).
CP="$(cat "$TOOLS/ava-cp.txt"):$ANDROID_HOME/platforms/android-34/android.jar"
OUT="$PWD/.build-out/verify"
rm -rf "$OUT"
mkdir -p "$OUT/aapt2" "$OUT/classes" "$OUT/test-classes"

echo "== [1/3] aapt2 compile + link =="
"$AAPT2" compile --dir app/src/main/res -o "$OUT/aapt2/res.zip" || exit 1
# aapt2 link needs a package attribute, which AGP normally injects from the
# namespace; the real manifest (correctly) has none, so link a temp copy.
sed 's|<manifest |<manifest package="com.harper.ava" |' \
  app/src/main/AndroidManifest.xml > "$OUT/aapt2/AndroidManifest.xml"
"$AAPT2" link -o "$OUT/aapt2/linked.apk" \
  -I "$ANDROID_HOME/platforms/android-34/android.jar" \
  --manifest "$OUT/aapt2/AndroidManifest.xml" \
  --java "$OUT/aapt2/r" \
  "$OUT/aapt2/res.zip" || exit 1
echo "aapt2 OK: $(find "$OUT/aapt2/r" -name R.java)"

echo "== [1b/3] font audit (no system fonts) =="
./scripts/font-audit.sh || exit 1

echo "== [2/3] kotlinc type-check (all main + test sources) =="
find app/src/main/java app/src/test/java -name "*.kt" | sort > "$OUT/sources.txt"
"$KOTLINC" -cp "$CP" -d "$OUT/classes" \
  -Xplugin="$TOOLS/kotlinc-dist/lib/compose-compiler-plugin.jar" \
  -jvm-target 17 \
  $(cat "$OUT/sources.txt") "$OUT/aapt2/r/com/harper/ava/R.java" \
  > "$OUT/kotlinc.log" 2>&1
KOTLIN_EXIT=$?
grep -E "error:|warning:" "$OUT/kotlinc.log" | head -30 || true
echo "kotlinc exit=$KOTLIN_EXIT errors=$(grep -c 'error:' "$OUT/kotlinc.log") warnings=$(grep -c 'warning:' "$OUT/kotlinc.log") classes=$(find "$OUT/classes" -name '*.class' | wc -l)"
[ "$KOTLIN_EXIT" -ne 0 ] && { echo "kotlinc FAILED — see $OUT/kotlinc.log"; exit 1; }

echo "== [3/3] unit tests (JUnitCore) =="
JUNIT="$TOOLS/ava-deps/junit__junit-4.13.2.jar"
HAMCREST="$TOOLS/ava-deps/org.hamcrest__hamcrest-core-1.3.jar"
"$KOTLINC" -cp "$CP:$OUT/classes" -d "$OUT/test-classes" -jvm-target 17 \
  app/src/main/java/com/harper/ava/core/util/LibraryLogic.kt \
  app/src/main/java/com/harper/ava/core/util/TimeFormat.kt \
  app/src/main/java/com/harper/ava/core/util/SettingsLogic.kt \
  app/src/main/java/com/harper/ava/core/util/LyricsLogic.kt \
  app/src/main/java/com/harper/ava/core/util/SleepTimerLogic.kt \
  app/src/main/java/com/harper/ava/core/util/SearchHistoryLogic.kt \
  app/src/main/java/com/harper/ava/data/preferences/UserPreferences.kt \
  app/src/main/java/com/harper/ava/core/util/InsightsLogic.kt \
  app/src/main/java/com/harper/ava/core/util/DeleteSongLogic.kt \
  app/src/main/java/com/harper/ava/core/util/UpdateLogic.kt \
  app/src/main/java/com/harper/ava/core/util/PlaylistLogic.kt \
  app/src/main/java/com/harper/ava/ui/nowplaying/AmbientGlow.kt \
  app/src/main/java/com/harper/ava/core/util/MediaItemLogic.kt \
  app/src/main/java/com/harper/ava/ui/theme/SeasonalDecor.kt \
  app/src/main/java/com/harper/ava/data/local/db/Database.kt \
  app/src/main/java/com/harper/ava/data/local/db/AppDatabase.kt \
  app/src/main/java/com/harper/ava/data/repository/MusicRepository.kt \
  app/src/main/java/com/harper/ava/domain/model/Models.kt \
  app/src/main/java/com/harper/ava/ui/theme/AppTheme.kt \
  app/src/main/java/com/harper/ava/playback/AvaMediaNotificationProvider.kt \
  app/src/main/java/com/harper/ava/playback/NotificationLikeLogic.kt \
  app/src/test/java/com/harper/ava/TimeFormatTest.kt \
  app/src/test/java/com/harper/ava/QueueLogicTest.kt \
  app/src/test/java/com/harper/ava/ScanNormalizeTest.kt \
  app/src/test/java/com/harper/ava/LibraryLogicTest.kt \
  app/src/test/java/com/harper/ava/SettingsLogicTest.kt \
  app/src/test/java/com/harper/ava/LyricsLogicTest.kt \
  app/src/test/java/com/harper/ava/ListKeyTest.kt \
  app/src/test/java/com/harper/ava/PlaybackServiceTest.kt \
  app/src/test/java/com/harper/ava/SleepTimerLogicTest.kt \
  app/src/test/java/com/harper/ava/SearchHistoryLogicTest.kt \
  app/src/test/java/com/harper/ava/ToggleFavoriteTest.kt \
  app/src/test/java/com/harper/ava/PlaybackRecoveryTest.kt \
  app/src/test/java/com/harper/ava/MediaItemLogicTest.kt \
  app/src/test/java/com/harper/ava/AppThemeTest.kt \
  app/src/test/java/com/harper/ava/InsightsLogicTest.kt \
  app/src/test/java/com/harper/ava/DeleteSongLogicTest.kt \
  app/src/test/java/com/harper/ava/UpdateLogicTest.kt \
  app/src/test/java/com/harper/ava/SeasonalDecorTest.kt \
  app/src/test/java/com/harper/ava/CustomNotificationLogicTest.kt \
  app/src/test/java/com/harper/ava/AddToPlaylistTest.kt \
  app/src/test/java/com/harper/ava/StringsParityTest.kt \
  app/src/test/java/com/harper/ava/NotificationLikeTest.kt \
  app/src/test/java/com/harper/ava/AmbientGlowLogicTest.kt \
  app/src/test/java/com/harper/ava/PlaylistLogicTest.kt \
  "$OUT/aapt2/r/com/harper/ava/R.java" \
  > "$OUT/test-compile.log" 2>&1 || { echo "test compile FAILED"; cat "$OUT/test-compile.log"; exit 1; }
java -cp "$OUT/test-classes:$OUT/classes:$CP:$JUNIT:$HAMCREST" \
  org.junit.runner.JUnitCore \
  com.harper.ava.TimeFormatTest \
  com.harper.ava.QueueLogicTest \
  com.harper.ava.ScanNormalizeTest \
  com.harper.ava.LibraryLogicTest \
  com.harper.ava.SettingsLogicTest \
  com.harper.ava.LyricsLogicTest \
  com.harper.ava.ListKeyTest \
  com.harper.ava.PlaybackServiceTest \
  com.harper.ava.SleepTimerLogicTest \
  com.harper.ava.SearchHistoryLogicTest \
  com.harper.ava.ToggleFavoriteTest \
  com.harper.ava.PlaybackRecoveryTest \
  com.harper.ava.MediaItemLogicTest \
  com.harper.ava.AppThemeTest \
  com.harper.ava.InsightsLogicTest \
  com.harper.ava.DeleteSongLogicTest \
  com.harper.ava.UpdateLogicTest \
  com.harper.ava.SeasonalDecorTest \
  com.harper.ava.CustomNotificationLogicTest \
  com.harper.ava.AddToPlaylistTest \
  com.harper.ava.StringsParityTest \
  com.harper.ava.NotificationLikeTest \
  com.harper.ava.AmbientGlowLogicTest \
  com.harper.ava.PlaylistLogicTest 2>&1 | tail -5
