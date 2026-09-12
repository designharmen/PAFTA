package com.harmen.pafta.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Harmen Design colour system, as the brand guideline defines it.
 *
 * Six fixed brand colours whose HEX values do not change, and nothing else: the
 * guideline permits only controlled tint / shade / opacity variations derived
 * from those six, and forbids adding a new principal colour. Every value below
 * is either one of the six or a stated derivation of one.
 *
 * The dark scheme follows the guideline's own reading of a dark surface:
 * graphite ground, ivory text, warm greige for secondary text (the guideline
 * rates greige on a dark ground at 8.1:1 and names it "koyu zeminde ikincil
 * metin"), bronze reserved for lines, icons and rules, and deep navy used only
 * as an accent surface and selected state — never as the ground of the whole
 * screen.
 */
public object HarmenColours {

    // --- The six brand colours ---------------------------------------------
    /** Koyu Grafit — PRIMARY DARK. */
    public val Graphite: Color = Color(0xFF2B2B2B)

    /** Fil Dişi — PRIMARY LIGHT. */
    public val Ivory: Color = Color(0xFFF7F7F7)

    /** Warm Greige — SECONDARY NEUTRAL. */
    public val Greige: Color = Color(0xFFD6CFC7)

    /** Pasifik Gece Mavisi — TECHNOLOGY / STRATEGIC ACCENT. */
    public val Navy: Color = Color(0xFF233447)

    /** Toprak Tonu Bronz — PREMIUM ACCENT. */
    public val Bronze: Color = Color(0xFF96785F)

    /** Neutral Gray — SUPPORTING NEUTRAL. */
    public val NeutralGray: Color = Color(0xFF6E6E6E)

    // --- Grounds: shades of graphite ---------------------------------------
    /** The application ground: Koyu Grafit itself. */
    public val Ground: Color = Graphite

    /** The drawing canvas — graphite at 82%, so the viewport reads as a surface. */
    public val Canvas: Color = Color(0xFF232323)

    /** Side and top panels — graphite lifted 5% toward ivory. */
    public val Panel: Color = Color(0xFF363636)

    /** Raised rows inside a panel — graphite lifted 10%. */
    public val PanelRaised: Color = Color(0xFF404040)

    // --- Accent -------------------------------------------------------------
    /**
     * Bronze, for lines, icons, rules and the active underline.
     *
     * The guideline caps bronze at 3.4:1 on ivory and forbids it as text below
     * 24px or in captions, so nothing in PAFTA sets small text in bronze. It
     * draws; it does not label.
     */
    public val Accent: Color = Bronze

    /** Bronze at 85%, for pressed states. */
    public val AccentDeep: Color = Color(0xFF806550)

    /** Bronze at 12%, as the wash behind an active control. */
    public val AccentWash: Color = Color(0x1F96785F)

    /**
     * Deep navy as the selected-state surface.
     *
     * "Gece mavisi yalnızca vurgu yüzeyi ve seçili durum" is the guideline's
     * rule for digital interfaces, with a cap of 25% of the surface — which a
     * selected tab and a selected tool stay far inside.
     */
    public val Selected: Color = Navy

    /** Navy at 55%, where the selection has to sit over the panel tone. */
    public val SelectedWash: Color = Color(0x8C233447)

    // --- Text ---------------------------------------------------------------
    /** Primary text: Fil Dişi on graphite — the guideline's main pair, 13.6:1. */
    public val Text: Color = Ivory

    /** Secondary text on a dark ground: Warm Greige, 8.9:1 on graphite. */
    public val TextMuted: Color = Greige

    /**
     * Third level: hints, disabled entries, units.
     *
     * Neutral Gray sits at 2.7:1 on graphite, so it is used only where the text
     * is genuinely optional — never for anything the user has to read.
     */
    public val TextFaint: Color = NeutralGray

    // --- Lines --------------------------------------------------------------
    /** Hairline borders: greige at 18%. */
    public val Hairline: Color = Color(0x2ED6CFC7)

    /** Drawing linework on the canvas: Fil Dişi. */
    public val Linework: Color = Ivory

    /** Secondary linework: hidden edges, construction lines. */
    public val LineworkFaint: Color = Greige

    /** The drawing grid: greige at 7%. */
    public val Grid: Color = Color(0x12D6CFC7)

    /** Every tenth grid line: greige at 16%, so the grid reads at a glance. */
    public val GridMajor: Color = Color(0x29D6CFC7)
}
