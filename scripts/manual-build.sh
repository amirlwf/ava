#!/usr/bin/env bash
# Ava (آوا) — MANUAL debug APK build, no Gradle.
# Gradle cannot run in this sandbox (its daemon needs raw loopback TCP,
# which the sandbox filter blocks — see ENV.md), so this script reproduces
# what `assembleDebug` does, step by step, with the plain Android tools:
#   1. aapt2 compile + link (resources + manifest -> APK skeleton + R.java)
#      — via merge-resources.py, which also merges LIBRARY resources from
#      the real AARs (see scripts/merge-resources.py — 0.5.0/0.5.1/0.5.2
#      crashed on launch because library R classes were never generated)
#   2. KSP 2.x standalone JVM app -> Room generated code (see KSP-NOTES.md)
#   3. kotlinc + Compose plugin -> app classes (incl. KSP output + R.java)
#   3b. javac -> R classes (kotlinc does NOT emit .class for .java
#      sources — the R classes must be compiled separately, or the
#      library code's getstatic on R$id etc. has no class to read)
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
#   .build-tools/real-aars/      real AARs from scripts/download-aars.py
#                                (library resources for merge-resources.py)
#   .build-tools/ksp/            KSP runtime jars + room-compiler processor cp
# Usage: ./scripts/manual-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source "$(dirname "$0")/java-env.sh"

ROOT="$PWD"
TOOLS="$ROOT/.build-tools"
OUT="$ROOT/.build-out"
DIST="$ROOT/dist/ava-0.5.3-debug.apk"
AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
# d8 8.2.2-dev (build-tools 34.0.0) NPEs on androidx.concurrent:concurrent-futures
# 1.2.0 (CallbackToFutureAdapter$SafeFuture$1.class) — a genuine d8 bug fixed
# in later R8. Use d8 8.10.9-dev from build-tools 36.0.0 (downloaded to
# .build-tools/build-tools-36/android-16/); aapt2 stays at 34.0.0.
# NOTE: Gradle compileSdk is 37 (libraries require it — see CI runs
# 42646098518/42647750383), but this manual build still compiles against
# android.jar from platforms/android-34: the code uses no API 35+ symbols,
# so the older android.jar is sufficient and no android-37 platform is
# needed locally. If code ever uses API 35+, download platforms;android-37
# and point ANDROID_JAR at it.
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

echo "== [1/7] aapt2 compile + link (app + library resources) =="
"$AAPT2" compile --dir app/src/main/res -o "$OUT/aapt2/res.zip"
# aapt2 link needs a package attribute (AGP injects it from the namespace)
# and the library-manifest components (AGP merges them from the AARs);
# both are reproduced by merge-manifest.py — the source manifest is untouched.
python3 "$(dirname "$0")/merge-manifest.py" \
  app/src/main/AndroidManifest.xml "$OUT/aapt2/AndroidManifest.xml" com.harper.ava
# 0.5.3: the app's res alone is NOT enough — library resources must be
# merged too, or the library R classes (androidx.lifecycle.runtime.R$id
# etc.) never exist and the app dies with NoClassDefFoundError at the
# first setContent. merge-resources.py compiles every library res/ from
# the real AARs, links them all, and writes the list of generated
# R.java files to $OUT/aapt2/r-java-files.txt for the kotlinc step.
python3 "$(dirname "$0")/merge-resources.py" \
  --cp-files "$TOOLS/ava-cp.txt,$TOOLS/ava-cp-coil.txt" \
  --aars-dir "$TOOLS/real-aars" \
  --app-res-zip "$OUT/aapt2/res.zip" \
  --manifest "$OUT/aapt2/AndroidManifest.xml" \
  --assets-dir app/src/main/assets \
  --out-dir "$OUT/aapt2" \
  --apk-out "$OUT/unsigned.apk" \
  --r-out "$OUT/aapt2/r" \
  --aapt2 "$AAPT2" --android-jar "$ANDROID_JAR" \
  --version-code 8 --version-name 0.5.3

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
  $(cat "$OUT/sources.txt") $(cat "$OUT/aapt2/r-java-files.txt") \
  > "$OUT/kotlinc.log" 2>&1 || {
    echo "kotlinc FAILED — see $OUT/kotlinc.log"
    grep -E "error:|warning:" "$OUT/kotlinc.log" | head -30 || true
    exit 1
  }
echo "kotlinc OK: classes=$(find "$OUT/classes" -name '*.class' | wc -l)"
grep -E "error:|warning:" "$OUT/kotlinc.log" | head -30 || true

echo "== [3b/7] javac compile (R.java -> R classes) =="
# kotlinc does NOT emit .class files for .java sources (verified
# 2026-11-09: com.harper.ava.R is absent from the 0.5.2 dex even though
# its R.java was passed to kotlinc — the app only worked because the
# app's own R fields are compile-time constants inlined by kotlinc).
# Library R fields are NON-final, so library code does a real getstatic
# on the R class at runtime — the R classes must really exist in the
# dex. Compile all generated R.java files with javac into $OUT/classes
# (R.java files are pure int constants; no classpath needed).
"$JAVA_HOME/bin/javac" -d "$OUT/classes" \
  $(cat "$OUT/aapt2/r-java-files.txt") \
  > "$OUT/javac.log" 2>&1 || {
    echo "javac FAILED — see $OUT/javac.log"; tail -20 "$OUT/javac.log"; exit 1
  }
echo "javac OK: R classes=$(find "$OUT/classes" -name 'R.class' | wc -l), R\$ nested=$(find "$OUT/classes" -name 'R$*.class' | wc -l)"

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
# NB: `| head -8` closes the pipe while aapt2 still has 100+ lines to
# write; aapt2 then dies with SIGPIPE (141) and `set -o pipefail`
# + `set -e` would abort the script AFTER a successful build
# (this is exactly what happened on the 0.5.3 build: exit 141 with a
# good APK). `|| true` swallows the expected SIGPIPE.
"$AAPT2" dump badging "$DIST" | head -8 || true
unzip -l "$DIST" | tail -15
ls -la "$DIST"
echo "DONE: $DIST"
