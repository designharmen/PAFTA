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

## Phase 1 — file manager and real import

- Document-picker import via `ACTION_OPEN_DOCUMENT`; copy into a `.pafta`.
- Project list backed by Room, built from `PaftaContainer.readManifest`.
- Wire the DXF viewer to an imported file instead of `SamplePlan`.
- Auto-save and undo/redo through `EditorViewModel`.
- **Exit criterion:** import a real DXF on a device and see it drawn.

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
