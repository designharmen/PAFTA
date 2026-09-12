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

**`assembleDebug` now succeeds** and produces an installable APK — see
*Getting to a real build* below.

**The app installs, launches and works.** Confirmed on a device: the library
screen renders as designed — monogram, tracked caps, copper accent, format chip,
Turkish date — and importing a real 2.2MB DWG worked, storing it in a project.

The first device session also produced the first real product finding: the files
an architect actually has are DWG and RVT, and neither has a viewer yet. The app
said so, but unhelpfully — `.rvt` was not even a recognised extension, so it was
refused outright rather than kept. Both now import, and the message names the
export route that works today (DXF) instead of ending at "not supported yet".
See *Phase ordering* below — this changes what should be built next.

**Exit criterion, met.** The sample plan was imported on a tablet and **drew**:
walls, windows, furniture, the room labels, the grid, the layer palette built
from the file's own layers, the file name in the status corner, and the compass.
Pinch-zoom and one-finger pan were tried and work. Four rounds of device testing
to get here, three of them inconclusive because of the file rather than the app.

The screenshot also carries a discrepancy worth more than the good news: the
properties table read **36 entities where the file holds 40**, and the doors
appeared as small crosses rather than as opening arcs. 36 is exactly what this
plan yields with block references *unexpanded* (4 references instead of the 8
entities they expand to), so the build on the device predates the `BLOCKS`
work — which is what the build stamp exists to make visible, and it was added
after that APK. Asked to confirm from the library screen.

**Original criterion:** import a real DXF *on a device* and see it drawn.
The APK exists and the app compiles; whether it runs, and whether the chrome
matches the design, is unverified until someone installs it. Nothing in a
successful build says an app does not crash on launch.

The owner chose to close this before deciding the DWG/3D question (see *Phase
ordering* below), so the test is now written out step by step, in Turkish, in
[CIZIM-EKRANI-TESTI.md](CIZIM-EKRANI-TESTI.md) — DXF export from AutoCAD or
Revit, installing the current APK, what the screen should look like, and a table
mapping each possible outcome to what to report back.

Reading the drawing path end to end before that test found no blocker, and one
limitation worth stating in advance: `DxfReader` skips the `BLOCKS` section, so
an `INSERT` is drawn as a small cross at its insertion point rather than as its
block's geometry. Walls and linework drawn directly still appear; doors,
windows, furniture and title blocks will not. In a drawing where everything sits
inside one block, the whole plan reduces to crosses. Expanding blocks is pure
`core:dxf` work — writable and testable without a device — and it is the obvious
next task if the device test shows it.

## The first device test of the drawing screen — and what it found

The owner installed the APK and tried to import a drawing. The screen came back
with `Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok` and the library
still holding only the DWG from the previous session.

That message can come from exactly one place, which is what made it worth
acting on: `ProjectStore.import` parsed a `.dxf`, found zero entities it models,
and **refused the import**. Two defects sat behind it.

### Defect 1 — a refused import loses the user's file *and* the evidence

Refusing meant the drawing was never stored, and the message said only that
there was nothing to draw. A plan made entirely of `HATCH` records and a file
whose `ENTITIES` section is genuinely empty produce the same sentence and need
opposite answers — and neither the user nor the next session could tell them
apart. It is the `.rvt` lesson again: keep the file, and say something the
person can act on.

Fixed in three parts:

- `ProjectStore` now refuses a DXF only when it cannot be *parsed*. A DXF that
  parses but holds nothing drawable is imported and kept, exactly like the
  formats that have no viewer yet.
- `DxfDrawing` carries `entityTypeCounts` — every record type in the file's
  `ENTITIES` section with how many of each — and `blockEntityCounts`.
- `StoreFailure.Unreadable` gained `found: Map<String, Int>`, carried from the
  parse to `ui/UiText.kt`, which composes the Turkish sentence around it:
  `Bu DXF çizimi açıldı ama içinde çizilecek bir şey yok. İçinde şunlar var:
  HATCH (1240), SPLINE (12)`. The record names are the file's own, so they are
  data and stay untranslated — the same rule that already lets the properties
  table print `Gösterilmeyen`.

