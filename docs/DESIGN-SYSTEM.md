# Harmen Design — as implemented in PAFTA

The source of truth is the brand guideline *HARMEN DESIGN — Master Color +
Typography System, V1.1 · 2026*. This document records how that system lands in
the app, and — just as importantly — every place where the app deviates from it
and why.

Before this, PAFTA's palette and type were derived from a reference image rather
than from the brand. They carried the brand's name without its values. That is
now corrected: the six brand colours and the three brand typefaces, with nothing
outside them.

## Colour — `app/.../ui/theme/Colour.kt`

The guideline fixes six colours, forbids adding a seventh, and permits only
controlled tint / shade / opacity variations of those six.

| Brand name | Token | Value | Role in the guideline |
| --- | --- | --- | --- |
| Koyu Grafit | `Graphite` | `#2B2B2B` | PRIMARY DARK |
| Fil Dişi | `Ivory` | `#F7F7F7` | PRIMARY LIGHT |
| Warm Greige | `Greige` | `#D6CFC7` | SECONDARY NEUTRAL |
| Pasifik Gece Mavisi | `Navy` | `#233447` | TECHNOLOGY / STRATEGIC ACCENT |
| Toprak Tonu Bronz | `Bronze` | `#96785F` | PREMIUM ACCENT |
| Neutral Gray | `NeutralGray` | `#6E6E6E` | SUPPORTING NEUTRAL |

Everything else in the file is a stated derivation:

| Token | Value | Derived how | Used for |
| --- | --- | --- | --- |
| `Ground` | `#2B2B2B` | Koyu Grafit itself | the application ground |
| `Canvas` | `#232323` | graphite at 82% | the drawing surface |
| `Panel` | `#363636` | graphite lifted 5% toward ivory | top bar, tool rail, inspector |
| `PanelRaised` | `#404040` | graphite lifted 10% | rows and fields inside a panel |
| `Accent` | `#96785F` | bronze | lines, icons, rules, the active underline |
| `AccentDeep` | `#806550` | bronze at 85% | pressed states |
| `AccentWash` | bronze @ 12% | opacity variation | wash behind an active control |
| `Selected` / `SelectedWash` | `#233447` / navy @ 55% | navy | the selected state |
| `Text` | `#F7F7F7` | ivory | primary text — 13.6:1 on graphite |
| `TextMuted` | `#D6CFC7` | greige | secondary text — 8.9:1 on graphite |
| `TextFaint` | `#6E6E6E` | Neutral Gray | hints and disabled only — 2.7:1 |
| `Hairline` | greige @ 18% | opacity variation | 1px borders, the monogram box |
| `Linework` | `#F7F7F7` | ivory | drawing lines on the canvas |
| `Grid` / `GridMajor` | greige @ 7% / 16% | opacity variation | grid, and every tenth line |

### The two rules that changed the interface

**Bronze draws; it does not label.** The guideline caps bronze at 3.4:1 on ivory
and forbids it as text below 24px or in captions. PAFTA had bronze on the active
tab label, the selected tool caption, the selected material name, the format
chip, the dismiss action and every dimension value on the canvas — all far under
that floor. Bronze now appears only as line, icon, outline, rule and wash.

**Navy is the selected state.** With bronze off the text, selection needed a
carrier, and the guideline supplies one for exactly this case: *"Gece mavisi
yalnızca vurgu yüzeyi ve seçili durum"*, capped at 25% of the surface. A
selected tab, tool, preset and material row now sit on navy with ivory text, and
the bronze rule stays beneath the active tab.

### Deviations, stated

- **The app is dark.** The guideline's drawing-sheet pairing asks for a Warm
  Greige or Fil Dişi ground, which is right for a printed sheet; PAFTA is an
  on-device viewer where a light ground washes out hairline linework, and the
  guideline's own *WEB & UI* pairing permits the dark reading. The whole palette
  is a token swap away from a light scheme if the brand owner prefers one.
- **Grounds are derived shades**, not raw brand colours. The guideline allows
  this explicitly; each derivation is written down in the table above so it
  cannot drift.

## Typography — `app/.../ui/theme/Type.kt`

Three families, exactly the guideline's Option A, all SIL OFL and all bundled in
`app/src/main/res/font` so the app downloads nothing and renders identically
offline:

- **Archivo** — display, headings, labels, navigation, buttons.
- **IBM Plex Sans** — body, descriptions, captions, interface text.
- **IBM Plex Mono** — technical information, sheet labels, coordinates, numbers.

The nine bundled files are fixed instances cut from the upstream variable fonts
at the weights the guideline names, so no weight outside 300–500 can be reached
by accident and Archivo 700+ — ruled out by name in the guideline — is not in the
build at all. Every file was checked for the guideline's Turkish test
(ı / İ / Ğ / Ş) before it was added.

