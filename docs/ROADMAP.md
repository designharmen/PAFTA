# Roadmap

Each phase ends with a commit. A phase is only "done" when its build result has
been stated honestly — including when the result is a failure.

## Phase 0 — foundation ✅ (this commit)

Multi-module Gradle project, the Harmen Design system in Compose, and the
pure-Kotlin core with real tests.

**Verified in the development container** — `./gradlew -PpaftaCoreOnly=true test`,
109 tests, 0 failures:

- `core:geometry` (37) — `Vec2`/`Vec3`, `Aabb`, segment intersection, polygon
  area/centroid/containment, arc and circle tessellation, and `Viewport2D`
  (fit, pan, zoom-about-pivot, screen↔model round trip).
- `core:units` (18) — mm/cm/m/inch/foot conversion, `5500mm`-style formatting,
  feet-and-inches with fractions, and a parser that accepts `5500`, `5.5 m`,
  `5,5 m`, `1' 6 1/2"` and returns `null` rather than guessing on bad input.
- `core:measure` (18) — distance / polyline / angle / area measurements, the
  pick state machine, and endpoint/midpoint/perpendicular/grid snapping.
- `core:dxf` (19) — DXF R12 reader and writer, including a full write→read
  round trip, classic `POLYLINE`+`VERTEX` collection, `MTEXT` fragment joining,
  and graceful handling of entity types the reader does not model.
- `core:project` (17) — the `.pafta` container: round trip of all seven
  annotation kinds, byte-exact payload preservation, atomic save, and clear
  errors for a non-PAFTA zip, a missing payload, corrupt JSON, and a
  future-format file.

**Not verified** — the `:app` module has not been compiled, because this
container has no Android SDK and cannot install one (see
[PHASE0-ENVIRONMENT.md](PHASE0-ENVIRONMENT.md)). It needs its first real build
on a machine with the SDK.

### Phase 0 defects found and fixed

Recorded because the process matters more than the clean result:

| Found by | Defect | Fix |
| --- | --- | --- |
| `feet and inches notation` | `formatFeetInches` dropped a zero inch column when feet were present, printing `18' 1/2"` instead of the CAD-correct `18' 0 1/2"` | the short form now applies only when there is no feet column |
| `values that round to zero…` | the `-0` guard ran *before* rounding, so `-1e-7` formatted as `-0mm` | normalise the sign after formatting |
| `core:dxf` compile | `DxfWriter.write` was `inline` but used local functions | removed `inline` |
| `core:project` compile | missing `kotlinx.serialization.encodeToString` import made the overload resolve wrongly | added the import |
| two of my own test expectations | bad arithmetic (`14500` for a 15500mm polyline) and bad reasoning about which axis binds in `fit` | corrected the tests, with the reasoning written into the comment |

## Phase 1 — file manager and real import ✅

Import through the system document picker, a project library, the viewer wired
to imported drawings, auto-save, and undo/redo.

**Verified in the development container** — `./gradlew -PpaftaCoreOnly=true test`,
**167 tests, 0 failures** (was 109 after Phase 0). The new logic went into
`core:project` precisely so it could be tested here rather than eyeballed:

- **`ProjectStore`** (24 tests) — import, list, open, save, rename, delete, all
  over `java.io.File` so the same code and tests cover Android and the JVM:
  name collisions get `-2` / `-3` suffixes instead of overwriting; an unknown
  extension, an empty file, an oversized file and an unparseable DXF are each
  refused with a message naming the actual problem; one corrupt project does not
  make the library unlistable; a project name containing `../` cannot escape the
  library directory.
- **`FileFormat`** (5 tests) — extension routing for 17 formats, with `readable`
  stating honestly which ones have a viewer today. A format PAFTA cannot draw yet
  still imports, so the user's file is safely inside a container rather than
  rejected.
- **`UndoStack`** (8 tests) — bounded snapshot history: the oldest step drops at
  the limit, and editing after an undo discards the abandoned redo branch.