The bare sentence, with no list after it, now means something precise: the
`ENTITIES` section is empty.

### Defect 2 — blocks were never expanded

Reading the drawing path before the test had already flagged this as a
limitation; the test made it a priority. `DxfReader` skipped the `BLOCKS`
section entirely and drew every `INSERT` as a small cross. In a drawing from any
real CAD tool that is the doors, the windows, the furniture, the fixtures and
the title block — and in a file where the plan is placed as one reference, it is
the entire drawing.

`DxfReader` now reads `BLOCKS` into definitions and replaces each reference with
the block's own geometry, placed through a single `BlockTransform` (base point,
scale, rotation, position). The details that matter in practice:

- **Nested blocks** expand too — a block placed inside a block is two
  applications of the same transform.
- **Mirrored references** re-derive an arc from its transformed end points
  rather than adding the rotation to the stored angles. Adding is what puts a
  mirrored door in the wrong quadrant, opening through the wall.
- **Layer and colour inheritance**: geometry on layer `0` inside a block takes
  the layer of the reference, and colour `BYBLOCK` takes its colour, so the
  layer palette still matches what is on screen.
- **Three bounds** stop a bad file taking the app down: a depth limit, a guard
  against a block that references itself, and a 200,000-entity budget. Anything
  they stop is left as a visible marker, and `expansionTruncated` records that
  the drawing shows less than the file holds.

### Hardening found while re-reading the parser

A byte-order mark at the head of an exported DXF made the very first group code
unreadable and failed the whole file. Several tools write one, and it is
invisible in any editor — so the refusal would have been unexplainable to
whoever exported the file. Both the UTF-8 form (as it arrives when the stream is
decoded as Latin-1) and a decoded `U+FEFF` are now skipped, with a test for each
and one proving a file without a mark is untouched.

### The second device report, and the two things it changed

The same message came back, this time with **no inventory after it** and with
the library **empty** — no project row at all. Both facts are informative.

An empty list is the tell. Under the change above an importable DXF is *kept*,
so a device running this build would show the project row and the message
together. A build where the import is refused shows the message and nothing
else. But the screenshot cannot say which build produced it, and that ambiguity
costs a whole device round — export, transfer, uninstall, install, test.

So the build stamps itself. `app/build.gradle.kts` takes a `paftaBuild`
property, the APK workflow passes GitHub's run number, and the library bar
prints it faintly at the right: `yapım 9`. A report from the device now names
the build it came from, and a build made by hand says `yerel`.

The second change is what the message can say when there is no inventory.
`StoreFailure.Unreadable.found` became a `FileInventory` — record types, layer
count, block count — because those three separate three different problems that
look identical on screen:

| What the file holds | What it means | What the screen says |
| --- | --- | --- |
| records the reader cannot draw | a real drawing, unsupported entity types | `İçinde şunlar var: HATCH (1240)…` |
| no records, but layers and blocks | an export that wrote definitions and no geometry | `Dosyada 312 katman ve 208 hazır parça tanımı var, ama çizim bölümü boş` |
| nothing at all | an empty file, or the wrong view exported | `Dosyanın çizim bölümü tamamen boş` |

A third change came out of asking what could produce exactly this symptom — an
empty drawing whose inventory is *also* empty, meaning no `ENTITIES` section was
parsed at all. A section that never closes used to run to the end of the file,
so one missing `ENDSEC` early on could hide every section after it, `ENTITIES`
included. The next `SECTION` now ends the previous one as well: a well-formed
file is unaffected, and a damaged one loses only the section that is broken.
Both cases are tested.

### The third report settled it: the file was never the user's drawing

Asked what produced the DXF, the owner answered that they had searched the web
and downloaded one. So the two device tests had been testing a stranger's file,
not PAFTA — and no amount of reading our own parser was going to explain it.

