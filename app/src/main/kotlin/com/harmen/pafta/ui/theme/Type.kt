package com.harmen.pafta.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harmen.pafta.R

/**
 * PAFTA's type system, taken from the Harmen Design guideline.
 *
 * Three families, and the guideline permits no fourth:
 *
 *  - **Archivo** (SIL OFL) — display, headings, labels, navigation, buttons.
 *  - **IBM Plex Sans** (SIL OFL) — body, descriptions, captions, interface text.
 *  - **IBM Plex Mono** (SIL OFL) — technical information, drawing-sheet labels,
 *    scale, coordinates, metadata and numeric data.
 *
 * Weights stay in the 300–500 band the guideline asks for; Archivo 700+ is
 * ruled out by name, and hierarchy is built from scale and space rather than
 * from weight. Tracking is expressed in `em`, exactly as the guideline states
 * it, so a size change cannot silently break the ratio.
 *
 * The named-but-absent families matter too: Inter, Roboto, Montserrat and
 * Poppins are forbidden by the guideline, and `tools/check-strings.py` fails the
 * build if one reappears.
 *
 * All nine files are bundled in `res/font`, generated as fixed instances of the
 * upstream variable fonts, so the app downloads nothing at runtime and renders
 * identically offline. Every one passes the guideline's Turkish test
 * (ı / İ / Ğ / Ş).
 */
public object HarmenType {

    /** Archivo — the primary typeface. */
    public val Display: FontFamily = FontFamily(
        Font(R.font.archivo_300, FontWeight.Light),
        Font(R.font.archivo_400, FontWeight.Normal),
        Font(R.font.archivo_500, FontWeight.Medium),
    )

    /**
     * Archivo at width 88.
     *
     * The guideline's drawing-sheet rule — "Archivo wdth 88 condensed başlık" —
     * is why the width axis was chosen for this brand at all: the same family
     * gives both a wide architectural display and a tight sheet label. Room
     * names on the plan are exactly that second case.
     */
    public val Condensed: FontFamily = FontFamily(
        Font(R.font.archivo_condensed_400, FontWeight.Normal),
    )

    /** IBM Plex Sans — interface and body text. */
    public val Sans: FontFamily = FontFamily(
        Font(R.font.plex_sans_300, FontWeight.Light),
        Font(R.font.plex_sans_400, FontWeight.Normal),
        Font(R.font.plex_sans_500, FontWeight.Medium),
    )

    /** IBM Plex Mono — technical information and numbers. */
    public val Mono: FontFamily = FontFamily(
        Font(R.font.plex_mono_400, FontWeight.Normal),
        Font(R.font.plex_mono_500, FontWeight.Medium),
    )

    /** The project name in the centre of the top bar. Guideline level: H4. */
    public val ProjectTitle: TextStyle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = 0.em,
    )

    /**
     * `DOSYA` `DÜZENLE` `GÖRÜNÜM` `PAYLAŞ` — guideline level: Navigation.
     * Archivo 400, upper case, 0.08em. The tracking is what makes these read as
     * navigation rather than as body text.
     */
    public val MenuCaps: TextStyle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.08.em,
    )

    /** Panel headings such as `[Katman Paleti]`. Guideline level: Label. */
    public val SectionTitle: TextStyle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    )

    /** Tab labels in the second top-bar row. Guideline level: Navigation. */
    public val Tab: TextStyle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.08.em,
    )

    /**
     * Tool rail captions under each icon.
     *
     * A deliberate deviation, recorded rather than hidden: the guideline's Label
     * level is 11px at 0.14em, which does not fit `Katmanlar` inside a 64dp
     * rail. The family and weight are kept and only size and tracking are
     * reduced — the smallest change that keeps the word readable.
     */
    public val ToolLabel: TextStyle = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        letterSpacing = 0.06.em,
    )

    /** Layer and material names in the panels. Guideline level: Body Small. */
    public val Body: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.005.em,
    )

    /** Property keys: `Öğe sayısı`, `Genişlik`. Guideline level: Body Small. */
    public val PropertyKey: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 12.5.sp,
        letterSpacing = 0.005.em,
    )

    /**
     * Property values: `12000mm`, `5500mm`. Guideline level: Technical
     * Information — Plex Mono, which also keeps the digits tabular so a column
     * of millimetres lines up.
     */
    public val Numeric: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        letterSpacing = 0.02.em,
    )

    /** Dimension labels drawn on the canvas. Guideline level: Technical Information. */
    public val DimensionLabel: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.04.em,
    )

    /**
     * Room names on the plan: the guideline's drawing-sheet voice — condensed
     * Archivo, upper case, widely tracked, quiet against the linework.
     */
    public val RoomLabel: TextStyle = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.22.em,
    )

    /** The status read-out in the canvas corners. Guideline: Technical Information. */
    public val Status: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.06.em,
    )
}
