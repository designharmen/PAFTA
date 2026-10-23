# Third-party components and licences

Project constraint: **only MIT / BSD / Apache-2.0 / LGPL / GPL / AGPL.** No
commercial SDK, no subscription, no account required to build or ship.

## In the build today

| Component | Licence | Use |
| --- | --- | --- |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | language and runtime |
| AndroidX core / activity / lifecycle / documentfile | Apache-2.0 | platform integration |
| Jetpack Compose (ui, foundation, material3, material-icons-extended) | Apache-2.0 | UI |
| **Archivo** | SIL OFL 1.1 | display, headings, labels, navigation (`res/font/archivo_*.ttf`) |
| **IBM Plex Sans** | SIL OFL 1.1 | body and interface text (`res/font/plex_sans_*.ttf`) |
| **IBM Plex Mono** | SIL OFL 1.1 | technical information and numbers (`res/font/plex_mono_*.ttf`) |

These three are the Harmen Design brand typefaces, and the guideline chose them
partly for this reason: it states plainly that the system needs no paid font and
carries no licence burden. SIL OFL 1.1 permits bundling, embedding and
redistribution in an application; the reserved-font-name clause is respected by
keeping the original names.

The bundled files are fixed instances cut from the upstream variable fonts
(`google/fonts`) at the weights the guideline names — Archivo 300/400/500 plus a
width-88 condensed cut, Plex Sans 300/400/500, Plex Mono 400/500. Instancing is
a permitted modification under the OFL; the names are unchanged.

**Removed:** Inter and JetBrains Mono, which the earlier palette used. Both are
OFL and were perfectly legal to ship — they were dropped because the brand
guideline names Inter among the forbidden typefaces, not for any licence
reason.

## Declared in the catalogue, wired up in later phases

| Component | Licence | Phase |
| --- | --- | --- |
| Google Filament + gltfio + filament-utils | Apache-2.0 | 3D renderer. Published to **Maven Central**, so it needs no extra repository. |
| Room / SQLite | Apache-2.0 | project database |

## DWG is read in PAFTA's own code, and carries no licence

AutoCAD's DWG is read by `core:dwg`, written for this project in Kotlin. It
depends on nothing, and it therefore adds **no licence obligation at all** — the
GPL decision that the DWG phase was waiting on turned out not to be needed for
reading. The reader covers AutoCAD R13, R14 and 2000; files from AutoCAD 2004
onwards are recognised and refused by their year.

**Test files.** `core/dwg/src/test/resources/autocad-*.dwg` are AutoCAD files
from the test set of the **LibreDWG** project (`LibreDWG/libredwg`,
**GPL-3.0**). They are test material only: they are not in the application, not
shipped in the APK, and nothing links against LibreDWG. PAFTA is distributed
under GPL-3.0 in any case, so their presence in the repository is consistent
with the project's own licence.

## Planned, not yet added

| Component | Licence | Note |
| --- | --- | --- |
| Assimp | BSD-3-Clause | OBJ / STL / PLY / DAE / 3DS import — needs the NDK |
| IfcOpenShell | LGPL-3.0 | IFC / BIM. LGPL: keep it as a separately linked library and do not statically fold it into the app. |
| LibreDWG | GPL-3.0 | **No longer needed for reading DWG** — see below. It would still be the route to *writing* DWG, and to the versions from AutoCAD 2004 onwards if the Kotlin reader does not get there. |
| CuraEngine | AGPL-3.0 | 3D-print slicing. AGPL's network clause is the reason it must stay an on-device, non-networked component. |

The AGPL entry is the only remaining licence risk in the plan. It is acceptable
under the constraint as written, but it sets the licence of the finished
application, so the slicing phase should start by confirming that is intended.
The DWG phase no longer needs that confirmation: nothing was linked.