The fix is a file whose contents are known and asserted: `ornek/kat-plani.dxf`,
a 12 × 9 m two-bedroom plan generated by `tools/ornek-plan-uret.py`. It carries
what a real CAD plan carries — layers, closed wall outlines, four doors placed
as block references (one rotated, one mirrored), windows, furniture and room
labels — and `SamplePlanFileTest` and `SamplePlanImportTest` assert what reading
it must produce, all the way through import into a `.pafta` container and back
out as a drawing. **If this plan does not appear on a device, the fault is
ours**, which is exactly the property the previous two rounds lacked.

One defect found, and it was in the test rather than the code: the end-to-end
test asserted "more than 40 entities" from a guess. The plan holds exactly 40,
and the assertion now states the composition that makes 40 rather than a
threshold. Same class of mistake as the two bad expectations in Phase 0 —
worth recording, because a guessed number in a test is a failure waiting to be
blamed on the code.

**Verified:** `./gradlew -PpaftaCoreOnly=true test --no-build-cache
--rerun-tasks`, **195 tests, 0 failures** (was 171). Fourteen are new: ten in
`DxfBlockTest` covering placement, scale and rotation, layer inheritance, the
mirrored arc, nesting, a self-referencing block, an undefined block, repeated
placement and the inventory counts; three in `DxfByteOrderMarkTest`; one in
`ProjectStoreTest` for a DXF that is now kept and explained rather than
refused.

**Not verified:** whether the owner's own drawing now draws. That still needs
the device.

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

## The brand system, applied for real

The owner sent the actual brand guideline — *HARMEN DESIGN — Master Color +
Typography System, V1.1* — and it settled a quiet inaccuracy: PAFTA's palette
and type had been derived from a reference image and carried the brand's name
without its values. Inter, the interface typeface through Phases 0 and 1, is
named in the guideline's DON'T list.

What the guideline fixes, and the app now follows: six colours whose HEX values
do not change, with only controlled tint / shade / opacity derivations allowed;
and three typefaces — Archivo, IBM Plex Sans, IBM Plex Mono — all SIL OFL, which
the guideline chose partly so the system needs no paid font.

Two of its rules changed the interface rather than just its colour values:

- **Bronze draws, it does not label.** Bronze is capped at 3.4:1 on ivory and
  forbidden as text below 24px. PAFTA had bronze on the active tab, the selected
  tool caption, the material name, the format chip, the dismiss action and every
  dimension value on the canvas. Bronze is now line, icon, outline, rule and
  wash only.
- **Navy is the selected state.** With bronze off the text, selection needed a
  carrier and the guideline names one for interfaces exactly: deep navy as
  accent surface and selected state, capped at 25% of the surface.

The nine bundled font files are fixed instances cut from the upstream variable
fonts at the weights the guideline names, so no weight outside 300–500 is
reachable and Archivo 700+ — ruled out by name — is not in the build at all.
`archivo_condensed_400` is cut at width 88, which is the guideline's
drawing-sheet instruction and the reason the brand picked a family with a width
axis; room names on the plan use it. Every file was checked for the guideline's
own Turkish test (ı / İ / Ğ / Ş) before being added.

Two deviations are stated rather than hidden, both in
[DESIGN-SYSTEM.md](DESIGN-SYSTEM.md): the app stays dark where the guideline's
drawing-sheet pairing asks for a light ground, and the tool rail's caption keeps
the family and weight but reduces size and tracking, because `Katmanlar` does
not fit a 64dp rail at 11px / 0.14em.

`tools/check-strings.py` now fails the build if a forbidden typeface or any
family outside the three appears in `res/font`, or if Kotlin references a font
file that is not there. Both checks were verified by deliberately introducing
the mistake and confirming the message names the right file and line.

## Updating from inside the app

Four device rounds in, the install chore was costing more than the bugs: open a
browser, find the run, download a zip, extract it, move the file to the tablet,
uninstall the old app, install the new one. The owner asked for a button.

Android will not let one app install another silently — and should not — so the
honest ceiling is one tap plus one confirmation. That is what `GÜNCELLE` in the
library bar now does: check, download with visible progress, hand the file to
Android's installer. A one-time "allow this app to install apps" permission is
requested before the download rather than after it, so a missing permission
never wastes 17 MB of someone's data.

