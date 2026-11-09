#!/usr/bin/env bash
# Ava (آوا) — MANUAL debug APK build, no Gradle.
# Gradle cannot run in this sandbox (its daemon needs raw loopback TCP,
# which the sandbox filter blocks — see ENV.md), so this script reproduces
# what `assembleDebug` does, step by step, with the plain Android tools:
#   1. aapt2 compile + link (resources + manifest -> APK skeleton + R.java)
#   2. KSP 2.x standalone JVM app -> Room generated code (see KSP-NOTES.md)
#   3. kotlinc + Compose plugin -> app classes (incl. KSP output + R.java)
#   4. d8 -> classes.dex (+ multidex as needed)
#   5. package dex + META-INF/services (see package-services.py — 0.5.0
#      crashed on launch without the coroutines MainDispatcherFactory
#      service file) into the APK, zipalign, sign with a debug keystore
#   6. verify: apksigner verify + aapt2 dump badging
#
# Prerequisites (durable, under .build-tools/ — never /tmp):
#   .build-tools/kotlinc-dist/   kotlin-compiler 2.3.21
#   .build-tools/ava-deps/       jars from scripts/maven-resolve.py
#   .build-tools/ava-cp.txt      colon-separated classpath from the resolver
#   .build-tools/ksp/            KSP runtime jars + room-compiler processor cp
# Usage: ./scripts/manual-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source "$(dirname "$0")/java-env.sh"

ROOT="$PWD"
TOOLS="$ROOT/.build-tools"
OUT="$ROOT/.build-out"
DIST="$ROOT/dist/ava-0.5.2-debug.apk"
AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
# d8 8.2.2-dev (build-tools 34.0.0) NPEs on androidx.concurrent:concurrent-futures
# 1.2.0 (CallbackToFutureAdapter$SafeFuture$1.class) — a genuine d8 bug fixed
# in later R8. Use d8 8.10.9-dev from build-tools 36.0.0 (downloaded to
# .build-tools/build-tools-36/android-16/); aapt2 stays at 34.0.0 to match
# compileSdk 34.
D8="$ROOT/.build-tools/build-tools-36/android-16/d8"
ZIPALIGN="$ANDROID_HOME/build-tools/34.0.0/zipalign"
APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
KEYTOOL="$JAVA_HOME/bin/keytool"
KOTLINC="$TOOLS/kotlinc-dist/bin/kotlinc"
COMPOSE_PLUGIN="$TOOLS/kotlinc-dist/lib/compose-compiler-plugin.jar"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
CP="$(cat "$TOOLS/ava-cp.txt")"
# android.jar is NOT a Maven artifact (the resolver cannot provide it) —
# it belongs on the kotlinc/KSP classpaths, but NEVER in the d8 dex input
# (it is passed to d8 separately via --lib).
KEYSTORE="$TOOLS/debug.keystore"

mkdir -p "$OUT/aapt2" "$OUT/classes" "$OUT/dex" "$OUT/ksp" "$ROOT/dist"

echo "== [1/7] aapt2 compile + link =="
"$AAPT2" compile --dir app/src/main/res -o "$OUT/aapt2/res.zip"
# aapt2 link needs a package attribute (AGP injects it from the namespace)
# and the library-manifest components (AGP merges them from the AARs);
# both are reproduced by merge-manifest.py — the source manifest is untouched.
python3 "$(dirname "$0")/merge-manifest.py" \
  app/src/main/AndroidManifest.xml "$OUT/aapt2/AndroidManifest.xml" com.harper.ava
"$AAPT2" link -o "$OUT/unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$OUT/aapt2/AndroidManifest.xml" \
  --java "$OUT/aapt2/r" \
  --version-code 7 --version-name 0.5.2 \
  --min-sdk-version 26 --target-sdk-version 34 \
  -A app/src/main/assets \
  "$OUT/aapt2/res.zip"
echo "aapt2 OK: $(find "$OUT/aapt2/r" -name R.java)"