- **`AutoSavePolicy`** (9 tests) — coalescing save timing: wait for a 2s quiet
  period, but never let 30s pass with unsaved work, and always save on exit.
  Sustained editing hits the ceiling; a drag of the opacity track writes the
  container once rather than forty times.
- **`DrawingDocument` + `mergeLayers`** (12 tests) — opening a payload as a
  drawing, and reconciling the file's layers with the user's saved visibility and
  opacity: the file decides which layers exist, the project decides how they
  look. Layers referenced only by entities (common in files from other tools)
  still appear; saved state for a layer no longer in the file is dropped.

**Not verified** — still no Android SDK in this container, so `:app` remains
uncompiled. The Android side of Phase 1 is: `ProjectRepository` (SAF content URI
→ bytes, `Context` → library directory), `LibraryScreen`, `LibraryViewModel`, the
rewritten `EditorViewModel`, navigation in `MainActivity`, and undo/redo plus a
dirty indicator in the top bar.

### Phase 1 decisions

- **No Room yet.** The roadmap called for a Room-backed project list; the files
  on disk are the source of truth and `readManifest` already supplies the
  metadata, so a database here would only be a cache that can disagree with the
  filesystem — and when it does, the user loses work or sees projects that are
  not there. Room arrives when there is data that *cannot* be derived from the
  files: recent-open order, per-project UI state, a search index.
- **Projects live in `filesDir/projects`**, not shared storage: no runtime
  permission, no scoped-storage special cases, and the library cannot become
  half-readable because a URI grant lapsed.
- **The picker accepts `*/*`.** CAD formats largely have no registered MIME type,
  so filtering by MIME would hide the user's own drawings; the extension decides
  whether the import is allowed, and a refusal says why.
- **DXF `TEXT` entities are now drawn**, sized from their model height, so an
  imported drawing shows its own annotation instead of silently dropping it.

### Phase 1 defects found while writing it

| Defect | Fix |
| --- | --- |
| `scheduleAutoSave` cancelled the coroutine job it was itself running inside, then relaunched — correct only by accident | the wait is a loop, so the job is only ever cancelled from outside itself |
| the `Grid` tool selected itself and changed nothing | selecting it toggles grid visibility |
| `DxfEntity.Text` was parsed but never rendered | a dedicated text pass, scaled from the entity's model height |

**Exit criterion, still open:** import a real DXF on a device and see it drawn.
That needs the first `assembleDebug`.

## Turkish interface retrofit ✅

Applied across Phases 0 and 1 after the brief added a Turkish-only requirement.
The glossary is in [TURKCE-SOZLUK.md](TURKCE-SOZLUK.md).

The mechanical change is the interesting part. `StoreFailure` used to carry an
English sentence in a `message` property, which the UI showed verbatim — an
English sentence one step from a Turkish screen. It now carries only structured
data (`UnknownFormat(extension)`, `TooLarge(sizeBytes, limitBytes)`,
`Unreadable(format, reason)`, `Io(cause, diagnostic)`), and a single composable
boundary (`ui/UiText.kt`) turns that data into Turkish from `strings.xml`. The
platform's own exception text is kept as `diagnostic` for logs and never shown,
because its language follows the OS rather than the app.

Enum labels became `@StringRes` ids for the same reason: a label cannot be an
English literal if its type is an integer resource id. 115 strings now live in
`strings.xml`, and the date format is pinned to Turkish rather than following the
device locale.

`StoreFailureTest` guards the rule: if a `message` property is ever reintroduced
on a failure type, that is English prose waiting to reach the screen.

