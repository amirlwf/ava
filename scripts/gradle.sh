#!/usr/bin/env bash
# Ava build wrapper: regenerates the Gradle proxy/TLS settings from the live
# environment, then runs the Gradle wrapper with the given arguments.
# Usage: ./scripts/gradle.sh assembleDebug
set -euo pipefail
cd "$(dirname "$0")/.."
source "$(dirname "$0")/java-env.sh"
python3 "$(dirname "$0")/gen-gradle-properties.py"
exec ./gradlew "$@"
