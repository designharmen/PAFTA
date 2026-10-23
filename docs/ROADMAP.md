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

## The pivot: PAFTA becomes a drawing tool

The owner stopped the work mid-phase and asked for something different: study
**Rayon** ([rayon.design](https://www.rayon.design/)) and build PAFTA to be like
it, replanning every phase if necessary.

### What Rayon is, from its own documentation and third-party reviews

A browser-based 2D architectural drawing tool — "the new-gen web-based tool that
streamlines the creation of precise and aesthetic architectural 2D drawings,
data-linked tables, and real-time collaboration". Its feature areas:

| Area | What it does |
| --- | --- |
| Drawing | walls, zones (rooms defined in one click), openings (doors and windows dropped onto walls), lines, shapes |
| Editing | copy, move, trim, offset, scale, join, rotate |
| Blocks | 3,000–4,000 CAD blocks — furniture, technical symbols, legends — with top, side and front views; users save their own |
| Styles | colours, line weights, hatches, fonts, text sizes |
| Annotation | dimensions, leaders, text, measurement |
| Data | properties on objects, and tables generated from them (room schedule, door and window schedule) |
| Layout | layers, pages, print |
| Files | imports DWG, DXF, PDF; exports PNG, PDF, DWG, DXF |
| Collaboration | real-time multi-user editing, comments, sharing |
| AI | visualisation from a plan, and tracing an image into a vector sketch |

Pricing is a free tier of three models and a Pro tier at about $38 a month.

### The finding that decides PAFTA's shape

**Rayon does not run on a tablet.** Its own FAQ says it is "currently compatible
only with desktops", that tablets and phones are not optimised, and its community
carries open requests for even a read-only viewer on mobile. Tablet support is on
their roadmap, not in their product.

So the instruction "make PAFTA like Rayon" does not mean writing a copy of
Rayon. It means building **what Rayon is for, on the device Rayon does not
run on** — an architect standing in a flat with a tablet, not sitting at a desk.
That is a product position rather than an imitation, and it changes what matters:
every tool has to work with a finger, offline, on a screen held in one hand.

### What this does to the work already done

Almost nothing is wasted, and that is not a consolation — it is why the pivot is
affordable. The geometry, the viewport, snapping, the undo history, auto-save,
the `.pafta` container, the DXF reader **and writer**, the Turkish-only
discipline and the brand system are all foundations of a drawing tool, not just
of a viewer. What changes is the direction of travel: PAFTA stops being
something that *shows* a drawing and becomes something that *makes* one.

The old Phases 2–6 are withdrawn. The 3D viewer in particular was a plan for a
different product: Rayon is deliberately 2D, and the owner's own files are floor
plans.

### What the owner added when the plan was put to them

Four decisions, all of which change the plan rather than a detail of it:

1. **PAFTA has no commercial aim.** That dissolves the licence question that has
   been open since Phase 0: GPL-3.0 is acceptable, so LibreDWG can read DWG, and
   open-licensed material — block libraries, sample drawings — can be collected
   from GitHub and elsewhere, each recorded in `LICENCES.md`.
2. **Nothing from the original brief is given up.** The Rayon-shaped drawing
   work is *in addition to* the 3D viewer, the other 3D formats and IFC/BIM, not
   instead of them. Those phases are restored below, later in the order.
3. **It has to run on the desktop too**, at the end.
4. **Google Drive last**, so a project saved on the tablet can be opened and
   carried on from a desktop. Deliberately last, because it may need a separate
   email account for PAFTA.

And one standing rule, now in `CLAUDE.md`: the interface stays simple. A tool
that does nothing must not look like a tool that works.

### The new plan

Fourteen phases. Each one still ends with an installed, tested build, and the
order is chosen so the app becomes useful as early as possible: everything that
needs native code, a server or an account sits at the end.

| Phase | What it delivers | Rests on |
| --- | --- | --- |
| **A — Drawing** | a wall with real thickness, a line, a rectangle, a circle; select, move, delete; saved into the project and exported to DXF | snapping, viewport, undo, `DxfWriter` — all built |
| **B — Architectural objects** | zones (a room named and measured in one tap), openings (doors and windows on a wall, with width and swing) | A |
| **C — Editing** | copy, rotate, scale, offset, trim, join, multi-select | A |
| **D — Library** | place blocks; build the library from open-licensed sets and from the office's own DXF blocks | block expansion — built |
| **E — Styles and layers** | line weights, colours, hatches, text styles; create, rename and reorder layers | layer palette — built |
| **F — Annotation** | dimension chains, leaders, tags, text on the drawing | measurement — built |
| **G — Properties and tables** | properties on objects; room schedule and door/window schedule generated from them | B |
| **H — Sheets and output** | sheets with a title block; PDF, PNG and DXF export; print | A |
| **I — DWG and PDF import** | open the files the owner actually has, with no computer in the loop. **LibreDWG, GPL-3.0** | the licence decision above |
| **J — 3D viewer** | Filament, GLB/GLTF, orbit and pan at interactive frame rates | NDK groundwork from I |
| **K — Other 3D formats** | Assimp: OBJ, STL, PLY, DAE, 3DS | J |
| **L — BIM** | IfcOpenShell for IFC; property panel, section planes | J, K |
| **M — Desktop** | the same app on a desktop through Compose Multiplatform | everything above staying free of Android-only APIs |
| **N — Google Drive** | projects saved to Drive; open on the tablet, carry on at the desk | M, and an account for PAFTA |

3D-print slicing (CuraEngine, AGPL-3.0) stays on the list beyond N, unscheduled,
as it was in the original brief.

**Two constraints that shape the late phases.** Phase M is not a rewrite if the
groundwork holds: the `core:` modules are already pure Kotlin and the interface
is Compose, so what matters is that new code keeps Android out of the parts that
do not need it — a rule now written into `CLAUDE.md`. Phase N needs an account
and Google's consent screen, which is free but is paperwork, hence its place at
the end.

**Real-time collaboration is not in the plan.** Rayon has it; it needs a server
running every month, and a server costs money, which the project's constraint
forbids. Sharing a file, or exporting a PDF to send, covers most of what it is
used for and costs nothing. If the owner wants live multi-user editing later, it
is a separate decision with a monthly bill attached.

**AI features are not in the plan** either, for the same reason: Rayon's image
generation and tracing run on paid services.

### Exit criterion for the pivot as a whole

An architect can stand in a room with a tablet, draw the room, place its door,
name it, read its area, and email a PDF — without a desktop anywhere in the
process.

---

## The tool specification, and what it changes

The owner supplied an analysis of AutoCAD's editing commands, Revit's element
tools, twenty wall types and twenty floor finishes, with the instruction to
build every one of them into PAFTA. This section records what that means for
the plan. Nothing in the fourteen phases is dropped; the specification mostly
*fills them in*, and in two places it changes the order.

### The distinction the specification makes, and PAFTA already keeps

Its first point is that the list is two different kinds of thing:

* **Editing commands** — fillet, chamfer, offset, trim, extend, copy, move,
  rotate, mirror, line, circle, rectangle, layer, text, hatch, dimension.
  Low-level vector work on PAFTA's own drawn geometry.
* **Building elements** — wall, door, window, floor, roof, column, beam, stair.
  Parametric objects with properties, hosts and schedules.

PAFTA has kept them apart from the beginning, and for the same reason: the
imported file is never rewritten. `DrawnShape` is PAFTA's own layer, the payload
is the architect's file, and the two are merged only to draw and to export. The
specification's warning — that editing a DWG's own geometry needs a paid SDK —
therefore describes a line PAFTA already sits on the right side of.

### Walls: one object, four axes, not twenty types

The twenty wall types are four different questions wearing one hat: what it is
made of, how it is built up, what it does, and whether it is a system. Twenty
separate types would be twenty places to fix the same bug. One wall carrying
four properties is the right shape, and it is the one the specification
recommends:

| The question | On the wall | Covers |
| --- | --- | --- |
| What is it made of | `material` — **built** | Brick, Stone, Concrete, Timber |
| What does it do | `function` | Partition, Drywall; Load-Bearing, Shear; Retaining, Gabion; Parapet |
| How is it built up | `layers[]` of material and thickness | Cavity, Precast, Cladding |
| How does it perform | `fireRating`, `acousticRating` | Fire Wall, Soundproof Wall |

Three of the twenty are not walls at all and should not pretend to be: a Green
Wall is planting, a Boundary Wall is the edge of the site, and Curtain and Glass
Walls are facade systems with a grid of their own. The first two belong to a
site layer; the third is the hardest item on the whole list and goes last.

### Floors: the twenty are finishes, not floors

The twenty floor types are what the floor is *covered with*. The floor itself is
a slab: an outline, a thickness and a level. So they are two things —
`FloorSlab { boundary, thickness, level, finish }` and a catalogue of finishes in
four families (timber, stone and ceramic, resilient, textile). The catalogue
feeds the material palette that is already in the interface, which means a floor
read out of an imported file can be given a finish and a colour **without the
file being touched** — which is the whole point.

### What this adds to the phases

| Phase | Was | Now also carries |
| --- | --- | --- |
| **C — Editing** | copy, rotate, scale, offset, trim, join, multi-select | fillet, chamfer, extend, mirror — all **built and tested in `core:geometry`**; the ones needing two shapes wait on multi-select |
| **D — Library** | place blocks | the twenty wall types and twenty floor finishes as ready-made catalogue entries |
| **E — Styles and layers** | weights, colours, hatches | hatch patterns as material representation in plan and section |
| **G — Properties and tables** | schedules | fire and acoustic ratings, wall function, floor finish — the properties the schedules are made of |

And it adds one phase that was not there, between B and C:

| **B2 — Building elements beyond the wall** | column, beam, floor slab, flat roof — each an object with a profile, a level and a material. Structural walls are not a new type: a shear wall is a wall whose function says so. |

Stairs, pitched and hipped roofs, curtain walling and the constraint system that
`ALIGN` needs are the four hardest items on the list, and they are placed last
within their phases rather than first. A stair is parametric generation with a
building-regulation rule set behind it; a hipped roof is a geometry problem in
its own right. Neither is a good place to be while the wall is still settling.

### What is built already

Phase C's arithmetic landed early, because the specification puts it first and
because it is pure geometry that can be proved on this machine rather than
guessed at on a tablet. `core/geometry/Editing.kt`: fillet with its tangent
arc, chamfer with two distances, offset for a segment and for a run of them with
its corners closed, trim against a boundary keeping the side pointed at, extend
to a boundary from whichever end is nearer, and rotate, mirror and scale. Every
one has tests, including the ones that must refuse: a radius that will not fit,
a boundary the segment would miss, an inset that would turn a shape inside out.

The two that need only one shape — **offset** and **turn** — are in the
interface now. Fillet, chamfer, trim and extend each need the user to pick
**two** shapes, and PAFTA has no way to hold two selections yet. They stay out
of the interface until it does, rather than appearing as buttons that do
nothing, which this project has a rule against.


---

## The interface, rearranged

The owner looked at the editor and asked for five things: make the right-hand
layer table open and shut instead of standing there permanently, make the P
badge go back to the project list, put a "new project" option on that list, put
the whole set of drawing tools on the top row where Aktif · Notlar · Mobilya
used to be, and put the building elements — wall, door, window, floor,
furniture — down the far-right column.

Doing it turned up something worth writing down. That top row was carrying a
`DOSYA` menu, a `DÜZENLE` menu, a `PAYLAŞ` button, an edit/view mode switch and
five tabs — **eleven controls, and not one of them changed anything**. Each set
a value in the state that nothing read: `onMenu` and `onShare` were never wired
from `MainActivity` at all, and `activeTab` and `editMode` were stored, carried
carefully through undo and redo, and never looked at. Eleven ways to tap and see
nothing happen, on the row the eye lands on first. They are gone. Rule 6b in
`CLAUDE.md` says a thing that does not work must not look like it works; it
turns out the rule needed applying to what was already there, not only to what
comes next.

The layout now reads down and across:

| Where | What |
| --- | --- |
| Top row | the P badge (the way back to the projects), the project name, undo and redo |
| Second row | every drawing and editing tool: Seç · Çizgi · Dikdörtgen · Daire · Yay · Yuvarlat · Pah kır · Buda · Uzat · Ölç · Tarama · Metin · Izgara |
| Third row | **only when the tool in hand needs something told** — the wall's thickness and material, the opening's width, the corner size, what Ölç is taking |
| The sheet | everything left over |
| Far right | Duvar · Kapı · Pencere · Mahal · Döşeme · Mobilya, with the panel handle above them |
| Between them | the layer and properties panel, when it is asked for |

The settings strip replaces the little panels that used to unfold underneath
each tool in the old left rail. One place for "the setting for what I am
holding" beats six places, and it reads horizontally, which is the shape of the
space above a drawing.

Döşeme and Mobilya are drawn faint and do not respond. They are phase B2, and
the rule above is why they are not simply left out: the owner asked for them by
name, so the column has to show that they are coming rather than pretend they
were never asked for.

### A project that starts empty

`YENİ PROJE` needed one change in the core, and it is a nice illustration of why
an error message should carry data rather than a sentence. `openAsDrawing`
refuses a project whose drawing holds nothing — that refusal was written for an
imported file full of records this reader cannot draw, and it is the right
answer there. A project the user has only just started looks identical to it: an
empty DXF. So the manifest now carries `blank`, set when PAFTA makes the project
itself, and the same empty drawing is an error in one case and a fresh sheet in
the other.

A blank project opens on 20m × 15m rather than on its own extent, because an
empty extent fits at 1mm per pixel and the user would start on a view one metre
wide. As soon as anything is drawn the view frames what was drawn instead.

The name is made unique before the project is written, not after: the file name
is found first and the project takes *that* as its name, so a second `Yeni
proje` becomes `Yeni proje-2` in the list rather than a second identical row.

---

## What the device said about the rounded corner

Yapım 37 went to the tablet and came back with one screenshot and two problems
in it, both from the same command.

**The corner was a gap with a pencil line across it.** `fillet` shortened both
walls and added a `DrawnShape.Arc` between them — and an arc was linework, so the
two wall bands stopped a radius short of each other and a hairline curved across
the hole. The arithmetic was right and the drawing was wrong, which is a useful
reminder that a plan is not a diagram of its own geometry: **a rounded corner
between two walls is a piece of curved wall.** So `Arc` now carries a
`thicknessMm`, zero meaning "this is a pencil line"; `wallBands` fills a thick
arc the same way it fills a wall; `zonePlans` cuts it into short straight pieces
so a room whose corner has been rounded still closes — otherwise tidying a
corner would open the room, which is the exact opposite of tidying it. Chamfer
had the same fault and the same fix, more simply: between two walls the flat it
leaves is now a short wall, not a line.

Two shapes that touch along one exact line can still show a seam where they are
merged, so a wall landing on a rounded corner is given one millimetre to overlap
by — under a screen pixel at any plan scale, and the difference between a closed
corner and a hairline.

**Every door slid along by however much the wall was cut back.** This one is the
price of a design decision that is still right. An opening does not hold a place
on the sheet; it holds how far it is from its wall's *start*. That is what keeps
a door in its doorway when the wall is dragged or made longer — and it is
exactly what breaks when the start itself moves, which is what fillet, chamfer
and trim all do. The fix is one function, `withLines`, through which all four
two-shape edits now change a wall: it measures how far the start travelled along
the wall's own direction and re-measures every opening in that wall from the new
start. A door that no longer fits the shortened wall is kept inside it rather
than sent off the end.

Turning a wall and typing a new length into it were checked against the same
question and are both already right — a rotation carries its doors round with
it, and a length change moves the far end, not the start.

### Phase C is finished

`mirror` and `scale` were built and tested in `core:geometry` and never reached
the interface; `join` was on the phase list and not written at all. All three
are in now, which closes the phase.

**Aynala** and **Birleştir** are two-tap tools like the other four. Mirror takes
the thing to reflect and then the wall to reflect it about — the original stays,
because half a plan and its mirror image is the whole point. Rectangles are
refused rather than reflected: a rectangle is held as two opposite corners with
its sides square to the sheet, so reflecting one about a sloping wall would
quietly straighten it, and a wrong answer is worse than a refusal. Join makes
one wall out of two that lie on the same line and touch; the second one's doors
and windows come across onto the survivor, re-measured, because a door does not
stop existing when the wall it is in is renamed. Two walls of a corridor and two
walls at a corner are both refused, and so is a gap — filling one in would be
inventing wall nobody drew.

**Ölçekle** is in the right-hand panel next to Döndür, as four percentages:
50, 75, 150, 200. It scales about the shape's own middle, so the thing stays
where it was pointed at rather than flying across the sheet, which is the
behaviour that makes `SCALE` frightening in the programs that have it.

---

## Phase B2 — the building elements that are not walls

Column, beam, floor slab, flat roof. The phase the tool specification added
between B and C, and the four things the element column down the right-hand side
was promising but could not yet do.

### Each one is the shape of the question it answers

**Kolon** is a point with a profile. Square, rectangular or round — the three
that get poured — and it is **filled solid**, because that is what a column is
where the plan cuts through it and a plan that outlines one is drawing a hole.
A round column is filled as a thirty-six-sided polygon so that the band list
stays one kind of thing, but it still exports as a single `CIRCLE` record: how
something is drawn and what it is are different questions. Placing one goes
through the same snapping a wall end does, because columns land on grid
intersections and wall corners far more often than anywhere else.

**Kiriş** is a line with a width, and it is the one element deliberately **not**
filled. A plan is a horizontal cut about a metre off the floor; a beam is over
your head. Drawing it as an outline when everything else in the building is
drawn solid is what tells you at a glance that it is above you. (The convention
is a dashed outline. Dashes wait for the line styles of phase E; an outline says
the same thing today without inventing machinery for it.)

**Döşeme** does not carry its own outline, and this is the whole design. Like a
room, it carries *where the user tapped*, and the walls decide the rest. A slab
whose boundary had been frozen at the moment it was placed would stop matching
the room the first time a wall moved, and the plan would then be lying about the
building. A test pulls a wall in by a metre and checks the slab simply measures
less.

It is measured to the **middle** of the walls, where a room is measured to their
faces. A slab is poured under the walls, not between them, so the two numbers
are different and both are right: the structural area and the usable area. That
is also why the slab writes its note a quarter of the room's height below the
centre — the room above it writes its name at the centre, and two labels on the
same point are two labels nobody can read.

**Teras çatı** is not a fifth element. It is a slab with `kind = ROOF` at a
different level, which is what a flat roof is in the building too: a deck with
nothing on top of it. Making it a separate tool would have put a seventh button
in the right-hand column saying nearly the same word as the sixth, which rule 6b
forbids. It is a choice in the slab's settings strip instead.

### The twenty floor finishes

The specification's twenty "floor types" are in, as `FloorFinish` — twenty
entries in four families, on one property of the slab. The specification's own
reading was right: they are not twenty kinds of floor, they are what the floor
is *covered with*. Keeping them as a catalogue means the twenty-first is a line
of code rather than a class, and it means a floor read out of somebody else's
DXF can be given a finish **without their file being touched**, which is the
point of PAFTA's whole overlay design.

The panel shows them in their four groups — ahşap, taş ve seramik, esnek ve
dökme, tekstil — because twenty in one list is a list nobody reads, and four
short lists are four lists where the one you want is in the group you already
had in mind. Each finish carries an ASCII code as well as its Turkish name,
because the code travels into exported DXF material and layer names and `Ş`
does not survive that journey.

### What a tap means when two things share a floor

A room and the slab under it fill exactly the same outline, so the rule already
written down for everything else now covers them both: **the thing put down most
recently is the thing a tap means.** Two separate passes would have made
whichever came second permanently untappable under whichever came first. Walls,
columns and beams are all still found before either, because they are things you
can see the edges of and a floor is not.

---

## The device, round three: a wall turned round behind our backs

Three failures came back off yapım 39, and the first one was a correction that
made things worse — which is the most useful kind of bug report there is.

**"Daha fazla kaydı."** The doors in a filleted wall moved *further* than
before the fix that was supposed to stop them moving. The cause was one line in
`core:geometry` that had been right for two rounds and was never questioned:

```kotlin
first = Segment2(farEndFrom(corner, first), touchFirst)
```

`fillet` returns each arm with the corner end **last**, whichever way round it
was drawn in. For half of all walls that is the user's wall handed back
reversed. Nothing downstream noticed, because a reversed wall draws identically
— but an opening holds how far it is from its wall's *start*, so reversing the
wall silently moves every door in it to the other end. The previous round's fix
then measured the "slide" against a direction that had flipped, and doubled the
error instead of cancelling it.

The tests that passed were passing for a reason worth recording: both walls in
them happened to be drawn *into* the corner, which is the one orientation
`fillet` does not reverse. `WallDirectionTest` now walks all four combinations
of which end of each wall touches the corner, which is the test that should
have been written first. Every two-shape edit now turns its result back the way
that wall's own line runs before anything else is worked out.

**Birleştir refused everything.** The collinearity tolerance was
`JOIN_TOLERANCE_MM`, one millimetre — which is what two *identical* numbers are
within, and nothing drawn with a finger on glass is ever that. The right
tolerance comes from the walls themselves: half the thinner one's thickness,
between a centimetre and fifteen. Inside that the two bands lie on top of each
other along their whole length, which is exactly what "the same wall" looks
like on a plan. Two pencil lines still get the centimetre, because a line has
no thickness to hide a kink in.

**Uzat refused a lot too**, for two separate reasons. It only ever stretched
the wall picked first, so pointing at the pair in the other order got "zaten
ulaşıyor" — it now tries the other way round before refusing, because which of
two walls is short is obvious looking at the plan and not worth making anybody
think about. And it reached only to the *centre line* of the wall it was aimed
at, when a wall is reached to its face: the meeting may now land half a
thickness past the end of the centre line.

It was also giving the wrong reason. "It already reaches" was returned for
every failure including "it would miss entirely", because the code inferred
crossing from *neither direction working* — which is equally true of two walls
that will never meet. `crosses` now asks the question directly, and
`WOULD_MISS` says the thing that is actually wrong.

## Everything moves in 10cm steps now

The owner's rule: *"yeni eklenecek veya hareket ettirilecek her şey için
referanslar 10 cm lik ızgaralar olacak."* Drawing and measuring already snapped;
dragging and placing did not. Dragging now rounds the **whole distance dragged
so far** to the grid and moves the shape by the difference — so a shape that
started on the grid stays on it, one that did not keeps its own offset, and
either way it moves by clean amounts rather than by whatever a thumb did. A
door's position along its wall is rounded the same way: 1837mm along a wall is a
door nobody can dimension. Turning the grid off gives the finger back, exactly
as it does everywhere else.

## The interface had no pictures in it

*"Sistemde görsellik yok, her şey yazı."* Four wall materials and twenty floor
finishes, each of them a line of Turkish and nothing else — and a material is a
thing you recognise by looking at it.

Every material and every finish now carries the mark a drawing gives it, drawn
rather than fetched: brick as a running bond, concrete as aggregate over
stippling, aerated block as big units with the diagonal, timber as grain;
boards, tiles, a poured sheet and a pile for the four finish families, with the
spacing and the overlay changing for each of the twenty so no two are drawn the
same. **No new colours** — the brand palette allows none — so what separates
them is pattern: direction, spacing, and what is laid over.

The same idea runs at two sizes. The square beside the word in the settings
strip and the panel is the same hatch that now fills the wall itself on the
plan, clipped to the merged outline so it stops at the faces and inside every
doorway. The hatch is measured in **pixels, not millimetres**, on purpose: it is
a convention for reading the drawing rather than a thing in the building, so it
stays the same weight at every zoom. Hatching in model units gives a solid block
zoomed out and three lines zoomed in.

---

## The plan kept zooming itself back out

*"Oto zoomlama gibi bir sorun var."* It was, and it was made two rounds ago by
the interface re-layout.

`PlanViewport` threw the user's view away on every resize:

```kotlin
.onSizeChanged { newSize -> if (newSize != surface) { surface = newSize; viewport = null } }
```

`viewport = null` means "fall back to the fitted view", which was written for
the device being turned. On the old layout the canvas almost never changed size:
the tool rail was a fixed column and each tool's settings unfolded *inside* it.
On the new one the canvas resizes constantly — the settings strip appears under
the tools the moment a tool with settings is picked, and the layer panel slides
out beside the sheet. Every one of those is a resize, and every resize zoomed the
plan back out to fit. Somebody working at 1:20 on a doorway lost their place
each time they changed tool.

The fix is `Viewport2D.resized`, which keeps the scale and keeps whatever was in
the middle of the old surface in the middle of the new one. A view the user has
never touched still fits, because there is nothing of theirs to keep.

Worth recording for its own sake: the bug was not in the code that was changed.
The re-layout did not touch the viewport at all — it changed how often the
viewport's existing behaviour fired, from twice a session to twice a minute.

## Phase D — the library

The things that are drawn the same way every time. A bed is 1600 x 2000 with
pillows across the head, a WC is a shape everybody recognises, a basin is a
rectangle with a circle in it. None is worth drawing by hand twice, and every
one is worth drawing the **same** twice.

### A project stores which piece, not a copy of it

`DrawnShape.Block` holds a `CatalogueBlock` — an enum value — plus where it
stands, which way it faces and, optionally, a size of its own. The geometry
lives in code. That is the whole design decision: a project saved today gets the
better drawing of a WC that lands next year, and a library of two hundred pieces
costs a project file two hundred words rather than two hundred drawings.

Twenty-two pieces in five drawers — oturma, yemek, yatak odası, mutfak, banyo.
Each is plain rectangles and circles about its own middle: a plan is read at
1:50 and a sofa with its cushions drawn in is a grey smudge. What has to be
right is the space it takes up and whether you can tell what it is at a glance.

A test checks every piece is exactly as wide and as deep as it claims and is
centred on its own origin — and it immediately caught the WC drawn 590mm deep
while telling everyone it was 700. That is the kind of error nobody sees on a
screen and everybody sees when the door will not shut.

Furniture is **outlines, never filled**: filled shapes on this plan mean
building — walls, columns, rounded corners — and a filled sofa would read as a
structure. It offers nothing to snap a wall onto either, since a wall that
caught on a sofa would be a wall nobody could draw past one.

Turning is by angle rather than by moving points, so a sofa turned to face the
other way is the same sofa at 180 degrees and has not drifted across the room.
Columns got the same treatment, which is what makes a 30x60 column useful.

### The twenty wall types are presets, not types

The specification's twenty wall types are in as `WallType`, and deliberately
**not** as twenty classes. `DrawnShape.Wall` already carries a material and a
thickness; a "type" is one tap that sets them both, plus a `function` —
partition, load-bearing, shear, retaining, parapet — which is a real property of
a real wall and is what the schedules of phase G will be made of.

A preset is only ever a label. What gets drawn is the thickness and the
material, and setting either of those by hand clears the preset, because one
that still claims to be selected after its numbers were changed is a preset
that is lying. A wall from the catalogue lands on exactly the same layer as one
built from the loose buttons: one wall, one layer, whichever door it came
through.

---

## 3D, brought forward

The owner: *"Şuanlık mimari işlemleri dondur. Derhal 3d görüntüleme eklentilerini
aktif hale getir."*

There are two things that could mean, and they are very different jobs.

**Phase J as written** is Filament, glTF, and a 3D *file* viewer — for models
made somewhere else. It needs the NDK, a native renderer and an asset pipeline,
and when it is finished it still shows nothing the owner has drawn, because
PAFTA has no glTF reader either. It is weeks of work ending in a demo of
somebody else's teapot.

**The building the owner has already drawn**, standing up, is a different thing
entirely — and it was almost free, because *PAFTA has been storing the third
dimension since the very first wall*. A wall has a height. A door has a height
and a sill. A column has a height, a slab has a thickness and a level, and the
furniture catalogue knows a wardrobe is 2m and a bath is 550. None of it had
ever appeared anywhere, because a plan is a horizontal cut and a height does
not show in one. All that was missing was somewhere to look at it from.

So that is what was built, and phase J's file viewer stays where it was.

### No renderer, no native code, no licence question

`Camera3D` in `core:geometry` is an orbit camera — a target, a distance, a
compass bearing and a height — that projects a model point onto the glass and
says how far away it was. `Model3D` in `core:project` turns shapes into flat
faces. The viewport sorts them back to front and fills them in that order.

That is the oldest trick in the subject and it is exactly right here. A house
is a few hundred flat faces, none of them passing through another — walls meet
at their faces, a floor stops at the wall it meets. A depth buffer and a
graphics pipeline would buy nothing and cost a native dependency, a licence
question, and the thing this project values most: **every piece of the
arithmetic is in `core` and has tests**, on a machine with no screen.

### The openings are the whole job

A wall with a door in it is not a rectangle with a door painted on. It is the
piece of wall before the door, the piece after it, the lintel over it — and for
a window, the wall under the sill as well — plus the reveals you see looking
into the opening, which are what give a doorway depth instead of leaving a hole
with no sides. Two doors in one wall is five pieces a side.

`Model3DTest` checks a doorway is actually open (no face spans it below head
height), that a window has wall under it, that a door as tall as its wall gets
jambs but no head, and that an opening wider than its own wall does not push
the wall out past its own ends.

Faces are wound so every one looks outwards, whichever way round the hand drew
the shape — otherwise half of everything on a plan comes out inside-out and is
shaded as though lit from within. One fixed light, high and over the left
shoulder, which is where architectural drawings have been lit from for two
hundred years.

### The mistake worth keeping

The first version of `toFaces` did all its work inside `buildList`:

```kotlin
public fun List<DrawnShape>.toFaces(): List<Face3> = buildList {
    val walls = filterIsInstance<DrawnShape.Wall>()   // ← the WRONG list
    ...
}
```

Inside `buildList`, `this` is the list being built. Most of this project's own
extensions need a `List<DrawnShape>` and so cannot bind to it — but
`filterIsInstance` is declared on `Iterable<*>`, which a `MutableList<Face3>`
satisfies perfectly. So it bound to the output, filtered the wrong collection,
returned nothing, and **compiled and ran without a word**. It stood up an empty
building.

Thirteen tests failed at once, which is how it was found in seconds rather than
on the device. `tools/check-strings.py` now refuses a loose `filterIsInstance`
inside a `buildList` — it must name what it is filtering — and the check was
verified by putting the exact mistake back.

---

## Five formats asked for; three of them open

*"Dwg, skp, stl, dxf ve obj dosyalarını açacak düzenlemeyi yap."*

Those five are not one job. They are three jobs of very different sizes, and
being straight about which is which matters more than the code does.

| | Today |
| --- | --- |
| **DXF** | already opened; nothing needed |
| **STL** | **opens now** — pure Kotlin reader, no dependency |
| **OBJ** | **opens now** — pure Kotlin reader, no dependency |
| **DWG** | **opens now** for every version but AutoCAD 2007 — R13, R14, 2000, 2004, 2010, 2013, 2018. PAFTA's own reader, pure Kotlin, no dependency. See *Phase F*. |
| **SKP** | **no free library exists.** Trimble reads its own format only through its own paid SDK. This is not a matter of effort |

### STL and OBJ were nearly free, because the viewer already existed

Both are lists of flat faces with no units and no scene tree, which is exactly
what last round's 3D view draws. So `core:mesh` is a new pure-Kotlin module with
two readers and no dependency on anything.

STL has one real trap and it is the kind that bites once: the format comes in a
text spelling and a binary one, and **a binary file is allowed to begin with the
word `solid`**, which is also how every text file begins. Sniffing the first
five bytes is the mistake everybody makes; a binary file written by a program
that put its own name in the header then reads as text and yields an empty
model from a perfectly good file. So the *size* decides instead: a binary STL is
exactly 84 bytes plus 50 a triangle, and nothing else lands on that number by
accident.

OBJ's three traps are all handled and all tested: the point numbers start at
**one**; a **negative** number counts back from the end of what has been defined
so far; and `7`, `7/2`, `7//3` and `7/2/3` all mean point seven. Faces are kept
as written rather than cut into triangles — the viewer fills any flat outline,
so a four-sided face stays one face, which is fewer to sort and a tidier
picture.

Neither format records units, and nothing here guesses at them. The view frames
whatever it is given, so a model exported in metres and one exported in
millimetres are both the right size on screen.

A model file can be far bigger than this renderer can draw: it sorts every face
back to front, once per frame. The reader stops at forty thousand faces and the
screen says how many the file actually held — which is better than reading it
all and crawling, and far better than showing part of it silently.

### A model is a reference, not a drawing

An imported model opens on its own screen with no tools on it. Nothing there
edits anything, which is the same rule the rest of PAFTA keeps: the file the
architect brought in is never rewritten. You turn it round, and you hide the
parts in the way — the names a file gives its own parts (`o`, `g` in OBJ) become
the palette, which is the one thing anybody actually wants to do with somebody
else's model.

### What DWG would take, said plainly

LibreDWG is the only free library that reads DWG. It is C, it is GPL-3.0 (which
this project has already accepted), and using it from Android means
cross-compiling it for four processor types and writing the bindings — none of
which can be built or tested in the container this project is developed in. It
is phase I, it is real work, and it is honest to say that rather than to start
it badly.

Until then the route that works today is in the app, in the message a DWG shows
when it is opened: AutoCAD's own "Save As → DXF", or the free **ODA File
Converter** on a computer, which converts a whole folder of DWG to DXF at once.

For SKP the message now says the true thing — no free library exists — and
points at SketchUp's own `File → Export → 3D Model`, choosing **OBJ** or
**STL**, both of which PAFTA opens as of this build.

---

## The converter, and the honest edge of it

*"Açılması mümkün olmayan dosyalar için ve dwg modelleri için uygulama içine
otomatik converter kur."*

There is no single converter to install. There are formats that can be turned
into flat faces in plain Kotlin, and formats that cannot — and the line between
them is not about effort.

**What now converts, automatically, with nothing asked of the user:** STL, OBJ,
**PLY**, **glTF** and **GLB**. The conversion happens at the moment the project
is opened, in `openAsModel`, and there is no button for it because there should
not be one: "open this file" should always have meant this.

PLY is the scanner format, and its design is the nicest of the lot — a
plain-text header that lists exactly what is in the file and in what order, then
the file. A reader that does what the header says works on files it has never
seen, including the colour and normal fields it has no use for, which are
stepped over by the header's own description of them rather than guessed at.
Text, little-endian binary and big-endian binary are all read.

glTF/GLB is the one worth most, because it is what everything exports today —
Blender, SketchUp, Revit, Rhino, every web tool. It is JSON saying where the
numbers are, plus the numbers, and the work is in following that properly: a
mesh points at accessors, an accessor at a slice of a buffer with its own byte
offset and possibly a stride, and a buffer is either the binary chunk behind the
JSON or base64 inside it. Node transforms are followed down the scene tree —
translation, a quaternion rotation and scale, or a 4x4 matrix — so a model of
many parts comes out assembled rather than piled on the origin. A scene that
refers to itself is walked once.

Materials, textures, animation, cameras and skins are not read, deliberately:
the viewer fills flat faces, so anything richer would be parsed, stored and
quietly ignored.

### Where the converter stops, and why

A `.gltf` may keep its numbers in a `.bin` file beside it. PAFTA imports **one**
file and copies it into the project, which is what makes a project keep working
after the original is moved or deleted — so there is no second file to go and
find later. That is now its own failure, `NEEDS_ANOTHER_FILE`, with a message
that says which single-file format to ask the exporter for instead.

**DWG cannot be converted in the app today, and saying otherwise would be a
lie.** The only free library that reads it is LibreDWG: C, GPL-3.0, and usable
from Android only by cross-compiling it for four processor types through the NDK
and writing the bindings. None of that can be built or tested in the container
this project is developed in, and a binary-format parser written without a
single real file to test against is a parser that is wrong. It is phase I, it is
real work, and it needs a round of its own.

**SKP cannot be converted at all.** Trimble reads its own format only through
its own paid SDK, and the licence rule in `CLAUDE.md` rules that out. This one
is not a matter of effort and will not change.

Both messages in the app now say the true thing and point at the route that
works today: ODA File Converter or AutoCAD's own Save As for DWG, and
`File → Export → 3D Model` choosing OBJ, STL or GLB for SketchUp.

### A test that keeps the list and the code honest

`every format the converter claims it can read, it can` walks the formats marked
readable and actually imports and opens a sample of each. A format marked
readable with no entry in the converter would be a file that imports, opens, and
shows nothing — which is the exact failure this project has spent the most time
stamping out.

---

## "Hiçbiri açılmıyor" — one real crash, one real gap, and a way to tell

The owner updated to yapım 44 and reported that none of the formats opened. Two
things came out of looking rather than guessing, and a third was built so that
this question never has to be asked blind again.

### A single bad coordinate took the whole app down

`Camera3D` is built with `require(distanceMm > 0.0 && isFinite)`, which is right
for geometry PAFTA writes itself. `Camera3D.framing` computed that distance from
the model's extent — and a model file PAFTA did not write **is allowed to
contain anything**, including `nan` where a coordinate should be. One of those
anywhere in a file made the extent not-a-number, which made the distance
not-a-number, which threw.

Not an error message: a crash, on the frame the model first appeared. The test
that finds it is three lines and should have been written the day the camera
was. It is fixed at both ends — `framing` now treats every number as suspect and
falls back rather than throwing, and the readers drop any face with a corner
that is not a number at the door, through one shared `addIfReal`.

### A file is now recognised by what is in it

Android hands over whatever display name the app the file came from chose to
give it. A download arrives as `model(1)`, a cloud drive hands over no extension
at all, a file gets renamed by hand. `FileFormat.of(fileName)` returning null
then refused a perfectly good model over its name — which is exactly the kind of
failure nobody can diagnose from the outside, and exactly the kind that produces
"none of them open".

`FileFormat.sniff` now asks the bytes: `glTF` in the first four for GLB, the
84-plus-50-a-triangle size rule for binary STL (checked *before* the text rules,
because a binary STL may begin with the word `solid` too), `ply`, JSON with an
`asset` key for glTF, `SECTION` with `HEADER` or `ENTITIES` for DXF, and both a
`v` line and an `f` line for OBJ, which has no header at all. The name still
wins when it says something; sniffing is the fallback, not a vote. A DWG is
deliberately not sniffed — recognising it faster would not make it openable.

### Three real files, in the repository and in the owner's hands

Every other test in this module builds its sample inline, which proves the
reader against *what the test author believed the format was*. `RealFileTest`
reads three whole files of a small house — four walls, a floor slab, a pitched
roof — as OBJ, STL and GLB, through the same import-and-open path the tablet
runs, and checks all three describe a house 6m by 4m with its ridge at 4.3m.

The same three files went to the owner. That is the point of them: with a file
that is *known* to work, "it does not open on my tablet" and "it does not open
at all" stop being the same report.

### What this does not fix

Nothing here makes DWG or SKP open, and the owner's own files are almost
certainly those two. That remains true and remains said plainly.

---

## "Bu DXF açıldı ama içinde çizilecek bir şey yok: 3DSOLID (30)"

The owner imported `rozet.dxf` and got exactly that. It is the most useful
message this project has ever produced, because it names what is in the file —
and what is in the file is thirty ACIS solids.

That message was right and the file is fine. PAFTA's DXF reader knew eight
entity types, all of them flat: line, point, polyline, circle, arc, text,
insert. A DXF written by a 3D program contains none of those.

### Three kinds of 3D now come through

**`3DFACE`** is the one that matters most: a flat face with three or four
corners, anywhere in space. It is how most programs write a 3D model to DXF.

**`SOLID`** (and `TRACE`) is its flat cousin, a filled quad — and it numbers its
corners in a Z rather than round the edge, so read literally the shape comes out
as a bow tie. Its last two corners are swapped on the way in.

**Polyface meshes** are how 3D reached DXF before `3DFACE` was common, and they
are the subtle one. A `POLYLINE` with bit 64 set is not a run of points at all:
it is a list of corners followed by a list of which corners make each face, both
written as `VERTEX` records and told apart by another flag. Read as an ordinary
polyline — which is what PAFTA did — a model becomes a scribble joining every
point in it to the next. The reader now checks the flag first.

A triangle is written in DXF as a quad with its last corner repeated, by every
program that writes one. That repeat is dropped, so a triangle comes back a
triangle.

### The file decides which view opens

A drawing that turns out to hold faces opens **showing the model**, not the
plan. The plan of a 3D model is the shadow it casts on the ground, which is not
what somebody who opened a 3D file came to see. Both views still work: the
switch is there, and a wall drawn by hand stands next to the imported geometry
in the same 3D view.

The faces are found once, when the file opens, and carried on the document.
Finding them means walking every entity, and the owner already has a 6.2MB
drawing in their library — doing that on the way to the screen would have meant
doing it again on every frame.

### What this does not fix, and what would

**`3DSOLID` is still not read, and `rozet.dxf` will still report thirty of
them.** An ACIS solid is a boundary representation — faces bounded by trimmed
analytic surfaces — stored as its own encoded format inside the DXF. Pulling
flat polygons out of one is a real piece of work, and it is work that **cannot
be done without the file**: the encoding has to be confirmed against actual
bytes, and a binary-format reader written blind is a reader that is wrong.

So the ask is specific now, and it is small: **`rozet.dxf` itself.** With it,
this becomes a solvable problem rather than a guess.


---

## Phase F — DWG, read in our own code

### The mistake that held this up for three sessions

Every previous answer about DWG said the same thing: it needs LibreDWG, a C
library cross-compiled through the NDK, and a binary format cannot be
reverse-engineered without a real file — so send one.

**The second half of that was false, and it was the expensive half.** This
container can fetch files over HTTPS. The LibreDWG project publishes a test set
of AutoCAD files covering every DWG version ever written, and the same drawings
exported to DXF. That is a specification and an answer sheet, available the
whole time. Three rounds were spent asking the owner for a file that was never
needed.

The lesson is not about DWG. It is: **before reporting that something cannot be
done without the owner, check whether it can be done without the owner.**

### What DWG actually is

Not a format — a family of them, and a file of bits rather than bytes.

- The first six bytes name the AutoCAD version. **R13, R14 and 2000** share one
  layout. **2004 onwards** replaced it entirely with compressed pages.
- A number is packed at whatever bit the previous one ended on, and the common
  cases are shortened: a scale of 1.0 is two bits, not eight bytes; a polyline
  vertex close to the one before it carries only the bytes that changed.
- Near the end of the file is a table of every object with its handle and where
  it starts. It is kept in runs; **each run counts from zero again**, and each
  run ends with two bytes of checksum that are not part of it. Read as though
  the runs continued one another, two thirds of the file points at nothing.
- The handle steps in that table are **unsigned**; the position steps are
  **signed**. Reading both as signed drifts the handles by whatever the first
  step over 63 was — in the test file, by 178 — and every layer lookup then
  lands on the wrong object.
- **Every entity type has its own field order, and the order changed in 2000.**
  A line stores its two ends interleaved from 2000 and plainly before it. Text
  stores its height as a plain number where everything around it is shortened.
  A lightweight polyline writes its vertices in full before 2000 and as
  differences from 2000. None of this is guessable; all of it is checkable.
- **The entity types are not even fixed.** DWG numbers the types it has always
  had — a line is 19 — but everything added since R13 is declared in a table at
  the front of the file and numbered by whatever that table says. The same
  lightweight polyline is 535 in one file and 538 in another. Without reading
  that table, the entity most plans are drawn with is an unknown number in half
  the files in the world.

### How it was made trustworthy

Every object repeats its own handle a few fields in. Checking it against the
handle the table gave is what makes a misread table harmless: an entry pointing
into the middle of something else is dropped rather than drawn as nonsense.
**In all three test files, 100% of objects validate** — 5785, 832 and 750.

Then each entity type was confirmed two ways:

1. **Against the DXF export of the same drawing**, coordinate by coordinate. All
   68 lines in the AutoCAD 2000 file match to the last digit; so do the text
   strings, their heights and their rotations, the polyline vertices, and all
   ten block references including one with a scale of 3256 and a rotation of
   311°.
2. **Against the end of the entity.** Every object says how many bits it is. A
   reader that gets a field order wrong ends somewhere else. Arcs, circles,
   ellipses, points, filled quads, faces and polylines all end **exactly** on
   the bit the object says they do.

That second check is the one that found the real answers. The published
descriptions of `TEXT` and `MTEXT` did not match the files; the bit arithmetic
said where the fields actually were.

### What it reads

Lines, points, circles, arcs, ellipses, lightweight and classic polylines,
polyface meshes, 3D faces, filled quads, single-line text, paragraph text, and
block references — which are expanded into their geometry, nested, with the same
depth and budget limits the DXF reader uses. Layers come out with their real
names.

The result is a `DxfDrawing`: the same value the DXF reader produces. So
everything already built — drawing, measuring, snapping, the layer palette, the
3D view — works on a DWG without knowing one from the other. That is why this
was a two-file change outside the reader itself.

**Proof it is right, not just self-consistent:** the R14 file and the 2000 file
are the same drawing saved by two AutoCAD versions with unrelated internal
layouts. They now produce **identical output** — same entity count, same kinds,
same coordinates to six decimals. A test asserts it.

### What it does not read, and what happens instead

- **AutoCAD 2004 and later.** Refused by name and year, with the fix: open the
  drawing in its own program, Save As, choose *AutoCAD 2000/LT2000 Drawing* or
  *AutoCAD 2000 DXF*. A thirty-second fix in the owner's own program, which is
  worth far more than a half-right drawing.
- **Dimensions.** A DWG dimension keeps its arrows and text in an anonymous
  block that nothing references directly; the lines are there, the entity that
  places them is not read yet.
- **Hatching, splines, 3D solids, tables, multileaders.** Counted in the file's
  own inventory, so a drawing made entirely of them says what it is made of
  instead of showing an empty sheet.
- A damaged file, or one cut off part way through, comes back as a refusal.
  Tests cut the file to a half, a third, a fifth and a twentieth, and flip every
  eleventh byte, and require that nothing throws.

### Two new guards before the build

Each cost a build or would have:

- **A module that exists but is not in the build.** Adding `core:dwg` without
  wiring it in fails nowhere in this container and fails the Android build
  minutes later. Both directions are now checked.
- **`= try { ... return ... }`.** Kotlin forbids a `return` in an expression
  body. Easy to write, costs a compile.

Both were verified by reintroducing the exact mistake and watching the check
catch it.


---

## Phase F, part two — AutoCAD 2004 and everything after it

The first part read the files with the old layout. This part reads the rest,
which is what the owner actually has: a drawing saved by any AutoCAD of the
last twenty years is a 2004-or-later file.

### What 2004 changed

Everything about the file, and nothing about the drawing.

A DWG up to 2000 is a plain file: a table of contents at a fixed place, then the
objects where it says they are. From 2004 it is a **container**. At byte 128 sits
a scrambled directory; it points at a map of pages; that points at a map of
sections; each section is spread over pages that are **compressed** and have to
be unpacked and stitched back together in the right order. Only then does
anything about the drawing exist.

Two sections matter: `AcDb:AcDbObjects`, which is every object, and
`AcDb:Handles`, the index into it — and that index turned out to be **the same
format as the table at the end of a 2000 file**. So did the objects. Once the
container was open, the reader from part one read the drawing with no changes at
all.

Three pieces had to be worked out:

- **The directory is obfuscated, not encrypted.** Each byte is combined with a
  run of numbers from a fixed arithmetic sequence, with no key. Twelve bytes of
  magic at its front are how we know it came out right.
- **The compression is undocumented.** It is a variant of LZ77 whose rules were
  learned from LibreDWG's implementation and written fresh here (said plainly in
  `docs/LICENCES.md`). The one subtlety that matters: a back-reference may reach
  back fewer bytes than it copies, so it must read back what it is writing as it
  goes. That is how a thousand identical bytes cost three — and copying from a
  snapshot instead gives output of the right length and the wrong content.
- **Each page's own header is scrambled too**, with a key made from where the
  page sits in the file. A page moved elsewhere stops reading, which is
  presumably the point.

### What 2007 and 2010 changed

These are changes to the objects, not the container.

- **2007 moved every string out of its object.** A layer name, a piece of text:
  where it used to sit is now nothing at all, and the strings live in a run at
  the very end of the object, as Unicode. The run's length is written
  *backwards* from the object's last bit, and one bit there says whether there
  is a run at all. A reader that misses this gets the geometry and loses every
  word in the drawing.
- **2010 changed how an object states its own size.** It now says how many of
  its bits are handles rather than how many are data, in a field not counted in
  its own size, and its type is written in a width that depends on the number.
- **Each release added flags to every entity** — material, shadow, three kinds
  of visual style, a data flag in 2013 — and each one is a bit that puts
  everything after it out of place if it is missed.
- **2007 also dropped two fields from every table entry.** Two bits, and they
  were enough to read a block's base point out of the middle of something else:
  every copy of that block landed two millimetres off, and the one placed at
  3256 times scale landed three and a half thousand units away. The four-version
  comparison is what caught it.

### 2007 is the one version that does not open

Alone among them, AutoCAD 2007 wrapped the container in an error-correcting code
of its own, and the release after it dropped that again. It is refused by name
and year, with the fix: save as any other version.

That is a deliberate stopping point, not an oversight. It is one release out of
eight, from 2007, and the alternative was more work than every other version
cost put together.

### How this was made trustworthy

The same way as part one, and then harder.

**Every object validates.** Each one repeats its own handle a few fields in; the
reader checks it against the index. On the 2004 file that is 735 of 735; on 2010,
536 of 536; on 2013, 481 of 481; on 2018, 474 of 474.

**Every entity ends where it says it does.** Each object states its own length in
bits. Arcs, circles, ellipses, points, faces, filled quads and polylines all
finish on exactly the bit the object says — in all four files. That check is what
found the field orders the published descriptions got wrong.

**Four releases, one drawing, identical output.** The test files are the same
drawing saved by R14, 2000, 2004 and 2018 — four unrelated file layouts, from a
plain file with a table of contents to compressed pages with the text held
separately as Unicode. All four now produce the same 143 entities, the same
kinds, the same coordinates to six decimals, and the same words. A test asserts
it, and it is the single most useful test in the module: it is what caught the
two-bit table change, and it would catch any future version's surprises the
same way.

**Coordinates checked against DXF.** All 68 lines in all four files match the
DXF export of the same drawing to the last digit, as do the text contents, their
heights and their rotations.

### One real fix along the way

Up to AutoCAD 2000 a polyline's points sit directly behind it in the file, which
is how both the DXF reader and part one of this one find them. **From 2004 they
do not** — they are scattered. But every point says which polyline owns it, in
every version, so that is what the reader goes by now, with the old
walk-behind-it as the fallback. Before that, a 2018 file lost its 3D polyline
and its mesh silently.