**Why releases rather than artifacts.** GitHub's build artifacts require an
authenticated request; release assets do not. Publishing the APK as a release
is what lets the app fetch its own update with **no credential inside the
APK** — the alternative was embedding a token in a binary the owner carries
around, which is a credential leak waiting for a lost tablet. The workflow tags
each build `yapim-<run number>`, the same number the app stamps into its own
library bar, so comparing "what is published" with "what is running" needs no
version parsing at all.

This requires the repository to be public while it is in use. The owner's
decision, recorded: public during development, private again once every phase
works.

The failure modes are data, not prose, exactly like `StoreFailure`:
`NO_NETWORK`, `NO_RELEASE`, `SERVER`, `CANNOT_SAVE`, turned into Turkish in
`ui/UiText.kt`. The download writes to a second file name and renames only when
it is whole, so Android's installer can never be handed a half-downloaded APK.

Nothing was added to the build for it: `HttpURLConnection` is in the platform
and `kotlinx.serialization` was already a dependency.

One compile error, and it was mine: the update check read the asset size as
`asset.sizeBytes` where the field is `size` — the response's own name and the
name we expose outwards differ, and I mixed them. Fixed, and the rest of the
file was then checked mechanically: every data class's fields were extracted and
every property access in the file compared against them, so a second instance of
the same mistake could not be hiding.

**Verified on the real endpoints**, not assumed:

| Check | Result |
| --- | --- |
| repository visibility | `private: false`, `visibility: public` |
| the exact URL the app queries | returns `yapim-15` with the APK asset |
| the asset download URL | `200`, 17,598,066 bytes, `application/vnd.android.package-archive` |

What that does not prove is the app's own path — button, download, installer —
which needs a device.

### The device test of the button, and the defect it found

It worked up to the last step: the button found build 16, downloaded it, and
opened Android's installer — which then refused with *"uygulama yüklenmedi"*.

The cause was mine and it was in the build, not the button. Every run signed the
APK with a **freshly generated debug key**, because a GitHub runner starts
without one. Android refuses to replace an installed app with a build signed by
a different key — that check is what stops anyone from pushing a counterfeit
update over a real app — so builds 15 and 16 could never install over each
other, no matter how well the button worked. `versionCode` was also pinned at
`1` for every build, which is not an update in Android's terms either.

Both are fixed: `versionCode` is now the build number, and every build is signed
with one stable key.

**Where the key lives, and why.** The repository is public for now, so a
keystore committed in the clear would let anyone sign a package Android would
accept as an update to the owner's PAFTA. It is therefore committed **encrypted**
(`imza/pafta-imza.p12.enc`, AES-256, PBKDF2, 240k iterations), with the
passphrase held only in the `PAFTA_IMZA_SIFRESI` repository secret. The workflow
decrypts it into the runner's temporary directory, and the passphrase is passed
to Gradle through the environment rather than the command line, so it never
reaches a process listing or a log.

If the secret is missing, the build still produces an APK but **no release is
published** — quietly publishing a differently-signed build would simply
recreate the defect on the next update.

The cost, once: the currently installed build was signed by a runner's throwaway
key, so it cannot be updated in place. PAFTA has to be uninstalled and the first
stably-signed build installed by hand. Every build after that is one tap.

**Measured on the published file**, by downloading build 18 from the release URL
the app itself uses and reading it:

| Check | Result |
| --- | --- |
| signing certificate (SHA-256) | `E2:C1:01:B0:…:3D:CB` — the project key, not a runner's |
| `versionCode` | `18` — the build number, where every earlier build said `1` |
| declared permissions | `INTERNET`, `REQUEST_INSTALL_PACKAGES`, and nothing else |

### Block expansion, confirmed on the device

The same round closed the question the `BLOCKS` work opened. On build 15 the
sample plan reports **40 entities** where the previous build reported 36, and
the doors draw as opening arcs rather than as crosses. The reader's expansion is
therefore doing on a tablet exactly what `DxfBlockTest` asserts on the JVM.

## Getting to a real build

