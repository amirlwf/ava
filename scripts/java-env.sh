#!/usr/bin/env bash
# Java/Android environment for the Ava project.
# NOTE: No JDK_JAVA_OPTIONS here on purpose — the JVM prints that variable's
# value to stderr ("Picked up JDK_JAVA_OPTIONS"), which would leak the
# rotating egress-proxy password into logs. Proxy/TLS settings for Gradle
# are generated at build time by scripts/gradle.sh into ~/.gradle/gradle.properties.
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# Gradle's JVM takes user.home from the passwd entry (root -> /root), NOT from
# $HOME, so pin GRADLE_USER_HOME explicitly to keep caches + gradle.properties
# in one predictable place.
export GRADLE_USER_HOME="$HOME/.gradle"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
