package com.harmen.pafta.dwg

/**
 * Reads a DWG file bit by bit.
 *
 * DWG is not a file of bytes. A drawing's numbers are packed at whatever bit the
 * previous number happened to end on, and the common cases are shortened: a
 * length of 1.0 is two bits rather than eight bytes, and a coordinate that
 * repeats the one before it carries only the bytes that changed. That is why
 * this reader counts in bits and why every value below has its own method —
 * each one is a different encoding, not a different size.
 *
 * The names are the ones the format's own documentation uses ([bitShort],
 * [bitDouble], [modularShort] and so on), kept deliberately so that this file
 * can be read next to that documentation.
 */
internal class DwgBitReader(private val data: ByteArray, bitPosition: Int = 0) {

    var position: Int = bitPosition
        private set

    val bitLength: Int get() = data.size * 8

    fun seek(bit: Int) {
        position = bit
    }

    fun skip(bits: Int) {
        position += bits
    }

    /** True while [count] more bits are inside the file. */
    fun has(count: Int): Boolean = position >= 0 && position + count <= bitLength

    fun bit(): Int {
        val index = position ushr 3
        if (index >= data.size) throw DwgTruncated()
        val byte = data[index].toInt() and 0xFF
        position++
        return (byte ushr (7 - (position - 1 and 7))) and 1
    }

    fun bits(count: Int): Int {
        var value = 0
        repeat(count) { value = (value shl 1) or bit() }
        return value
    }

    /** One bit, as a flag. */
    fun boolean(): Boolean = bit() == 1

    /** Two bits, the type tag in front of most shortened numbers. */
    fun twoBits(): Int = bits(2)

    /** A raw byte. */
    fun rawChar(): Int = bits(8)

    /** A raw 16-bit integer, little-endian. */
    fun rawShort(): Int {
        val lo = bits(8)
        val hi = bits(8)
        return lo or (hi shl 8)
    }

    /** A raw 32-bit integer, little-endian. */
    fun rawLong(): Int {
        val a = bits(8)
        val b = bits(8)
        val c = bits(8)
        val d = bits(8)
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }

    /** A raw 64-bit floating point number, little-endian. */
    fun rawDouble(): Double {
        var raw = 0L
        for (i in 0 until 8) raw = raw or (bits(8).toLong() shl (i * 8))
        return Double.fromBits(raw)
    }

    /** A shortened 16-bit integer: full, one byte, zero, or 256. */
    fun bitShort(): Int = when (twoBits()) {
        0 -> rawShort()
        1 -> bits(8)
        2 -> 0
        else -> 256
    }

    /** A shortened 32-bit integer: full, one byte, or zero. */
    fun bitLong(): Int = when (twoBits()) {
        0 -> rawLong()
        1 -> bits(8)
        2 -> 0
        else -> throw DwgMalformedValue()
    }

    /** A shortened double: full, 1.0, or 0.0. */
    fun bitDouble(): Double = when (twoBits()) {
        0 -> rawDouble()
        1 -> 1.0
        2 -> 0.0
        else -> throw DwgMalformedValue()
    }

    /**
     * A double stored as the difference from [default].
     *
     * This is how DWG keeps a polyline small: a vertex a few millimetres from
     * the last one shares every high byte with it, so only the low bytes are
     * written.
     */
    fun bitDoubleWithDefault(default: Double): Double = when (twoBits()) {
        0 -> default
        1 -> {
            var raw = default.toRawBits()
            for (i in 0 until 4) {
                raw = (raw and (0xFFL shl (i * 8)).inv()) or (bits(8).toLong() shl (i * 8))
            }
            Double.fromBits(raw)
        }
        2 -> {
            var raw = default.toRawBits()
            for (i in 4 until 6) {
                raw = (raw and (0xFFL shl (i * 8)).inv()) or (bits(8).toLong() shl (i * 8))
            }
            for (i in 0 until 4) {
                raw = (raw and (0xFFL shl (i * 8)).inv()) or (bits(8).toLong() shl (i * 8))
            }
            Double.fromBits(raw)
        }
        else -> rawDouble()
    }

    /** A thickness: one bit when it is zero, which it almost always is. */
    fun bitThickness(): Double = if (boolean()) 0.0 else bitDouble()

    /**
     * An extrusion direction: one bit when it points straight up, which is the
     * case for every entity drawn on a plan.
     */
    fun bitExtrusion(): Triple<Double, Double, Double> =
        if (boolean()) {
            Triple(0.0, 0.0, 1.0)
        } else {
            Triple(bitDouble(), bitDouble(), bitDouble())
        }