The development container has no Android SDK and cannot install one
(`dl.google.com` is blocked by network policy), so `assembleDebug` had never run
through two phases of work. `.github/workflows/apk.yml` builds on GitHub's
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
| 5 | `compileDebugKotlin`, 2m7s | `StoreFailure.message` still referenced twice, `_error` still typed `String?`, and `Modifier.padding(horizontal =, top =, bottom =)` — an overload that does not exist | fixed, and all three classes added to `check-strings.py` |
| 6 | same as 5 | the docs-only commit was built against the unfixed code | n/a |
| **7** | **nothing — succeeded** | — | — |

**Attempt 7 produced an APK.** `assembleDebug` completed in 2m18s and the
artifact uploaded: `PAFTA-apk`, 17,146,210 bytes, from commit `2da2e18`.

Worth recording why the end came quickly once the diagnostics were right:
attempt 5's error list named only three files. Kotlin analyses the whole module
and reports every error it finds, so the other sixteen app sources — the tool
rail, the inspector, the library screen, the viewport with its text measuring and
dozens of Material icon names — had already been validated by the compiler. That
turned "how much of this 19-file module is wrong?" into a list of four known
faults.

### The recurring mistake, and its actual cause

Five times on this branch an edit I believed I had made was not in the file:
English property keys twice, a missing `R` import, and then three separate
changes to `EditorViewModel`. Each was caught by re-reading the source rather
than by trusting the edit.

Attempt 5 finally exposed the cause rather than the symptom. A script that
patched several files in sequence did this:

```python
p = ".../EditorViewModel.kt"
s = open(p).read()
s = s.replace(...)          # three edits computed

p = ".../LibraryViewModel.kt"   # p and s reassigned
s = open(p).read()
s = s.replace(...)
open(p, "w").write(s)           # only this file is written
```

Every EditorViewModel edit was computed into `s` and then discarded when `s` was
reassigned, because the write before moving to the next file was missing. That
one omission accounts for all three of attempt 5's errors, and for the missing
`R` import in attempt 4.

Two practices follow, and both are now habit: write each file before moving to
the next, and after any batch of edits grep for what should now be true *and* for
what should now be absent. The second is what actually catches it — the first can
be forgotten again.

### Checks that run before a build

`tools/check-strings.py` exists because the app module cannot be compiled in the
development container, and several error classes are decidable by reading the
source. It rejects unescaped apostrophes and quotes in string resources, values
starting with `@` or `?`, multi-argument format strings without positional
markers, `R.string` ids that are used but not defined, files using `R.string`
without importing `R`, `const val` initialised from an R field,
`Modifier.padding` mixing `horizontal`/`vertical` with per-side arguments, and
any surviving reference to the removed `StoreFailure.message`. Each check was
verified by deliberately introducing the mistake and confirming it names the
right file and line — for the padding rule, the same line number the Kotlin
compiler had reported.

### Known warning, deliberately not yet addressed

The Kotlin Gradle plugin is loaded in three subprojects, which it reports as
unsupported. The build proceeds past it and compiles every module. Fixing it at
the same time as a real error would make the next failure ambiguous about which
change caused it, so it waits for its own change. The likely fix is declaring
plugin versions in `settings.gradle.kts` under `pluginManagement` and requesting
them without versions in the modules, which would also remove the per-module
duplication the root build currently documents.

## Phase ordering, after the first device test

The plan had Phase 2 as the 3D viewer. The first real use suggests otherwise.

The user's own files are DWG (AutoCAD) and RVT (Revit). DXF — the one format
PAFTA reads — is what both of those export to, so the app is usable today only
by asking the user to convert every file before importing it. A 3D viewer does
not change that; **DWG reading does.**

The case for moving DWG earlier:

- it is the format the user actually has, in volume
- the DXF engine, measurement, annotation, layer palette and `.pafta` container
  are already built and tested; DWG reading feeds all of them
- LibreDWG is a C library, so it is the first phase needing the NDK — which the
  3D viewer also needs. Doing it first derisks both.

The case against, which is real: LibreDWG is **GPL-3.0**, and linking it sets the
licence of the whole application. That is a decision to take deliberately, not
in passing. It is allowed under the project's licence constraint as written, but
it should be confirmed before the work starts, not after.

Recorded as an open question rather than a decision.

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
