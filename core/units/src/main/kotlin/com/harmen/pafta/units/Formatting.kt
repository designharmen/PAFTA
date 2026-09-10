package com.harmen.pafta.units

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a numeric length is rendered in the UI.
 *
 * @param unit the unit to display in
 * @param decimals digits after the decimal separator
 * @param trimTrailingZeros drop `.0` / `.50` noise so labels stay short
 * @param spaceBeforeSymbol `5500 mm` instead of `5500mm`
 */
public data class LengthFormat(
    val unit: LengthUnit = LengthUnit.MILLIMETRE,
    val decimals: Int = 0,
    val trimTrailingZeros: Boolean = true,
    val spaceBeforeSymbol: Boolean = false,
) {
    init {
        require(decimals in 0..6) { "decimals must be in 0..6, was $decimals" }
    }

    public companion object {
        /** The drawing default: whole millimetres, as in the reference design. */
        public val MILLIMETRES: LengthFormat = LengthFormat(LengthUnit.MILLIMETRE, decimals = 0)

        /** Metres with two decimals, e.g. `5.50m`. */
        public val METRES: LengthFormat = LengthFormat(LengthUnit.METRE, decimals = 2)
    }
}

/** Formats [millimetres] for display, e.g. `5500mm`. */
public fun formatLength(millimetres: Double, format: LengthFormat = LengthFormat.MILLIMETRES): String {
    val value = format.unit.fromMillimetres(millimetres)
    val body = formatNumber(value, format.decimals, format.trimTrailingZeros)
    val gap = if (format.spaceBeforeSymbol) " " else ""
    return "$body$gap${format.unit.symbol}"
}

/** Formats an area given in square millimetres, e.g. `24.40m²`. */
public fun formatArea(
    squareMillimetres: Double,
    unit: AreaUnit = AreaUnit.SQUARE_METRE,
    decimals: Int = 2,
    trimTrailingZeros: Boolean = false,
): String {
    val value = unit.fromSquareMillimetres(squareMillimetres)
    return formatNumber(value, decimals, trimTrailingZeros) + unit.symbol
}

/** Formats an angle given in radians, e.g. `90°`. */
public fun formatAngle(
    radians: Double,
    unit: AngleUnit = AngleUnit.DEGREES,
    decimals: Int = 1,
    trimTrailingZeros: Boolean = true,
): String {
    val value = unit.fromRadians(radians)
    return formatNumber(value, decimals, trimTrailingZeros) + unit.symbol
}

/**
 * Formats [millimetres] in imperial feet-and-inches notation, e.g. `18' 0 1/2"`.
 * Inch fractions are rounded to [fractionDenominator]ths.
 */
public fun formatFeetInches(millimetres: Double, fractionDenominator: Int = 16): String {
    require(fractionDenominator > 0 && (fractionDenominator and (fractionDenominator - 1)) == 0) {
        "fractionDenominator must be a power of two, was $fractionDenominator"
    }
    val sign = if (millimetres < 0) "-" else ""
    val totalInches = abs(millimetres) / LengthUnit.INCH.millimetresPerUnit
    var feet = (totalInches / 12.0).toInt()
    var inches = totalInches - feet * 12.0

    var numerator = (inches % 1.0 * fractionDenominator).roundToInt()
    var whole = inches.toInt()
    if (numerator == fractionDenominator) {
        whole += 1
        numerator = 0
    }
    if (whole == 12) {
        feet += 1
        whole = 0
    }

    var den = fractionDenominator
    while (numerator != 0 && numerator % 2 == 0) {
        numerator /= 2
        den /= 2
    }

    val inchPart = when {
        numerator == 0 -> "$whole\""
        // Only drop a zero inch column when there is no feet column in front of
        // it: `1/2"` on its own, but `18' 0 1/2"` when feet are shown.
        whole == 0 && feet == 0 -> "$numerator/$den\""
        else -> "$whole $numerator/$den\""
    }
    return if (feet == 0) "$sign$inchPart" else "$sign$feet' $inchPart"
}

private fun formatNumber(value: Double, decimals: Int, trimTrailingZeros: Boolean): String {
    var s = String.format(Locale.ROOT, "%.${decimals}f", value)
    if (trimTrailingZeros && s.contains('.')) {
        s = s.trimEnd('0').trimEnd('.')
    }
    // A tiny negative value rounds to "-0" / "-0.00"; drop the sign once it has
    // rounded away, otherwise a dimension reads "-0mm".
    if (s.startsWith("-") && s.drop(1).all { it == '0' || it == '.' }) {
        s = s.drop(1)
    }
    return s
}
