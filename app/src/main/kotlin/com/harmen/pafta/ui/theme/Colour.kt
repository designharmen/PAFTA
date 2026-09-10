package com.harmen.pafta.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Harmen Design palette.
 *
 * One accent colour and a ladder of neutrals — that discipline is the whole
 * look. Nothing in PAFTA introduces a second hue: state is carried by the
 * copper accent, by opacity, and by weight.
 */
public object HarmenColours {

    // --- Grounds ------------------------------------------------------------
    /** The application ground, behind everything. */
    public val Ground: Color = Color(0xFF1A1A1A)

    /** The drawing canvas, a hair lighter so the viewport reads as a surface. */
    public val Canvas: Color = Color(0xFF1E1E1E)

    /** Side and top panels. */
    public val Panel: Color = Color(0xFF242424)

    /** Raised rows inside a panel: selected layers, pressed tools, fields. */
    public val PanelRaised: Color = Color(0xFF2A2A2A)

    // --- Accent: copper / rose-gold ----------------------------------------
    /** Active tab underline, selected tool, dimension lines, selection. */
    public val Accent: Color = Color(0xFFC97D5D)

    /** The deeper end of the accent, for pressed states and fills. */
    public val AccentDeep: Color = Color(0xFFB8694F)

    /** A 12% accent wash, for the fill behind an active tab. */
    public val AccentWash: Color = Color(0x1FC97D5D)

    // --- Text ---------------------------------------------------------------
    /** Primary text: broken white, never pure #FFFFFF. */
    public val Text: Color = Color(0xFFE8E4DE)

    /** Secondary text, labels, inactive tabs. */
    public val TextMuted: Color = Color(0xFF9A9A9A)

    /** Third-level text: hints, units, disabled entries. */
    public val TextFaint: Color = Color(0xFF6A6A6A)

    // --- Lines --------------------------------------------------------------
    /** Hairline borders: panel edges, the monogram box, field outlines. */
    public val Hairline: Color = Color(0xFF343434)

    /** Drawing linework on the canvas. */
    public val Linework: Color = Color(0xFFE8E4DE)

    /** Secondary linework: hidden edges, construction lines. */
    public val LineworkFaint: Color = Color(0xFF7E7A76)

    /** The drawing grid. */
    public val Grid: Color = Color(0xFF2C2C2C)

    /** Every tenth grid line, so the grid reads at a glance. */
    public val GridMajor: Color = Color(0xFF383838)
}