**Verified:** 169 tests, 0 failures (`--no-build-cache --rerun-tasks`, so they
genuinely executed rather than being restored from Gradle's cache).

Two of these edits silently failed to apply on the first pass and left English
property keys (`"Wall"`, `"Entities"`) in place. They were caught by re-scanning
the sources afterwards rather than by trusting the edit, and fixed.

## Getting to a real build

The development container has no Android SDK and cannot install one
(`dl.google.com` is blocked by network policy), so `assembleDebug` had never run
through two phases of work. `.github/workflows/pafta-apk.yml` builds on GitHub's
runners instead, which have the SDK preinstalled — and produces a downloadable
APK without the project owner installing any development tools.

What the attempts found, in order. Every failure was real, and each one is now
closed in a way that cannot recur silently:

| # | Stopped at | Cause | Closed by |
| --- | --- | --- | --- |
| 1 | configuration, 1s | root declared `kotlin("jvm") apply false`, putting the Kotlin plugin on the inherited classpath; `:app` then requested `kotlin("android")` — same artifact — with a version, which Gradle refused to verify | root declares no plugins; each module declares its own, which also keeps `-PpaftaCoreOnly=true` free of AGP |
| 2 | `mergeDebugResources` | `101 No'lu Daire` — an unescaped apostrophe makes a string resource invalid | escaped, and `tools/check-strings.py` now rejects it in about a second |
| 3 | `compileDebugKotlin`, 2m8s | unknown — `--stacktrace` put 200 lines of Gradle internals between the error and the end of the log | dropped `--stacktrace`; the workflow now prints only compiler errors, failed tasks and "What went wrong", at the end of the log and in the job summary |
| 4 | `compileDebugKotlin`, 4m20s | `Unresolved reference 'R'` (a missing import an earlier edit had not actually applied) and `const val X = R.string.y`, which Kotlin rejects because R fields come from generated Java | both fixed, and both classes added to `check-strings.py` |

### The recurring mistake worth naming

Three times on this branch an edit I believed I had made was not in the file —
English property keys twice, and the missing `R` import once. Each was caught by
re-reading the source afterwards rather than by trusting the edit. The lesson
applied: after any batch of edits, grep for what should now be true, and for what
should now be absent.

### Checks that run before a build

`tools/check-strings.py` exists because the app module cannot be compiled in the
development container, and several error classes are decidable by reading the
source. It rejects unescaped apostrophes and quotes in string resources, values
starting with `@` or `?`, multi-argument format strings without positional
markers, `R.string` ids that are used but not defined, files using `R.string`
without importing `R`, and `const val` initialised from an R field. Each check
was verified by deliberately introducing the mistake and confirming it names the
right file and line.

### Known warning, deliberately not yet addressed

The Kotlin Gradle plugin is loaded in three subprojects, which it reports as
unsupported. The build proceeds past it and compiles every module. Fixing it at
the same time as a real error would make the next failure ambiguous about which
change caused it, so it waits for its own change. The likely fix is declaring
plugin versions in `settings.gradle.kts` under `pluginManagement` and requesting
them without versions in the modules, which would also remove the per-module
duplication the root build currently documents.

## Phase 2 — 3D viewer

- Filament `SurfaceView`, orbit/pan/zoom, wireframe and solid modes.
- GLB/GLTF through `gltfio`.
- Model tree from the glTF node hierarchy; layer palette drives node visibility.
- **Exit criterion:** a GLB orbits at interactive frame rates on a device.

## Phase 3 — measurement and annotation on real geometry

- Ray-pick against mesh and drawing geometry, feeding `MeasurementEngine`.
- Annotation placement, editing, persistence; screenshot and share.
- **Exit criterion:** measure a model, save, reopen, and see the measurement.

## Phase 4 — native formats (needs the NDK)

Assimp for OBJ/STL/PLY/DAE/3DS. First phase requiring `externalNativeBuild`;
budget time for CMake and ABI configuration.

## Phase 5 — BIM

IfcOpenShell for IFC, property panel, section/clipping planes, camera presets.
Revit interoperability goes through IFC round-trip via Revit's own
export/import — there is no free library that reads `.rvt` directly.

## Phase 6 — and beyond

DWG reading via LibreDWG (**decide on GPL-3.0 first** — see
[LICENCES.md](LICENCES.md)), CuraEngine slicing, and SketchUp `.skp` reading,
which stays marked as high-risk: the open-source parsers cover only some file
versions.
