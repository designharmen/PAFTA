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

## Planned, not yet added

| Component | Licence | Note |
| --- | --- | --- |
| Assimp | BSD-3-Clause | OBJ / STL / PLY / DAE / 3DS import — needs the NDK |
| IfcOpenShell | LGPL-3.0 | IFC / BIM. LGPL: keep it as a separately linked library and do not statically fold it into the app. |
| LibreDWG | GPL-3.0 | DWG **reading**. GPL is viral — shipping it makes the whole application GPL-3.0. That is a deliberate decision to take before the DWG phase, not during it. |
| CuraEngine | AGPL-3.0 | 3D-print slicing. AGPL's network clause is the reason it must stay an on-device, non-networked component. |

The GPL and AGPL entries are the only licence risks in the plan. Both are
acceptable under the constraint as written, but they set the licence of the
finished application, so the DWG and slicing phases should start by confirming
that is intended.