    /** A length-prefixed integer, used for sizes. */
    fun modularShort(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val word = rawShort()
            if (word and 0x8000 != 0) {
                value = value or ((word and 0x7FFF) shl shift)
                shift += 15
                if (shift > 30) throw DwgMalformedValue()
            } else {
                return value or (word shl shift)
            }
        }
    }

    /**
     * Text.
     *
     * The byte count includes the terminating zero in files from AutoCAD R14
     * onwards and does not in R13, so the zero is trimmed rather than counted.
     * The bytes are interpreted as Windows-1254, which is the Turkish code page:
     * a Turkish layer name written by a Turkish AutoCAD arrives correct, and a
     * file in another code page still reads, with only its accented letters
     * wrong. DWG before 2007 does not record which code page it used anywhere
     * this reader can trust.
     */
    fun text(): String {
        val count = bitShort()
        if (count <= 0) return ""
        if (!has(count * 8)) throw DwgTruncated()
        val bytes = ByteArray(count) { bits(8).toByte() }
        val end = if (bytes.isNotEmpty() && bytes[bytes.size - 1] == 0.toByte()) bytes.size - 1 else bytes.size
        return String(bytes, 0, end, TURKISH_CODE_PAGE)
    }

    /**
     * A reference to another object.
     *
     * The low nibble says how many bytes the number takes; the high nibble says
     * how to read it — as it stands, or counted from the handle of the object
     * being read. [DwgReader] resolves the second kind.
     */
    fun handle(): DwgHandleRef {
        val code = bits(4)
        val count = bits(4)
        var value = 0L
        repeat(count) { value = (value shl 8) or bits(8).toLong() }
        return DwgHandleRef(code, value)
    }
}

/** A raw handle reference as it appears in the file. */
internal data class DwgHandleRef(val code: Int, val value: Long)

/** The file ended in the middle of something. */
internal class DwgTruncated : RuntimeException(null, null, false, false)

/** A value used an encoding the format does not define. */
internal class DwgMalformedValue : RuntimeException(null, null, false, false)

/**
 * The code page DWG text is read in, when the device has it.
 *
 * Windows-1254 is the Turkish code page, so a Turkish layer name written by a
 * Turkish AutoCAD arrives correct. Asking a device for a code page it does not
 * carry throws, and this is a value built once when the class loads — so a
 * device without it would fail *every* DWG, reporting a damaged file for a
 * perfectly good drawing. The fallback keeps every drawing opening and costs
 * only the accents.
 */
private val TURKISH_CODE_PAGE: java.nio.charset.Charset = try {
    charset("windows-1254")
} catch (e: Exception) {
    Charsets.ISO_8859_1
}

/** An unsigned length-prefixed integer: seven bits a byte, low byte first. */
internal fun DwgBitReader.unsignedModularChar(): Long {
    var value = 0L
    var shift = 0
    while (true) {
        val b = rawChar()
        value = value or ((b and 0x7F).toLong() shl shift)
        shift += 7
        if (b and 0x80 == 0) return value
        if (shift > 56) throw DwgMalformedValue()
    }
}

/**
 * An object type as AutoCAD 2010 and later write it.
 *
 * Two bits say how wide the number is, because by 2010 a drawing could hold
 * types numbered past a byte and most files still only used the first few.
 */
internal fun DwgBitReader.objectType(): Int = when (twoBits()) {
    0 -> bits(8)
    1 -> bits(8) + 0x1F0
    else -> rawShort()
}

/** A length-prefixed integer: three bits of byte count, then the bytes. */
internal fun DwgBitReader.bitLongLong(): Long {
    val count = bits(3)
    var value = 0L
    repeat(count) { value = (value shl 8) or rawChar().toLong() }
    return value
}

/**
 * Text as AutoCAD 2007 and later store it: a count of characters, then that
 * many sixteen-bit characters.
 *
 * This is the one encoding in DWG that needs no guessing about code pages — it
 * is Unicode, so a Turkish layer name is a Turkish layer name.
 */
internal fun DwgBitReader.unicodeText(): String {
    val count = bitShort()
    if (count <= 0) return ""
    if (!has(count * 16)) throw DwgTruncated()
    val out = StringBuilder(count)
    repeat(count) {
        val c = rawShort()
        if (c != 0) out.append(c.toChar())
    }
    return out.toString()
}