echo "== [2/7] KSP (Room codegen, standalone JVM app) =="
# KSP 2.x runs as a standalone JVM app, NOT as a kotlinc -Xplugin
# (the old KSP1 compiler-plugin jar does not exist for 2.3.13).
# See scripts/KSP-NOTES.md for the research behind this invocation.
# shellcheck disable=SC1091
source "$(dirname "$0")/ksp-env.sh"   # sets KSP_CP, KSP_MAIN, ROOM_PROCESSOR_CP
java -cp "$KSP_CP" "$KSP_MAIN" \
  -language-version=2.3 \
  -api-version=2.3 \
  -jvm-target=17 \
  -module-name=app \
  -source-roots="$ROOT/app/src/main/java" \
  -project-base-dir="$ROOT" \
  -output-base-dir="$OUT/ksp" \
  -caches-dir="$OUT/ksp/caches" \
  -class-output-dir="$OUT/ksp/classes" \
  -kotlin-output-dir="$OUT/ksp/generated/kotlin" \
  -java-output-dir="$OUT/ksp/generated/java" \
  -resource-output-dir="$OUT/ksp/generated/resources" \
  -jdk-home="$JAVA_HOME" \
  -libraries="$CP:$ANDROID_JAR" \
  "$ROOM_PROCESSOR_CP" \
  > "$OUT/ksp.log" 2>&1 || {
    echo "KSP FAILED — see $OUT/ksp.log"; tail -40 "$OUT/ksp.log"; exit 1
  }
echo "KSP OK: generated files:"
find "$OUT/ksp/generated" -type f | sort

echo "== [3/7] kotlinc compile (app + R + KSP output) =="
find app/src/main/java -name "*.kt" | sort > "$OUT/sources.txt"
find "$OUT/ksp/generated" \( -name '*.kt' -o -name '*.java' \) | sort >> "$OUT/sources.txt"
# shellcheck disable=SC2086
"$KOTLINC" -cp "$CP:$ANDROID_JAR" -d "$OUT/classes" \
  -Xplugin="$COMPOSE_PLUGIN" \
  -jvm-target 17 \
  $(cat "$OUT/sources.txt") "$OUT/aapt2/r/com/harper/ava/R.java" \
  > "$OUT/kotlinc.log" 2>&1 || {
    echo "kotlinc FAILED — see $OUT/kotlinc.log"
    grep -E "error:|warning:" "$OUT/kotlinc.log" | head -30 || true
    exit 1
  }
echo "kotlinc OK: classes=$(find "$OUT/classes" -name '*.class' | wc -l)"
grep -E "error:|warning:" "$OUT/kotlinc.log" | head -30 || true

echo "== [4/7] d8 dex =="
# The classpath has 634 duplicated .class files (-android vs -jvm vs KMP-root
# variants of the same library). d8 errors on duplicates, so dex-input.py
# extracts everything into one directory, newest-version-wins per class.
python3 "$(dirname "$0")/dex-input.py" \
  --cp-file "$TOOLS/ava-cp.txt" --classes-dir "$OUT/classes" --out "$OUT/dex-input" \
  --jar-out "$OUT/dex-input.jar"
# Clean stale dex files so a shrinking build never packages leftovers.
rm -f "$OUT"/dex/classes*.dex
"$D8" --min-api 26 --release \
  --lib "$ANDROID_JAR" \
  --output "$OUT/dex" \
  "$OUT/dex-input.jar" \
  > "$OUT/d8.log" 2>&1 || {
    echo "d8 FAILED — see $OUT/d8.log"; tail -30 "$OUT/d8.log"; exit 1
  }
echo "d8 OK: $(ls "$OUT/dex"/classes*.dex | wc -l) dex file(s)"

echo "== [5/7] package dex + META-INF/services into APK =="
python3 - "$OUT/unsigned.apk" "$OUT/dex" <<'EOF'
import glob, sys, zipfile
apk, dex_dir = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    for dex in sorted(glob.glob(f"{dex_dir}/classes*.dex")):
        name = dex.split("/")[-1]
        z.write(dex, name, compress_type=zipfile.ZIP_STORED)
print("packaged:", sorted(glob.glob(f"{dex_dir}/classes*.dex")))
EOF
# 0.5.0 lesson: AGP merges META-INF/services/* from dependency jars into
# the APK; the manual build dropped them, so Dispatchers.Main crashed the
# app in Application.onCreate (see scripts/package-services.py).
python3 "$(dirname "$0")/package-services.py" "$OUT/unsigned.apk" "$TOOLS/ava-cp.txt"

echo "== [6/7] zipalign + sign =="
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android \
  --key-pass pass:android --out "$DIST" "$OUT/aligned.apk"

echo "== [7/7] verify =="
"$APKSIGNER" verify --verbose "$DIST"
"$AAPT2" dump badging "$DIST" | head -8
unzip -l "$DIST" | tail -15
ls -la "$DIST"
echo "DONE: $DIST"
