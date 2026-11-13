#!/usr/bin/env python3
"""Minimal Maven dependency resolver for offline verification of the Ava app.

Fetches POMs from Google Maven / Maven Central, resolves the transitive
compile/runtime closure (parents, properties, dependencyManagement), downloads
each artifact (jar, or classes.jar extracted from aar), and prints a
colon-separated classpath. Nothing here needs Gradle or its daemon, which is
why this exists: the sandbox blocks Gradle's daemon TCP protocol.

Usage:
  python3 scripts/maven-resolve.py --out /tmp/ava-deps --cp-out /tmp/ava-cp.txt
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

# (group, artifact, version) — direct deps from gradle/libs.versions.toml.
# Compose artifact versions come from the BOM (resolved at runtime).
DIRECT = [
    ("androidx.core", "core-ktx", "1.19.1"),
    ("androidx.lifecycle", "lifecycle-runtime-ktx", "2.11.0"),
    ("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.11.0"),
    ("androidx.lifecycle", "lifecycle-runtime-compose", "2.11.0"),
    ("androidx.activity", "activity-compose", "1.13.0"),
    ("androidx.navigation", "navigation-compose", "2.10.2"),
    ("androidx.media3", "media3-exoplayer", "1.11.1"),
    ("androidx.media3", "media3-session", "1.11.1"),
    ("androidx.media3", "media3-common", "1.11.1"),
    # 0.1-fix1 (Track G, ringtone trim): Transformer re-encodes a song
    # segment to AAC/MP4. Transitive: media3-effect, media3-muxer,
    # kotlinx-coroutines-guava (resolved by the BFS below).
    ("androidx.media3", "media3-transformer", "1.11.1"),
    ("androidx.room", "room-runtime", "2.8.5"),
    ("androidx.room", "room-ktx", "2.8.5"),
    ("androidx.datastore", "datastore-preferences", "1.3.0"),
    # Coil 3 is Kotlin Multiplatform: the root artifacts (coil, coil-core,
    # coil-compose) are metadata-only jars (Kotlin/Native .knm linkdata, no
    # .class files). The Android app needs the -android platform variants.
    ("io.coil-kt.coil3", "coil-compose-android", "3.6.3"),
    ("io.coil-kt.coil3", "coil-android", "3.6.3"),
    # Round-5 (0.5.5): androidx.collection:collection-jvm pinned to 1.5.0.
    # compose-ui 1.12.1's android POM declares collection-jvm 1.5.0, but the
    # resolver's first-seen-wins BFS pinned it to 1.4.5 (from an earlier
    # transitive edge) — and 1.4.5 has no OrderedScatterSet* (added in
    # 1.5.0), so the app died with NoClassDefFoundError at the first
    # SubcomposeLayout. DIRECT entries are processed first, so this wins
    # the race. (The KMP root artifact `collection` itself resolves to a
    # shell AAR with no classes.jar — see download_artifact — which is
    # why the -jvm variant must be pinned explicitly.)
    ("androidx.collection", "collection-jvm", "1.5.0"),
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
    ("junit", "junit", "4.13.2"),
]

COMPOSE_FROM_BOM = [
    ("androidx.compose.ui", "ui"),
    ("androidx.compose.ui", "ui-graphics"),
    ("androidx.compose.ui", "ui-tooling-preview"),
    ("androidx.compose.material3", "material3"),
    ("androidx.compose.material", "material-icons-core"),
    ("androidx.compose.material", "material-icons-extended"),
]

BOM = ("androidx.compose", "compose-bom", "2026.09.00")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "ava-resolver/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def artifact_url(repo: str, g: str, a: str, v: str, ext: str) -> str:
    return f"{repo}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"


def get_pom(g: str, a: str, v: str) -> ET.Element | None:
    for repo in (GOOGLE, CENTRAL):
        try:
            data = fetch(artifact_url(repo, g, a, v, "pom"))
            return ET.fromstring(data)
        except Exception:
            continue
    print(f"WARN: no POM for {g}:{a}:{v}", file=sys.stderr)
    return None


def text(el: ET.Element | None) -> str:
    return (el.text or "").strip() if el is not None else ""


def norm_version(v: str) -> str:
    """Maven version ranges: '[2.11.0]' means exactly 2.11.0; '[a,b)' means >= a.
    Take the lower bound (ranges in AndroidX POMs are pinned exact ranges)."""
    v = v.strip()
    m = re.match(r"^[\[(]\s*([^,\]\)]+)", v)
    return m.group(1).strip() if m else v


def substitute(value: str, props: dict[str, str]) -> str:
    def repl(m: re.Match) -> str:
        return props.get(m.group(1), m.group(0))

    for _ in range(5):
        new = re.sub(r"\$\{([^}]+)\}", repl, value)
        if new == value:
            break
        value = new
    return value


def collect_pom(g: str, a: str, v: str, depth: int = 0):
    """Returns (properties, dependencyManagement, dependencies) merged with parents."""
    if depth > 8:
        return {}, {}, []
    pom = get_pom(g, a, v)
    if pom is None:
        return {}, {}, []
    props: dict[str, str] = {"project.version": v, "project.groupId": g, "project.artifactId": a}
    for p in pom.findall("m:properties/*", NS):
        props[p.tag.split("}", 1)[-1]] = text(p)
    parent = pom.find("m:parent", NS)
    parent_props, parent_mgmt, parent_deps = {}, {}, []
    if parent is not None:
        pg, pa, pv = (text(parent.find(f"m:{k}", NS)) for k in ("groupId", "artifactId", "version"))
        parent_props, parent_mgmt, parent_deps = collect_pom(pg, pa, pv, depth + 1)
    props = {**parent_props, **props}
    mgmt: dict[tuple[str, str], str] = dict(parent_mgmt)
    dm = pom.find("m:dependencyManagement/m:dependencies", NS)
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            dg, da = text(d.find("m:groupId", NS)), text(d.find("m:artifactId", NS))
            dv = text(d.find("m:version", NS))
            if dg and da and dv:
                mgmt[(dg, da)] = substitute(dv, props)
    deps = list(parent_deps)
    ds = pom.find("m:dependencies", NS)
    if ds is not None:
        for d in ds.findall("m:dependency", NS):
            scope = text(d.find("m:scope", NS)) or "compile"
            optional = text(d.find("m:optional", NS)) == "true"
            dg, da = text(d.find("m:groupId", NS)), text(d.find("m:artifactId", NS))
            dv = text(d.find("m:version", NS))
            if scope in ("test", "provided", "system") or optional:
                continue
            deps.append((dg, da, dv, scope))
    return props, mgmt, deps


def resolve(direct):
    """BFS over the dependency graph; returns {(g,a): version} + packaging map."""
    resolved: dict[tuple[str, str], str] = {}
    queue: list[tuple[str, str, str]] = list(direct)
    seen: set[tuple[str, str, str]] = set()
    while queue:
        g, a, v = queue.pop(0)
        if (g, a, v) in seen:
            continue
        seen.add((g, a, v))
        if (g, a) in resolved and resolved[(g, a)] != v:
            # first-seen version wins (nearest to root, like Maven)
            continue
        resolved[(g, a)] = v
        props, mgmt, deps = collect_pom(g, a, v)
        for dg, da, dv, _scope in deps:
            if not dg or not da:
                continue
            dv = substitute(dv, props) if dv else mgmt.get((dg, da), "")
            if not dv:
                print(f"WARN: no version for {dg}:{da} (from {g}:{a})", file=sys.stderr)
                continue
            queue.append((dg, da, norm_version(dv)))
    return resolved


def download_artifact(outdir: str, g: str, a: str, v: str) -> str | None:
    """Downloads jar (or aar -> extracted classes.jar). Returns jar path or None.

    Round-5: an AAR may also carry jars under libs/ (AGP puts those on the
    app's classpath too). emoji2 1.2.0 hides its flatbuffer classes
    (MetadataItem/MetadataList) in libs/repackaged.jar — classes.jar alone
    does not contain them, so they were missing from the dex. Merge every
    libs/*.jar into the extracted jar (classes.jar wins on collision).
    NOTE: jars downloaded before this fix are cached by name and are NOT
    re-extracted — delete a stale jar to force re-extraction.
    """
    dest = os.path.join(outdir, f"{g}__{a}-{v}")
    if os.path.exists(dest + ".jar"):
        return dest + ".jar"
    for ext in ("aar", "jar"):
        for repo in (GOOGLE, CENTRAL):
            url = artifact_url(repo, g, a, v, ext)
            try:
                data = fetch(url)
            except Exception:
                continue
            path = dest + "." + ext
            with open(path, "wb") as f:
                f.write(data)
            if ext == "aar":
                jar = dest + ".jar"
                with zipfile.ZipFile(path) as z:
                    names = z.namelist()
                    if "classes.jar" not in names:
                        return None
                    written: set[str] = set()
                    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as out:
                        with z.open("classes.jar") as src:
                            with zipfile.ZipFile(src) as cj:
                                for n in cj.namelist():
                                    # .class files plus Kotlin module files:
                                    # kotlinc discovers a package's top-level
                                    # functions (e.g. coil3.compose.AsyncImage)
                                    # from META-INF/*.kotlin_module — dropping
                                    # it makes those symbols unresolvable even
                                    # though the .class files are present.
                                    if n.endswith(".class") or n.endswith(".kotlin_module"):
                                        out.writestr(n, cj.read(n))
                                        written.add(n)
                        for lib in sorted(n for n in names
                                          if n.startswith("libs/")
                                          and n.endswith(".jar")):
                            with z.open(lib) as src:
                                with zipfile.ZipFile(src) as lj:
                                    for n in lj.namelist():
                                        if n.endswith(".class") and n not in written:
                                            out.writestr(n, lj.read(n))
                                            written.add(n)
                os.remove(path)
                return jar
            return path
    print(f"WARN: could not download {g}:{a}:{v}", file=sys.stderr)
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--cp-out", required=True)
    # Extra direct deps as g:a:v triples, comma-separated — e.g. to resolve
    # the KSP processor classpath (room-compiler) into a separate dir.
    # NOTE: when --extra is given, the BOM/Compose resolution is skipped
    # and ONLY the extra deps (+ their transitive closure) are resolved.
    ap.add_argument("--extra", default="")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    if args.extra:
        direct = []
        for triple in args.extra.split(","):
            g, a, v = triple.split(":")
            direct.append((g, a, v))
        resolved = resolve(direct)
        print(f"resolved {len(resolved)} artifacts")
        cp: list[str] = []
        for (g, a), v in sorted(resolved.items()):
            jar = download_artifact(args.out, g, a, v)
            if jar:
                cp.append(jar)
        with open(args.cp_out, "w") as f:
            f.write(":".join(cp))
        print(f"classpath entries: {len(cp)} -> {args.cp_out}")
        return 0

    # Resolve Compose versions from the BOM first.
    _, bom_mgmt, _ = collect_pom(*BOM)
    direct = list(DIRECT)
    for g, a in COMPOSE_FROM_BOM:
        v = bom_mgmt.get((g, a))
        if not v:
            print(f"WARN: {g}:{a} not in BOM {BOM}", file=sys.stderr)
            continue
        print(f"BOM: {g}:{a}:{v}")
        direct.append((g, a, v))

    resolved = resolve(direct)
    print(f"resolved {len(resolved)} artifacts")
    cp: list[str] = []
    for (g, a), v in sorted(resolved.items()):
        jar = download_artifact(args.out, g, a, v)
        if jar:
            cp.append(jar)
    with open(args.cp_out, "w") as f:
        f.write(":".join(cp))
    print(f"classpath entries: {len(cp)} -> {args.cp_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
