#!/usr/bin/env python3
"""Round-3 (fixed): download the REAL AARs for the manual build's resource merge.

The resolver kept only extracted classes.jar files; the .aar files left in
ava-deps/ are KMP *root* artifacts (shell AARs: manifest + license only,
no classes.jar, no res/, no R.txt). The real Android AARs are the
`-android` variants (and the plain AARs like lifecycle-process,
media3-session). This script mines the classpaths (ava-cp.txt +
ava-cp-coil.txt) for (group, artifact, version) triples, downloads
`<artifact>-<version>.aar` for each, and keeps only AARs that actually
contain classes.jar (i.e. real Android libraries). Resources (res/ +
R.txt) come from those.

Output: .build-tools/real-aars/<group>__<artifact>-<version>.aar
Usage: download-aars.py <project-root>
"""
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = sys.argv[1]
TOOLS = os.path.join(ROOT, ".build-tools")
OUT = os.path.join(TOOLS, "real-aars")

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"


def candidates(group, artifact, version):
    path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.aar"
    if group.startswith(("androidx.", "com.android.", "com.google.android.")):
        return [f"{GOOGLE}/{path}", f"{CENTRAL}/{path}"]
    return [f"{CENTRAL}/{path}", f"{GOOGLE}/{path}"]


def fetch(url, dest):
    r = subprocess.run(
        ["curl", "-fsSL", "--retry", "2", "--connect-timeout", "20",
         "-o", dest, url],
        capture_output=True, text=True,
    )
    return r.returncode == 0


def parse_cp(path):
    triples = set()
    if not os.path.exists(path):
        return triples
    for entry in open(path).read().split(":"):
        base = os.path.basename(entry.strip())
        if not base.endswith(".jar"):
            continue
        m = re.match(r"^(?P<group>.+)__(?P<artifact>.+)-(?P<ver>\d[\w.\-]*)\.jar$", base)
        if m:
            triples.add((m.group("group"), m.group("artifact"), m.group("ver")))
    return triples


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    triples = set()
    for cp in ("ava-cp.txt", "ava-cp-coil.txt"):
        triples |= parse_cp(os.path.join(TOOLS, cp))
    print(f"{len(triples)} (group, artifact, version) triples from classpaths")
    kept, shells, failed = [], [], []
    for group, artifact, ver in sorted(triples):
        dest = os.path.join(OUT, f"{group}__{artifact}-{ver}.aar")
        done = False
        for url in candidates(group, artifact, ver):
            if fetch(url, dest):
                done = True
                break
            if os.path.exists(dest):
                os.remove(dest)
        if not done:
            failed.append(f"{group}:{artifact}:{ver} (no AAR)")
            continue
        try:
            with zipfile.ZipFile(dest) as z:
                names = z.namelist()
        except Exception:
            os.remove(dest)
            failed.append(f"{group}:{artifact}:{ver} (bad zip)")
            continue
        if "classes.jar" not in names:
            os.remove(dest)
            shells.append(f"{group}:{artifact}:{ver} (shell, no classes.jar)")
            continue
        has_res = any(n.startswith("res/") for n in names)
        has_rtxt = "R.txt" in names
        print(f"KEEP {group}:{artifact}:{ver} entries={len(names)} res={has_res} R.txt={has_rtxt} size={os.path.getsize(dest)}")
        kept.append((group, artifact, ver, has_res, has_rtxt))
    print(f"\nkept real AARs: {len(kept)} (with res/: {sum(1 for k in kept if k[3])})")
    print(f"shells skipped: {len(shells)}, no AAR: {len(failed)}")
    if failed:
        print("NO-AAR:", *failed, sep="\n  ")


if __name__ == "__main__":
    main()
