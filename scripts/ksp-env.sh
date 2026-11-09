#!/usr/bin/env bash
# Sourced by scripts/manual-build.sh (expects $ROOT and $TOOLS to be set).
# KSP 2.x standalone runtime — see scripts/KSP-NOTES.md for the research
# behind these paths (KSP 2.3.13 is KSP2, Analysis-API based; there is NO
# kotlinc -Xplugin jar for it — the old KSP1 plugin mode is dead).
KSP_RESEARCH="$TOOLS/ksp-research"
KSP_CP="$KSP_RESEARCH/symbol-processing-aa-embeddable-2.3.13.jar:$KSP_RESEARCH/symbol-processing-api-2.3.13.jar:$KSP_RESEARCH/symbol-processing-common-deps-2.3.13.jar:$KSP_RESEARCH/kotlin-stdlib-2.4.21.jar"
KSP_MAIN="com.google.devtools.ksp.cmdline.KSPJvmMain"
# Room annotation processor + its runtime deps, resolved separately by
#   python3 scripts/maven-resolve.py --extra androidx.room:room-compiler:2.8.5 \
#       --out .build-tools/ksp/processor-deps --cp-out .build-tools/ksp/processor-cp.txt
# Kept apart from the app classpath so processor jars never leak into the dex.
ROOM_PROCESSOR_CP="$(cat "$TOOLS/ksp/processor-cp.txt")"
