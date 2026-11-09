#!/usr/bin/env python3
"""Regenerate ~/.gradle/gradle.properties proxy/TLS block from live env.

The sandbox egress proxy uses rotating credentials (present in $https_proxy
only for the current shell). This script derives host/port/user/password at
build time and writes them to the USER-HOME gradle.properties (never into
the project repo). It preserves every other line already in that file.
The JVM truststore holds the sandbox egress CA bundle so Gradle's HTTPS
downloads (Google Maven / Maven Central / Plugin Portal) verify correctly.
"""
import os
import re
import sys
from urllib.parse import urlparse

HOME = os.path.expanduser("~")
GUH = os.environ.get("GRADLE_USER_HOME") or os.path.join(HOME, ".gradle")
TARGET = os.path.join(GUH, "gradle.properties")
TRUSTSTORE = os.path.join(HOME, "Android", "java-truststore.jks")

MANAGED_PREFIXES = (
    "systemProp.http.proxyHost",
    "systemProp.http.proxyPort",
    "systemProp.http.proxyUser",
    "systemProp.http.proxyPassword",
    "systemProp.http.nonProxyHosts",
    "systemProp.https.proxyHost",
    "systemProp.https.proxyPort",
    "systemProp.https.proxyUser",
    "systemProp.https.proxyPassword",
    "systemProp.https.nonProxyHosts",
    "org.gradle.jvmargs",
)


def main() -> int:
    proxy = os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY") or ""
    lines: list[str] = []
    if os.path.exists(TARGET):
        with open(TARGET) as f:
            for raw in f:
                stripped = raw.strip()
                if any(stripped.startswith(p + "=") for p in MANAGED_PREFIXES):
                    continue
                lines.append(raw.rstrip("\n"))

    block = ["# --- managed by music-player/scripts/gradle.sh (rotating proxy creds) ---"]
    if proxy:
        u = urlparse(proxy)
        block += [
            f"systemProp.http.proxyHost={u.hostname}",
            f"systemProp.http.proxyPort={u.port}",
            f"systemProp.https.proxyHost={u.hostname}",
            f"systemProp.https.proxyPort={u.port}",
            "systemProp.http.nonProxyHosts=localhost|127.0.0.1",
            "systemProp.https.nonProxyHosts=localhost|127.0.0.1",
        ]
        if u.username:
            block += [
                f"systemProp.http.proxyUser={u.username}",
                f"systemProp.http.proxyPassword={u.password or ''}",
                f"systemProp.https.proxyUser={u.username}",
                f"systemProp.https.proxyPassword={u.password or ''}",
            ]
    block.append(
        "org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8 "
        f"-Djavax.net.ssl.trustStore={TRUSTSTORE} "
        "-Djavax.net.ssl.trustStorePassword=changeit"
    )

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w") as f:
        f.write("\n".join(lines + [""] + block) + "\n")
    os.chmod(TARGET, 0o600)
    print(f"gradle.properties proxy block regenerated: {TARGET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
