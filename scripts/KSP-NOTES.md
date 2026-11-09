# KSP standalone (no Gradle) — research notes

## Headline: KSP 2.3.13 has no `kotlinc -Xplugin` path

The old KSP1 compiler-plugin jar `com.google.devtools.ksp:symbol-processing:2.3.13`
does **not** exist as a jar: its POM downloads (HTTP 200, `<packaging>pom</packaging>`)
but the jar URL is HTTP 404. The `-P plugin:com.google.devtools.ksp.symbol-processing:apclasspath`
options belong to that dead KSP1 mode. KSP 2.x (KSP2, Analysis-API based, default
since 2.0.0; KSP1 is deprecated and incompatible with Kotlin ≥ 2.3.0 and AGP ≥ 9.0)
runs standalone as a JVM app. Official docs:
https://kotlinlang.org/docs/ksp-command-line.html

> `java -cp <classpath> <mainclass> <options> <processor>`

## Versions (verified 2026-11-09 by direct HTTP-200 downloads, not maven-metadata)

- KSP **2.3.13** exists on Maven Central (new flat versioning, no `<kotlin>-<ksp>` suffix).
  `symbol-processing-gradle-plugin:2.3.13` POM depends on `symbol-processing-api:2.3.13`
  + `symbol-processing-common-deps:2.3.13`.
- `symbol-processing-cmdline:2.3.13` does **not** exist (jar + pom both 404).
- `symbol-processing-aa-embeddable:2.3.13` **exists** — fat jar (~81 MB, 40,281 entries:
  bundles kotlin-compiler-embeddable + kotlinx-coroutines), contains
  `com/google/devtools/ksp/cmdline/KSPJvmMain.class`. This is the recommended
  single KSP runtime jar. (Plain `symbol-processing-aa` is the same without coroutines.)
- `androidx.room:room-compiler:2.8.5` (Google Maven) declares
  `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
  → `androidx.room.RoomKspProcessor$Provider`.

## KSP runtime jars (in `.build-tools/ksp-research/`)

| Artifact | Notes |
|---|---|
| `symbol-processing-aa-embeddable-2.3.13.jar` | fat runtime, entry point `com.google.devtools.ksp.cmdline.KSPJvmMain` |
| `symbol-processing-api-2.3.13.jar` | `SymbolProcessorProvider` |
| `symbol-processing-common-deps-2.3.13.jar` | common config / incremental classes |
| `kotlin-stdlib-2.4.21.jar` | from the official doc's dependency list |

Verified live: `java -cp <those four> com.google.devtools.ksp.cmdline.KSPJvmMain -h`
exits 0 and lists the required options (no `apclasspath`, no `ksp.*` plugin options).

## KSP invocation (Step A — generates sources only, does NOT compile them)

```bash
java -cp "$KSP_CP" "$KSP_MAIN" \
  -language-version=2.3 -api-version=2.3 -jvm-target=17 -module-name=app \
  -source-roots=<PROJECT>/app/src/main/java \
  -project-base-dir=<PROJECT> \
  -output-base-dir=<PROJECT>/.build-out/ksp \
  -caches-dir=<PROJECT>/.build-out/ksp/caches \
  -class-output-dir=<PROJECT>/.build-out/ksp/classes \
  -kotlin-output-dir=<PROJECT>/.build-out/ksp/generated/kotlin \
  -java-output-dir=<PROJECT>/.build-out/ksp/generated/java \
  -resource-output-dir=<PROJECT>/.build-out/ksp/generated/resources \
  -jdk-home=<JAVA_HOME> \
  -libraries=<APP_COMPILE_CP>:<ANDROID_JAR> \
  "<ROOM_PROCESSOR_CP>"
```

- The processor is the final positional argument (colon-separated classpath).
- `-libraries` is the full module compile classpath + `android.jar`.
- Omit `-incremental` on a clean one-shot build.
- No `-processor-options` needed: the project's `@Database` has
  `exportSchema = false`, so no `room.schemaLocation` is required; there are
  no `@TypeConverter`s anywhere in the app.
- Room generates `_Impl` code as **Java** (JavaPoet) — expect output mainly
  under `-java-output-dir`.

## Room processor classpath

`room-compiler:2.8.5` + its runtime deps (from its POM): `room-common:2.8.5`,
`room-compiler-processing:2.8.5`, `room-external-antlr:2.8.5`, `auto-common:1.2.1`,
`kotlinpoet-jvm:2.0.0`, `kotlinpoet-javapoet:2.0.0`, `javapoet:1.13.0`,
`sqlite-jdbc:3.41.2.2`, `commons-codec:1.15`, `auto-value-annotations:1.6.3`,
`annotations:12.0` (+ kotlin-stdlib). Resolved with:

```bash
python3 scripts/maven-resolve.py --extra androidx.room:room-compiler:2.8.5 \
    --out .build-tools/ksp/processor-deps --cp-out .build-tools/ksp/processor-cp.txt
```

## Step B — compile the generated sources

KSP only generates sources; a second kotlinc invocation compiles them as if
hand-written (main sources + `R.java` + everything under the KSP output dirs),
with the Compose compiler plugin as usual.

## Caveats

- Room 2.8.5's POM lists KSP API `2.0.10-1.0.24` as a compile dep; running it
  under KSP 2.3.13 is exactly what the pinned Gradle plugin resolves to, but
  it is untested in cmdline mode — the first real Step A run is the proof.
- The embedded Kotlin compiler version inside the `-aa-embeddable` fat jar
  could not be pinned (no `pom.properties`, empty manifest). If Step A errors
  about language version, that is the first thing to check.
- Doc version skew: the official doc example names 2.3.10 jars with stdlib
  2.4.20 / coroutines 1.10.2, while its dependency list says stdlib 2.4.21 /
  coroutines 1.11.0; POMs say stdlib 2.3.20 / coroutines 1.10.2. The 2.4.21
  stdlib path was verified; any of these should work.
