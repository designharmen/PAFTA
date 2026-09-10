package com.harmen.pafta.units

import java.util.Locale

/**
 * Length units PAFTA can display and parse.
 *
 * Millimetres are the internal canonical unit: every model coordinate is
 * stored in millimetres so that integer-ish architectural values (120, 2900,
 * 5500) stay exact and conversions happen only at the UI boundary.
 */
public enum class LengthUnit(
    /** How many millimetres one unit of this measure spans. */
    public val millimetresPerUnit: Double,
    /** Suffix shown in the UI, e.g. `5500mm`. */
    public val symbol: String,
) {
    MILLIMETRE(1.0, "mm"),
    CENTIMETRE(10.0, "cm"),
    METRE(1000.0, "m"),
    INCH(25.4, "\""),
    FOOT(304.8, "'"),
    ;

    /** Converts [millimetres] into this unit. */
    public fun fromMillimetres(millimetres: Double): Double = millimetres / millimetresPerUnit

    /** Converts [value], expressed in this unit, into millimetres. */
    public fun toMillimetres(value: Double): Double = value * millimetresPerUnit

    public companion object {
        /**
         * Resolves a unit from a user- or file-supplied token such as `mm`,
         * `cm`, `m`, `in`, `inch`, `"`, `ft`, `feet`, `'`. Case-insensitive.
         */
        public fun fromSymbol(token: String): LengthUnit? =
            when (token.trim().lowercase(Locale.ROOT)) {
                "mm", "millimetre", "millimeter", "millimetres", "millimeters" -> MILLIMETRE
                "cm", "centimetre", "centimeter", "centimetres", "centimeters" -> CENTIMETRE
                "m", "metre", "meter", "metres", "meters" -> METRE
                "in", "inch", "inches", "\"" -> INCH
                "ft", "foot", "feet", "'" -> FOOT
                else -> null
            }
    }
}

/** Angular display units. */
public enum class AngleUnit(public val symbol: String) {
    DEGREES("°"),
    RADIANS(" rad"),
    GRADIANS(" gon"),
    ;

    public fun fromRadians(radians: Double): Double = when (this) {
        DEGREES -> Math.toDegrees(radians)
        RADIANS -> radians
        GRADIANS -> radians * 200.0 / Math.PI
    }

    public fun toRadians(value: Double): Double = when (this) {
        DEGREES -> Math.toRadians(value)
        RADIANS -> value
        GRADIANS -> value * Math.PI / 200.0
    }
}

/** Area units, derived from [LengthUnit]. */
public enum class AreaUnit(public val base: LengthUnit, public val symbol: String) {
    SQUARE_MILLIMETRE(LengthUnit.MILLIMETRE, "mm²"),
    SQUARE_CENTIMETRE(LengthUnit.CENTIMETRE, "cm²"),
    SQUARE_METRE(LengthUnit.METRE, "m²"),
    SQUARE_INCH(LengthUnit.INCH, "in²"),
    SQUARE_FOOT(LengthUnit.FOOT, "ft²"),
    ;

    /** Converts an area given in square millimetres into this unit. */
    public fun fromSquareMillimetres(squareMillimetres: Double): Double {
        val f = base.millimetresPerUnit
        return squareMillimetres / (f * f)
    }

    public fun toSquareMillimetres(value: Double): Double {
        val f = base.millimetresPerUnit
        return value * f * f
    }

    public companion object {
        /** The square unit matching a linear [unit]. */
        public fun of(unit: LengthUnit): AreaUnit = when (unit) {
            LengthUnit.MILLIMETRE -> SQUARE_MILLIMETRE
            LengthUnit.CENTIMETRE -> SQUARE_CENTIMETRE
            LengthUnit.METRE -> SQUARE_METRE
            LengthUnit.INCH -> SQUARE_INCH
            LengthUnit.FOOT -> SQUARE_FOOT
        }
    }
}
