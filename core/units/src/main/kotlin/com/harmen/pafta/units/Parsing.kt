package com.harmen.pafta.units

/**
 * Parses a user-typed length into millimetres.
 *
 * Accepted forms (whitespace optional, decimal comma allowed):
 *   `5500`          -> interpreted in [defaultUnit]
 *   `5500mm`, `5.5 m`, `550cm`
 *   `18"`, `18in`, `6ft`, `6'`
 *   `18' 6"`, `6'6"`, `6' 6 1/2"`   (feet + inches, optional inch fraction)
 *   `1/2"`                          (bare fraction)
 *
 * Returns `null` when the text cannot be understood, so callers can keep the
 * user's input in the field and show an inline error rather than guessing.
 */
public fun parseLengthToMillimetres(
    text: String,
    defaultUnit: LengthUnit = LengthUnit.MILLIMETRE,
): Double? {
    val raw = text.trim().replace(',', '.')
    if (raw.isEmpty()) return null

    val negative = raw.startsWith("-")
    val body = raw.removePrefix("-").removePrefix("+").trim()
    if (body.isEmpty()) return null

    val mm = parseFeetInches(body) ?: parseSimple(body, defaultUnit) ?: return null
    return if (negative) -mm else mm
}

/** `6' 6 1/2"`, `18'`, `6'6"` — returns null when no feet/inches marker is present. */
private fun parseFeetInches(body: String): Double? {
    val footIdx = body.indexOf('\'')
    val hasFoot = footIdx >= 0
    val hasInch = body.contains('"')
    if (!hasFoot && !hasInch) return null

    var feet = 0.0
    var rest = body
    if (hasFoot) {
        feet = body.substring(0, footIdx).trim().toDoubleOrNull() ?: return null
        rest = body.substring(footIdx + 1).trim()
    }
    rest = rest.removeSuffix("\"").trim()
    val inches = if (rest.isEmpty()) 0.0 else parseMixedFraction(rest) ?: return null
    return feet * LengthUnit.FOOT.millimetresPerUnit + inches * LengthUnit.INCH.millimetresPerUnit
}

/** `5500`, `5.5m`, `550 cm`, `18in` */
private fun parseSimple(body: String, defaultUnit: LengthUnit): Double? {
    val split = body.indexOfFirst { it.isLetter() }
    if (split < 0) {
        val v = parseMixedFraction(body) ?: return null
        return defaultUnit.toMillimetres(v)
    }
    val number = body.substring(0, split).trim()
    val suffix = body.substring(split).trim()
    val unit = LengthUnit.fromSymbol(suffix) ?: return null
    val v = parseMixedFraction(number) ?: return null
    return unit.toMillimetres(v)
}

/** `6`, `6.5`, `1/2`, `6 1/2` */
private fun parseMixedFraction(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(' ', '\t').filter { it.isNotBlank() }
    return when (parts.size) {
        1 -> parseScalarOrFraction(parts[0])
        2 -> {
            val whole = parts[0].toDoubleOrNull() ?: return null
            val frac = parseFractionOnly(parts[1]) ?: return null
            whole + frac
        }
        else -> null
    }
}

private fun parseScalarOrFraction(token: String): Double? =
    if (token.contains('/')) parseFractionOnly(token) else token.toDoubleOrNull()

private fun parseFractionOnly(token: String): Double? {
    val bits = token.split('/')
    if (bits.size != 2) return null
    val n = bits[0].trim().toDoubleOrNull() ?: return null
    val d = bits[1].trim().toDoubleOrNull() ?: return null
    if (d == 0.0) return null
    return n / d
}
