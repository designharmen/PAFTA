package com.harmen.pafta.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.harmen.pafta.R

/**
 * PAFTA's type system.
 *
 * Two families, fixed for the life of the project:
 *
 *  - **Inter** (SIL OFL 1.1) for every label and heading. Light and regular
 *    weights carry the interface; 600 appears only in the project title.
 *  - **JetBrains Mono** (SIL OFL 1.1) for anything numeric — dimensions,
 *    coordinates, property values — so that `5500mm` and `4500mm` line up
 *    column-for-column in the properties table.
 *
 * Both are bundled in `res/font`, so the app needs no font download at runtime
 * and renders identically offline.
 */
public object HarmenType {

    public val Sans: FontFamily = FontFamily(
        Font(R.font.inter_300, FontWeight.Light),
        Font(R.font.inter_400, FontWeight.Normal),
        Font(R.font.inter_500, FontWeight.Medium),
        Font(R.font.inter_600, FontWeight.SemiBold),
    )

    public val Mono: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_400, FontWeight.Normal),
        Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    )

    /** The project name in the centre of the top bar. */
    public val ProjectTitle: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 0.2.sp,
    )

    /**
     * `FILE` `EDIT` `VIEW` — upper case, widely tracked. The letter-spacing is
     * what makes these read as menu headings rather than body text.
     */
    public val MenuCaps: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
    )

    /** Panel headings such as `[Layers Palette]`. */
    public val SectionTitle: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    )

    /** Tab labels in the second top-bar row. */
    public val Tab: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    )

    /** Tool rail captions under each icon. */
    public val ToolLabel: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 9.5.sp,
        letterSpacing = 0.3.sp,
    )

    /** Body text inside panels: layer names, material names. */
    public val Body: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
    )

    /** Property keys: `Wall`, `Length`, `Height`. */
    public val PropertyKey: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 11.5.sp,
    )

    /** Property values and dimension text: `120mm`, `5500mm`, `2900mm`. */
    public val Numeric: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Dimension labels drawn on the canvas, in the accent colour. */
    public val DimensionLabel: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
    )

    /** Room names on the plan: thin, upper case, widely tracked, low opacity. */
    public val RoomLabel: TextStyle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        letterSpacing = 3.sp,
    )

    /** The status read-out in the canvas corners. */
    public val Status: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp,
    )
}
