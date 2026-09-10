# Harmen Design — as implemented

The visual specification, and where each value lives in code. The rule that
governs everything: **one accent colour, and neutrals.** State is carried by the
copper accent, by opacity, and by type weight — never by introducing a second
hue.

## Palette — `app/.../ui/theme/Colour.kt`

| Token | Value | Used for |
| --- | --- | --- |
| `Ground` | `#1A1A1A` | application ground |
| `Canvas` | `#1E1E1E` | the drawing surface |
| `Panel` | `#242424` | top bar, tool rail, inspector |
| `PanelRaised` | `#2A2A2A` | rows and fields inside a panel |
| `Accent` | `#C97D5D` | active tab, selected tool, dimension lines, selection |
| `AccentDeep` | `#B8694F` | pressed states |
| `AccentWash` | `#C97D5D` @ 12% | fill behind an active tab or tool |
| `Text` | `#E8E4DE` | primary text — broken white, never `#FFFFFF` |
| `TextMuted` | `#9A9A9A` | secondary text, inactive tabs |
| `TextFaint` | `#6A6A6A` | hints, hidden layers, disabled |
| `Hairline` | `#343434` | 1px borders, the monogram box |
| `Linework` | `#E8E4DE` | drawing lines on the canvas |
| `Grid` / `GridMajor` | `#2C2C2C` / `#383838` | grid, and every tenth line |

The warm grey → beige gradient from the reference image is a presentation frame
only. It is **not** in the application and appears nowhere in the code.

## Typography — `app/.../ui/theme/Type.kt`

Two families, fixed for the project, both bundled in `app/src/main/res/font` so
the app needs no font download and renders identically offline:

- **Inter** (SIL OFL 1.1) — weights 300/400/500/600, for every label and heading.
- **JetBrains Mono** (SIL OFL 1.1) — weights 400/500, for every number.

Numbers use the mono face so that `5500mm` and `4500mm` align column-for-column
in the properties table and dimension strings stay the same width at any value.

| Style | Face | Size | Tracking | Used for |
| --- | --- | --- | --- | --- |
| `ProjectTitle` | Inter 600 | 17sp | 0.2 | project name, monogram letter |
| `MenuCaps` | Inter 500 | 11sp | **1.6** | `FILE` `EDIT` `VIEW` `SHARE` |
| `SectionTitle` | Inter 500 | 11sp | 0.8 | `[Layers Palette]` |
| `Tab` | Inter 400 | 12sp | 0.4 | tab group labels |
| `ToolLabel` | Inter 300 | 9.5sp | 0.3 | tool rail captions |
| `Body` | Inter 400 | 12.5sp | — | layer and material names |
| `PropertyKey` | Inter 300 | 11.5sp | — | `Wall`, `Length`, `Height` |
| `Numeric` | JetBrains Mono 400 | 11.5sp | −0.2 | `120mm`, `5500mm` |
| `DimensionLabel` | JetBrains Mono 500 | 10sp | — | dimension text on the canvas |
| `RoomLabel` | Inter 300 | 12sp | **3.0** | `LIVING`, `KITCHEN`, `BATH` |
| `Status` | JetBrains Mono 400 | 10sp | 0.3 | corner read-outs |

The wide tracking on `MenuCaps` and `RoomLabel` is what makes those read as
drawing-sheet annotation rather than as body text.

## Layout

### Top bar, two rows — `ui/chrome/TopBar.kt`

- **Row 1** (44dp): `P` monogram at the left — a square with a 1px accent border
  on the ground colour, holding a single centred letter — then `FILE` `EDIT`;
  the project name centred by weight so it stays centred whatever flanks it;
  `SHARE` as a hairline-outlined button at the right.
- **Row 2** (36dp): `EDIT` / `VIEW` at the left; the tab group
  (`Active` `Annotations` `Furniture` `Walls` `Grid`) centred. The active tab
  gets **both** an accent wash and a 2dp accent underline drawn across its own
  width — the wash alone is too quiet on the panel, the underline alone reads as
  a progress bar.

### Tool rail — `ui/chrome/ToolRail.kt`

Icon over caption, one column, 64dp wide (52dp and icon-only below 720dp).
Order: Select, Pencil, Line, Arc, Dim, Dimensions, Hatch, Text, Grid, Measure,
Palette, Layers. The selected tool goes accent on an accent wash.

Two tools expand inline when active, because neither can act without a value
first: **Dimensions** shows its length presets (`3100mm` `4500mm` `4800mm`, in
the mono face), and **Text** shows the last string typed so it can be stamped
again without retyping.

### Inspector — `ui/chrome/RightPanel.kt`

232dp, hidden below 600dp rather than squeezed. Four bracketed sections, in
order:

- `[Layers Palette]` — colour chip, name, opacity percentage, and a four-step
  opacity track. A layer with no colour override shows an accent *outline*
  instead of a filled chip, so "inherits from the file" is visibly different
  from "set to copper". A hidden layer keeps its percentage but drops to faint,
  so the user can see what it would come back as.
- `[Material Selector]` — swatch square plus name.
- `[Properties]` — key/value rows; keys in Inter Light, values in the mono face.
- `[Annotation Tools]` — Text, Callout, Stamp, Comment, Arrow, Pin, Dimension.

### Canvas — `ui/viewport/PlanViewport.kt`

Light linework on the near-black ground, copper dimension lines with arrow
heads, room names in thin tracked caps at 45% opacity. `Unit 101 – Lvl 2` sits
bottom-left, an orientation compass bottom-right with its north needle in the
accent colour.

Everything is drawn from model millimetres through one `Viewport2D`, so a pinch
or drag changes a single transform and the linework, dimensions and labels stay
registered to each other at any zoom. Two details that matter in practice: the
grid is skipped entirely once its spacing falls below 4px, where it degrades
into a flat wash, and arc tessellation follows the on-screen radius so a
zoomed-in curve stays smooth without over-segmenting a small one.
