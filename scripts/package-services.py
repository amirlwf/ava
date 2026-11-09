#!/usr/bin/env python3
"""Package META-INF/services files into the APK (manual, no-Gradle build).

Why this exists: AGP normally merges the META-INF/services/* files from
every dependency jar into the APK. The manual build's dex-input.py only
copies .class files, so the 0.5.0 APK shipped with NO services files at
all — and kotlinx-coroutines discovers its Android Main dispatcher
through ServiceLoader (META-INF/services/kotlinx.coroutines.internal.
MainDispatcherFactory -> AndroidDispatcherFactory). Without that file,
the first touch of Dispatchers.Main throws:

    IllegalStateException: Module with the Main dispatcher is missing.
    Add dependency providing the Android Main dispatcher.

PlayerRepository builds CoroutineScope(SupervisorJob() + Dispatchers.Main)
in AppContainer, i.e. inside Application.onCreate — so 0.5.0 crashed on
launch, before any Activity. This script restores exactly what AGP
would have packaged: every META-INF/services/* file found in the
resolved classpath, duplicates merged (unique lines, first-seen order).

Usage: package-services.py <apk> <cp-file>
"""
import sys
import zipfile

apk_path, cp_file = sys.argv[1], sys.argv[2]
PREFIX = "META-INF/services/"

# name -> ordered unique lines
services: dict[str, list[str]] = {}
order: list[str] = []

with open(cp_file) as f:
    cp = f.read().split(":")

for jar in cp:
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        continue
    for name in z.namelist():
        if not name.startswith(PREFIX) or name.endswith("/"):
            continue
        short = name[len(PREFIX):]
        if short not in services:
            services[short] = []
            order.append(short)
        try:
            text = z.read(name).decode("utf-8", errors="replace")
        except Exception:
            continue
        for line in text.splitlines():
            line = line.strip()
            if line and not line.startswith("#") and line not in services[short]:
                services[short].append(line)

if not services:
    print("package-services: no META-INF/services files found on classpath")
    sys.exit(0)

with zipfile.ZipFile(apk_path, "a", zipfile.ZIP_DEFLATED) as apk:
    existing = set(apk.namelist())
    for short in order:
        entry = PREFIX + short
        if entry in existing:
            print(f"package-services: SKIP (already in APK): {entry}")
            continue
        body = ("\n".join(services[short]) + "\n").encode("utf-8")
        apk.writestr(entry, body)
        print(f"package-services: added {entry} ({len(services[short])} line(s))")

print(f"package-services: {len(order)} service file(s) packaged into {apk_path}")
