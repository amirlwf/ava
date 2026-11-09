#!/usr/bin/env python3
"""Merge library resources (from AARs) into the manual (no-Gradle) build.

WHY THIS EXISTS — the 0.5.0/0.5.1/0.5.2 launch-crash root cause:
`aapt2 compile/link` in manual-build.sh only ever saw the app's own
`app/src/main/res`. Library resources were never compiled and library R
classes were never generated, because AARs ship no R classes (only
R.txt) and the resolver extracts only classes.jar from AARs. At the
first `ComponentActivity.setContent` the Compose/lifecycle code reads
fields of `androidx.lifecycle.runtime.R$id`, `androidx.compose.ui.R$id`
etc. — classes that did not exist in the dex — and the app died with
NoClassDefFoundError before the first frame. Verified firsthand:
`aapt2 dump resources` on the 0.5.2 APK shows zero material3/androidx
resources, and `javap -c` on the library classes shows the R reads.

What this script does (the part AGP does for free):
1. Picks the dedup winner per (group, artifact) from the resolved
   classpaths, with the same newest-version-wins rule as dex-input.py
   (ties: first on the classpath wins).
2. For each winner AAR that has a res/ directory: extracts it, runs
   `aapt2 compile --dir` on it, and reads the library package name
   from the AAR's (text) AndroidManifest.xml. Libraries that share a
   package (ktx/base pairs, KMP-root vs -android variants) are ALL
   merged under that one package — aapt2 generates one R.java per
   package, exactly as AGP does.
3. Runs `aapt2 link` with the app's compiled res zip LAST (so the app
   overlays libraries, as in AGP) plus every library res zip, with
   `--auto-add-overlay` and `--extra-packages <pkg1:pkg2:...>` so aapt2
   generates an R.java for every library package. The generated R.java
   files are then fed to kotlinc together with the app sources.
   Empirically (verified 2026-11-09): aapt2 generates the SAME full
   resource table in EVERY package's R.java (each is a complete copy
   with a different package line), so every library R class contains
   every resource with its final id — there is no private-symbol gap.

Duplicate resources (AGP's mergeResources step, reproduced here):
aapt2 link errors when the same resource is defined in two .flat
files, even when the values are byte-identical (verified: media-compat
1.7.0 vs media3-session 1.11.1 define 9 identical notification
resources). AGP's merger dedupes them (first in dependency order
wins); this script reproduces that with a retry loop: on a
"conflicting value" link error it removes the duplicate definition
from the LOSER library's values XML (never from the app) and
recompiles that library. `aapt2 compile` does NOT resolve
cross-references (verified: a stripped media-compat res compiles
cleanly even though its layouts still reference the stripped
colors/styles) — references resolve at link time against the
winning library's definitions, so stripping never cascades.
If the conflicting file is not a values XML (layout, drawable...),
the whole file is dropped from the loser instead (the winner's copy
of the same-named file survives in the merged table).

Usage:
  merge-resources.py --cp-files <ava-cp.txt,ava-cp-coil.txt>
      --aars-dir <real-aars> --app-res-zip <res.zip>
      --manifest <AndroidManifest.xml> [--assets-dir <dir>]
      --out-dir <OUT/aapt2> --apk-out <unsigned.apk> --r-out <OUT/r>
      --aapt2 <path> --android-jar <path>
      --version-code N --version-name X [--min-sdk 26] [--target-sdk 34]
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

VER_RE = re.compile(
    r"^(?P<stem>.*?)-(?P<ver>\d+(?:\.\d+)*)(?:-(?:android|jvm|ktx|java))?\.jar$"
)


def version_of(name):
    m = VER_RE.match(name)
    if not m:
        return (0,)
    return tuple(int(x) for x in m.group("ver").split("."))


def pad(v, n):
    return v + (0,) * (n - len(v))


def axml_manifest_package(data):
    """Return the `package` attribute of an AAR's AndroidManifest.xml.

    AAR manifests are plain TEXT XML (only the final APK's manifest is
    binary AXML) — parse with ElementTree.
    """
    text = data.decode("utf-8", "replace")
    root = ET.fromstring(text)
    if root.tag != "manifest":
        raise ValueError(f"root element is <{root.tag}>, not <manifest>")
    package = root.get("package")
    if not package:
        raise ValueError("no package attribute on <manifest>")
    return package


def compile_lib(aapt2, stage, out_zip, label):
    """aapt2 compile --dir <stage> -o <out_zip>; returns (ok, output)."""
    r = subprocess.run(
        [aapt2, "compile", "--dir", stage, "-o", out_zip],
        capture_output=True, text=True,
    )
    if r.returncode != 0:
        print(
            f"WARN: aapt2 compile failed for {label}:\n"
            f"{(r.stdout or '')}{(r.stderr or '')}"
        )
        return False
    return True


# aapt2 link duplicate-resource errors look like:
#   <file>:<line>: error: resource 'color/foo' has a conflicting value for configuration ().
#   <file>:<line>: note: originally defined here.
CONFLICT_RE = re.compile(
    r"^(?P<file>.+?):\d+: error: resource "
    r"'(?P<type>[^/]+)/(?P<name>[^']+)' has a conflicting value"
)
NOTE_RE = re.compile(r"^(?P<file>.+?):\d+: note: originally defined here")


def strip_resource_from_file(path, rtype, rname):
    """Remove one resource definition from a values XML file.

    Handles both <type name="..."> and <item type="..." name="..."/>.
    Returns True if an element was removed.
    """
    tree = ET.parse(path)
    root = tree.getroot()
    removed = False
    for el in list(root):
        if el.tag == rtype and el.get("name") == rname:
            root.remove(el)
            removed = True
        elif (el.tag == "item" and el.get("type") == rtype
                and el.get("name") == rname):
            root.remove(el)
            removed = True
    if removed:
        tree.write(path, encoding="utf-8", xml_declaration=True)
    return removed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cp-files", required=True,
                    help="comma-separated classpath files (ava-cp.txt,ava-cp-coil.txt)")
    ap.add_argument("--aars-dir", required=True)
    ap.add_argument("--app-res-zip", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--assets-dir", default="")
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--apk-out", required=True)
    ap.add_argument("--r-out", required=True)
    ap.add_argument("--aapt2", required=True)
    ap.add_argument("--android-jar", required=True)
    ap.add_argument("--version-code", required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--min-sdk", default="26")
    ap.add_argument("--target-sdk", default="34")
    args = ap.parse_args()

    # 1. Dedup winners per stem (newest version wins, ties: first seen).
    winners = {}  # stem -> (version_tuple, version_str)
    for cp_file in args.cp_files.split(","):
        for jar in open(cp_file).read().split(":"):
            name = os.path.basename(jar)
            m = VER_RE.match(name)
            if not m:
                continue
            stem, ver_str = m.group("stem"), m.group("ver")
            ver = tuple(int(x) for x in ver_str.split("."))
            old = winners.get(stem)
            if old is None:
                winners[stem] = (ver, ver_str)
            else:
                n = max(len(ver), len(old[0]))
                if pad(ver, n) > pad(old[0], n):
                    winners[stem] = (ver, ver_str)

    lib_res_dir = os.path.join(args.out_dir, "lib-res")
    aar_res_stage = os.path.join(args.out_dir, "aar-res")
    shutil.rmtree(lib_res_dir, ignore_errors=True)
    shutil.rmtree(aar_res_stage, ignore_errors=True)
    os.makedirs(lib_res_dir, exist_ok=True)
    os.makedirs(aar_res_stage, exist_ok=True)
    if os.path.exists(args.r_out):
        shutil.rmtree(args.r_out)
    os.makedirs(args.r_out, exist_ok=True)

    # lib_id -> dict(stage, zip, package, stem, ver_str)
    libs = {}
    packages = {}  # package -> [lib_id, ...] (shared packages merge)
    n_no_aar = n_no_res = n_compiled = 0
    for stem in sorted(winners):
        ver, ver_str = winners[stem]
        aar_name = f"{stem}-{ver_str}.aar"
        aar_path = os.path.join(args.aars_dir, aar_name)
        if not os.path.exists(aar_path):
            # Jar-only library (kotlin-stdlib, coroutines-core, guava,
            # ...) — no AAR, no resources. Skip quietly.
            n_no_aar += 1
            continue
        try:
            z = zipfile.ZipFile(aar_path)
        except Exception as exc:
            print(f"WARN: unreadable AAR {aar_name}: {exc}")
            continue
        names = z.namelist()
        if "AndroidManifest.xml" not in names:
            print(f"WARN: {aar_name} has no AndroidManifest.xml, skipped")
            continue
        try:
            package = axml_manifest_package(z.read("AndroidManifest.xml"))
        except Exception as exc:
            print(f"WARN: {aar_name}: cannot parse manifest: {exc}")
            continue
        if not package:
            print(f"WARN: {aar_name}: no package attribute, skipped")
            continue
        res_entries = [n for n in names if n.startswith("res/") and not n.endswith("/")]
        if not res_entries:
            n_no_res += 1
            continue
        lib_id = f"{stem}-{ver_str}"
        stage = os.path.join(aar_res_stage, lib_id)
        for entry in res_entries:
            rel = entry[len("res/"):]
            dst = os.path.join(stage, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with z.open(entry) as src, open(dst, "wb") as fh:
                shutil.copyfileobj(src, fh)
        out_zip = os.path.join(lib_res_dir, f"{lib_id}.zip")
        if not compile_lib(args.aapt2, stage, out_zip, aar_name):
            print(f"WARN: {aar_name} resources skipped")
            continue
        libs[lib_id] = {
            "stage": stage, "zip": out_zip, "package": package,
            "stem": stem, "ver_str": ver_str,
        }
        packages.setdefault(package, []).append(lib_id)
        n_compiled += 1

    print(
        f"merge-resources: winners={len(winners)} jar-only(no AAR)={n_no_aar} "
        f"AARs without res/={n_no_res} libraries compiled={n_compiled} "
        f"packages={len(packages)}"
    )
    for pkg in sorted(packages):
        if len(packages[pkg]) > 1:
            print(f"  shared package {pkg}: {', '.join(packages[pkg])}")

    def stage_of_file(path):
        """Map a staging file path back to its lib_id (or None)."""
        apath = os.path.abspath(path)
        for lib_id, info in libs.items():
            if apath.startswith(os.path.abspath(info["stage"]) + os.sep):
                return lib_id
        return None

    def is_app_file(path):
        # The app's res is compiled from app/src/main/res; aapt2 reports
        # source paths from that tree. Never strip from the app.
        return os.path.abspath(path).endswith(".xml") and (
            "/app/src/main/res/" in os.path.abspath(path).replace(os.sep, "/")
            or os.path.abspath(path).startswith(
                os.path.abspath("app/src/main/res"))
        )

    stripped = []  # (lib_id, type, name, winner_file) for the report
    lib_zips = [libs[i]["zip"] for i in sorted(libs)]
    extra = ":".join(sorted(packages))

    # 2. Link with a duplicate-resolution retry loop.
    for attempt in range(1, 7):
        cmd = [
            args.aapt2, "link",
            "-o", args.apk_out,
            "-I", args.android_jar,
            "--manifest", args.manifest,
            "--java", args.r_out,
            "--auto-add-overlay",
            "--extra-packages", extra,
            "--version-code", args.version_code,
            "--version-name", args.version_name,
            "--min-sdk-version", args.min_sdk,
            "--target-sdk-version", args.target_sdk,
        ]
        if args.assets_dir and os.path.isdir(args.assets_dir):
            cmd += ["-A", args.assets_dir]
        cmd += lib_zips + [args.app_res_zip]
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode == 0:
            break
        output = (r.stdout or "") + (r.stderr or "")
        # Parse (conflict, winner) pairs.
        conflicts = []
        pending = None
        for line in output.splitlines():
            m = CONFLICT_RE.match(line)
            if m:
                pending = (m.group("file"), m.group("type"), m.group("name"))
                continue
            n = NOTE_RE.match(line)
            if n and pending is not None:
                conflicts.append((pending[0], pending[1], pending[2], n.group("file")))
                pending = None
        if not conflicts:
            print(f"ERROR: aapt2 link failed (attempt {attempt}) with no "
                  "parseable duplicate-resource errors:", file=sys.stderr)
            print("  cmd:", " ".join(cmd), file=sys.stderr)
            print(output, file=sys.stderr)
            sys.exit(1)
        print(f"merge-resources: link attempt {attempt}: {len(conflicts)} "
              "duplicate resource(s), resolving:")
        recompiled = set()
        for loser_file, rtype, rname, winner_file in conflicts:
            # The app always wins: if aapt2 blames an app file, strip
            # the library file instead.
            if is_app_file(loser_file):
                loser_file, winner_file = winner_file, loser_file
            lib_id = stage_of_file(loser_file)
            if lib_id is None:
                print(f"ERROR: conflicting file {loser_file} is not a "
                      "library staging file and not the app; cannot "
                      "resolve. Winner: " + winner_file, file=sys.stderr)
                print(output, file=sys.stderr)
                sys.exit(1)
            rel = os.path.relpath(loser_file, libs[lib_id]["stage"])
            if rel.startswith("values" + os.sep) and loser_file.endswith(".xml"):
                if strip_resource_from_file(loser_file, rtype, rname):
                    print(f"  stripped {rtype}/{rname} from "
                          f"{lib_id}/{rel} (winner: {winner_file})")
                    stripped.append((lib_id, rtype, rname, winner_file))
                else:
                    print(f"ERROR: {rtype}/{rname} not found in "
                          f"{loser_file}; cannot strip", file=sys.stderr)
                    sys.exit(1)
            else:
                # File-based resource (layout, drawable, ...): drop the
                # whole file from the loser; the winner's same-named
                # file survives in the merged table.
                os.remove(loser_file)
                print(f"  dropped file {lib_id}/{rel} "
                      f"(duplicate {rtype}/{rname}; winner: {winner_file})")
                stripped.append((lib_id, rtype, rname, winner_file))
            if lib_id not in recompiled:
                recompiled.add(lib_id)
        for lib_id in sorted(recompiled):
            info = libs[lib_id]
            if not compile_lib(args.aapt2, info["stage"], info["zip"], lib_id):
                print(f"ERROR: recompile failed for {lib_id} after "
                      "stripping duplicates", file=sys.stderr)
                sys.exit(1)
    else:
        print("ERROR: aapt2 link still failing after 6 attempts",
              file=sys.stderr)
        sys.exit(1)

    r_files = []
    for root, _dirs, files in os.walk(args.r_out):
        for f in files:
            if f == "R.java":
                r_files.append(os.path.join(root, f))
    print(f"merge-resources: linked {args.apk_out} "
          f"(attempt {attempt})")
    if stripped:
        print(f"merge-resources: stripped {len(stripped)} duplicate "
              "definition(s) (AGP mergeResources equivalent):")
        for lib_id, rtype, rname, winner in stripped:
            print(f"  {lib_id}: {rtype}/{rname}")
    print(f"merge-resources: R.java files generated: {len(r_files)}")
    for f in sorted(r_files):
        rel = os.path.relpath(f, args.r_out)
        pkg = rel.rsplit(os.sep, 1)[0].replace(os.sep, ".")
        print(f"  R.java: {pkg}")
    print(f"merge-resources: extra-packages: {extra}")
    # Machine-readable list for the build script.
    with open(os.path.join(args.out_dir, "r-java-files.txt"), "w") as fh:
        fh.write("\n".join(sorted(r_files)) + "\n")


if __name__ == "__main__":
    main()
