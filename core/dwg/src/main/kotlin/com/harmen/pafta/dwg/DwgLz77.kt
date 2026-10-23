package com.harmen.pafta.dwg

/**
 * Unpacks the compression AutoCAD 2004 introduced.
 *
 * From that release a DWG is no longer a file you can read in place: it is a set
 * of compressed pages, and nothing inside them is reachable until they are
 * unpacked. The scheme is a variant of LZ77 — a stream of two kinds of
 * instruction, "copy these bytes through" and "repeat what you wrote N bytes
 * ago" — with the lengths packed into the opcode byte wherever they fit.
 *
 * The one subtlety that matters: a repeat may reach back fewer bytes than it
 * copies, so it has to read back the bytes it is writing as it goes. That is
 * how a run of a thousand identical bytes costs three. Copying from a snapshot
 * instead produces output that is the right length and wrong.
 */
internal object DwgLz77 {

    /**
     * Unpacks [compressedSize] bytes at [from] into [decompressedSize] bytes.
     *
     * Returns as much as it could unpack, zero-padded: a page is allowed to
     * finish before its buffer is full, and a page whose stream is damaged
     * should cost that page rather than the drawing.
     */
    fun inflate(
        source: ByteArray,
        from: Int,
        compressedSize: Int,
        decompressedSize: Int,
    ): ByteArray {
        val out = ByteArray(decompressedSize)
        if (decompressedSize <= 0 || compressedSize <= 0) return out
        val end = minOf(from + compressedSize, source.size)
        if (from < 0 || from >= end) return out

        var at = from
        var written = 0

        fun byte(): Int {
            if (at >= end) throw DwgTruncated()
            return source[at++].toInt() and 0xFF
        }

        /** A length that did not fit in the opcode: 0xFF per extra byte. */
        fun extendedLength(low: Int, base: Int): Int {
            var total = low
            while (true) {
                val b = byte()
                if (b != 0) return total + b + base
                total += 0xFF
            }
        }

        fun literalLength(opcode: Int): Int {
            val low = opcode and 0x0F
            return if (low == 0) extendedLength(0, 0x0F) + 3 else low + 3
        }

        fun repeatLength(opcode: Int, mask: Int): Int {
            val low = opcode and mask
            return if (low == 0) extendedLength(0, mask) + 2 else low + 2
        }

        fun copyThrough(count: Int): Int {
            repeat(count) {
                if (written >= out.size) throw DwgTruncated()
                out[written++] = byte().toByte()
            }
            return byte()
        }

        try {
            var opcode = byte()
            if (opcode and 0xF0 == 0) {
                opcode = copyThrough(literalLength(opcode))
            }

            while (at < end && written < out.size && opcode != STREAM_END) {
                var offset: Int
                val length: Int
                if (opcode < 0x10 || opcode >= 0x40) {
                    length = (opcode shr 4) - 1
                    val second = byte()
                    offset = (((opcode shr 2) and 3) or (second shl 2)) + 1
                } else if (opcode < 0x20) {
                    length = repeatLength(opcode, 7)
                    offset = (opcode and 8) shl 11
                    val first = byte()
                    val second = byte()
                    offset = offset or (first shr 2) or (second shl 6)
                    offset += 0x4000
                    opcode = first
                } else {
                    length = repeatLength(opcode, 0x1F)
                    val first = byte()
                    val second = byte()
                    offset = (first shr 2) or (second shl 6)
                    offset += 1
                    opcode = first
                }

                if (length < 0 || offset <= 0 || offset > written) break
                if (written + length > out.size) break
                // Read back as it writes: see the note above.
                for (i in 0 until length) {
                    out[written] = out[written - offset]
                    written++
                }

                var literal = opcode and 3
                if (literal == 0) {
                    opcode = byte()
                    if (opcode and 0xF0 == 0) literal = literalLength(opcode)
                }
                if (literal > 0) {
                    if (written + literal > out.size) break
                    opcode = copyThrough(literal)
                }
            }
        } catch (e: DwgTruncated) {
            return out
        } catch (e: IndexOutOfBoundsException) {
            return out
        }
        return out
    }

    private const val STREAM_END = 0x11
}
