#!/usr/bin/env python3
"""Build the d8 input directory for the manual (no-Gradle) build.

The resolved classpath contains 634 duplicated .class files: the same
library shipped as -android, -jvm and KMP-root variants (e.g.
lifecycle-runtime-android 2.11.0 vs lifecycle-runtime 2.6.2, activity
1.13.0 vs activity-ktx 1.7.0). Gradle's conflict resolution would keep one
version; d8 errors on duplicates. So this script extracts every jar into
one directory, newest-version-wins per class file (ties: first wins,
which is the -android variant, since the classpath is ordered app-first).
App classes (from kotlinc) are copied first and are never overwritten.

Non-class entries are skipped: d8 only needs .class files.

Usage: dex-input.py --cp-file <ava-cp.txt> --classes-dir <OUT/classes>
           --out <OUT/dex-input> [--exclude junit,hamcrest]
"""
import argparse
import os
import re
import shutil
import zipfile

ap = argparse.ArgumentParser()
ap.add_argument("--cp-file", required=True)
ap.add_argument("--classes-dir", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--exclude", default="junit,hamcrest")
# d8 (build-tools 34.0.0) rejects a bare directory of .class files
# ("Unsupported source file type"), so also pack the deduped classes
# into a jar for d8 to consume.
ap.add_argument("--jar-out", default="")
args = ap.parse_args()

VER_RE = re.compile(r"^(?P<stem>.*?)-(?P<ver>\d+(?:\.\d+)*)(?:-(?:android|jvm|ktx|java))?\.jar$")


def version_of(path):
    m = VER_RE.match(os.path.basename(path))
    if not m:
        return (0,)
    return tuple(int(x) for x in m.group("ver").split("."))


def pad(v, n):
    return v + (0,) * (n - len(v))


excluded = [e for e in args.exclude.split(",") if e]
# Clean slate: stale files from earlier runs (e.g. module-info.class)
# must not leak into the dex input.
shutil.rmtree(args.out, ignore_errors=True)
os.makedirs(args.out, exist_ok=True)

# 1. App classes first — they always win.
# module-info.class is skipped everywhere: d8 cannot dex it (it NPEs
# in the dex writer on its null SourceFile) and AGP strips it too.
n_app = 0
for root, _dirs, files in os.walk(args.classes_dir):
    for f in files:
        if not f.endswith(".class") or f == "module-info.class":
            continue
        src = os.path.join(root, f)
        rel = os.path.relpath(src, args.classes_dir)
        dst = os.path.join(args.out, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        n_app += 1

# 2. Dependency jars, newest-wins per class.
seen: dict[str, tuple[tuple[int, ...], str]] = {}
n_written = n_dup_skip = n_dup_overwrite = n_jars = 0
cp = open(args.cp_file).read().split(":")
for jar in cp:
    if any(e in os.path.basename(jar) for e in excluded):
        continue
    if not os.path.exists(jar):
        print(f"WARN: missing jar on classpath: {jar}")
        continue
    n_jars += 1
    ver = version_of(jar)
    try:
        z = zipfile.ZipFile(jar)
    except Exception as exc:
        print(f"WARN: unreadable jar {jar}: {exc}")
        continue
    for name in z.namelist():
        if (
            not name.endswith(".class")
            or name.startswith("META-INF/")
            or name.endswith("/module-info.class")
            or name == "module-info.class"
        ):
            continue
        if name in seen:
            old_ver, old_jar = seen[name]
            n = max(len(ver), len(old_ver))
            if pad(ver, n) > pad(old_ver, n):
                with z.open(name) as src, open(os.path.join(args.out, name), "wb") as dst:
                    shutil.copyfileobj(src, dst)
                seen[name] = (ver, jar)
                n_dup_overwrite += 1
            else:
                n_dup_skip += 1
            continue
        dst = os.path.join(args.out, name)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with z.open(name) as src, open(dst, "wb") as fh:
            shutil.copyfileobj(src, fh)
        seen[name] = (ver, jar)
        n_written += 1

print(
    f"dex-input: app_classes={n_app} jars={n_jars} "
    f"lib_classes={n_written} dups_skipped={n_dup_skip} "
    f"dups_overwritten_newer={n_dup_overwrite} total={n_app + n_written}"
)

if args.jar_out:
    with zipfile.ZipFile(args.jar_out, "w", zipfile.ZIP_DEFLATED) as jar:
        for root, _dirs, files in os.walk(args.out):
            for f in sorted(files):
                path = os.path.join(root, f)
                jar.write(path, os.path.relpath(path, args.out))
    print(f"dex-input jar: {args.jar_out} ({os.path.getsize(args.jar_out)} bytes)")