`archivo_condensed_400.ttf` is cut at width 88, which is the guideline's
drawing-sheet instruction (*"Archivo wdth 88 condensed başlık"*) and the reason
the brand chose a family with a width axis at all. Room names on the plan use it.

| Style | Guideline level | Face | Size | Tracking |
| --- | --- | --- | --- | --- |
| `ProjectTitle` | H4 | Archivo 500 | 17sp | 0 |
| `MenuCaps` | Navigation | Archivo 400 | 12sp | 0.08em |
| `SectionTitle` | Label | Archivo 500 | 11sp | 0.14em |
| `Tab` | Navigation | Archivo 400 | 12sp | 0.08em |
| `ToolLabel` | Label (reduced) | Archivo 500 | 9.5sp | 0.06em |
| `Body` | Body Small | Plex Sans 400 | 13sp | 0.005em |
| `PropertyKey` | Body Small | Plex Sans 300 | 12.5sp | 0.005em |
| `Numeric` | Technical Information | Plex Mono 400 | 11.5sp | 0.02em |
| `DimensionLabel` | Technical Information | Plex Mono 500 | 10.5sp | 0.04em |
| `RoomLabel` | drawing-sheet label | Archivo Condensed 400 | 12sp | 0.22em |
| `Status` | Technical Information | Plex Mono 400 | 10sp | 0.06em |

Tracking is expressed in `em` exactly as the guideline states it, so changing a
size cannot silently break the ratio.

**Deviation, stated:** the guideline's Label level is 11px at 0.14em. At that
size and tracking `Katmanlar` does not fit the 64dp tool rail, so `ToolLabel`
keeps the family and weight and reduces only size and tracking — the smallest
change that keeps the word readable.

### The guard

`tools/check-strings.py` fails the build if a forbidden typeface (Inter, Roboto,
Montserrat, Poppins — named in the guideline's DON'T list), or any family
outside the three, appears in `res/font`; and if Kotlin references a font file
that is not there. Both checks were verified by deliberately introducing the
mistake.

## Layout

### Top bar, two rows — `ui/chrome/TopBar.kt`

- **Row 1** (44dp): `P` monogram at the left — a square with a 1px bronze border
  on the ground colour, holding a single centred letter — then `DOSYA` `DÜZENLE`;
  the project name centred by weight so it stays centred whatever flanks it;
  `PAYLAŞ` as a hairline-outlined button at the right.
- **Row 2** (36dp): `DÜZENLE` / `GÖRÜNÜM` at the left; the tab group
  (`Aktif` `Notlar` `Mobilya` `Duvarlar` `Izgara`) centred. The active tab gets
  a navy surface **and** a 2dp bronze underline across its own width — the
  surface alone is too quiet, the underline alone reads as a progress bar.

### Tool rail — `ui/chrome/ToolRail.kt`

Icon over caption, one column, 64dp wide (52dp and icon-only below 720dp).
Order: Seç, Kalem, Çizgi, Yay, Ölçü, Ölçüler, Tarama, Metin, Izgara, Ölç, Palet,
Katmanlar. The selected tool takes a navy surface, a bronze icon and ivory text.

Two tools expand inline when active, because neither can act without a value
first: **Ölçüler** shows its length presets in the mono face, and **Metin** shows
the last string typed so it can be stamped again without retyping.

### Inspector — `ui/chrome/RightPanel.kt`

232dp, hidden below 600dp rather than squeezed. Four bracketed sections:
`[Katman Paleti]`, `[Malzeme Seçici]`, `[Özellikler]`, `[Not Araçları]`. A layer
with no colour override shows a bronze *outline* instead of a filled chip, so
"inherits from the file" is visibly different from "set to a colour". A hidden
layer keeps its percentage but drops to faint, so the user can see what it would
come back as.

### Canvas — `ui/viewport/PlanViewport.kt`

Ivory linework on the graphite canvas, bronze dimension lines with arrow heads,
and dimension values in ivory on a knocked-out ground — the value has to be
readable at a glance, and bronze text at 10sp is out of bounds. Room names sit
in condensed Archivo at 45% opacity. The file name is bottom-left, an
orientation compass bottom-right with its north needle in bronze.

Everything is drawn from model millimetres through one `Viewport2D`, so a pinch
or drag changes a single transform and the linework, dimensions and labels stay
registered to each other at any zoom. Two details that matter in practice: the
grid is skipped entirely once its spacing falls below 4px, where it degrades
into a flat wash, and arc tessellation follows the on-screen radius so a
zoomed-in curve stays smooth without over-segmenting a small one.
