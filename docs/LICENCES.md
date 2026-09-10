# Third-party components and licences

Project constraint: **only MIT / BSD / Apache-2.0 / LGPL / GPL / AGPL.** No
commercial SDK, no subscription, no account required to build or ship.

## In the build today

| Component | Licence | Use |
| --- | --- | --- |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | language and runtime |
| AndroidX core / activity / lifecycle / documentfile | Apache-2.0 | platform integration |
| Jetpack Compose (ui, foundation, material3, material-icons-extended) | Apache-2.0 | UI |
| **Inter** | SIL OFL 1.1 | interface typeface (`app/src/main/res/font/inter_*.ttf`) |
| **JetBrains Mono** | SIL OFL 1.1 | numeric typeface (`app/src/main/res/font/jetbrains_mono_*.ttf`) |

Both font families are bundled as TrueType, converted from the upstream Google
Fonts web builds. SIL OFL 1.1 permits bundling and redistribution in an
application; the reserved-font-name clause is respected by keeping the original
names.

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
