package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3

/**
 * PLY: what every 3D scanner writes, and what a lot of research tools read.
 *
 * A header in plain text that describes the file, then the data in whatever
 * form the header said — text, or binary one way round or the other. The header
 * is the format: it lists each kind of thing in the file, how many there are,
 * and what each one is made of, in order. A reader that does what the header
 * says works on files it has never seen, which is the whole point of the design.
 */
public object PlyReader {

    public fun read(bytes: ByteArray): MeshResult {
        val headerEnd = endOfHeader(bytes) ?: return MeshResult.Failure(MeshFailure.MALFORMED)
        val header = bytes.copyOfRange(0, headerEnd).toString(Charsets.US_ASCII)
        if (!header.trimStart().startsWith("ply")) {
            return MeshResult.Failure(MeshFailure.MALFORMED)
        }

        val layout = parseHeader(header) ?: return MeshResult.Failure(MeshFailure.MALFORMED)
        return when (layout.encoding) {
            Encoding.ASCII -> readAscii(
                bytes.copyOfRange(headerEnd, bytes.size).toString(Charsets.US_ASCII),
                layout,
            )

            Encoding.BINARY_LITTLE, Encoding.BINARY_BIG ->
                readBinary(bytes, headerEnd, layout)
        }
    }

    // --- the header ----------------------------------------------------------

    private enum class Encoding { ASCII, BINARY_LITTLE, BINARY_BIG }

    /** One field of one record: what it is called and how wide it is. */
    private data class Field(val name: String, val type: String, val listCountType: String?)

    private data class Element(val name: String, val count: Int, val fields: List<Field>)

    private data class Layout(val encoding: Encoding, val elements: List<Element>)

    /** The byte just past `end_header\n`, or null when there is no header. */
    private fun endOfHeader(bytes: ByteArray): Int? {
        val marker = "end_header".toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - marker.size) {
            for (j in marker.indices) if (bytes[i + j] != marker[j]) continue@outer
            var at = i + marker.size
            // Past the line ending, whichever of the three it is.
            while (at < bytes.size && (bytes[at] == '\r'.code.toByte())) at++
            if (at < bytes.size && bytes[at] == '\n'.code.toByte()) at++
            return at
        }
        return null
    }

    private fun parseHeader(header: String): Layout? {
        var encoding: Encoding? = null
        val elements = ArrayList<Element>()
        var fields = ArrayList<Field>()
        var name: String? = null
        var count = 0

        fun flush() {
            name?.let { elements += Element(it, count, fields.toList()) }
            fields = ArrayList()
        }

        for (raw in header.lineSequence()) {
            val parts = raw.trim().split(WHITESPACE)
            when (parts.firstOrNull()) {
                "format" -> encoding = when (parts.getOrNull(1)) {
                    "ascii" -> Encoding.ASCII
                    "binary_little_endian" -> Encoding.BINARY_LITTLE
                    "binary_big_endian" -> Encoding.BINARY_BIG
                    else -> return null
                }

                "element" -> {
                    flush()
                    name = parts.getOrNull(1) ?: return null
                    count = parts.getOrNull(2)?.toIntOrNull() ?: return null
                }

                "property" -> {
                    if (parts.getOrNull(1) == "list") {
                        // `property list uchar int vertex_indices`
                        fields += Field(
                            name = parts.getOrNull(4) ?: return null,
                            type = parts.getOrNull(3) ?: return null,
                            listCountType = parts.getOrNull(2),
                        )
                    } else {
                        fields += Field(
                            name = parts.getOrNull(2) ?: return null,
                            type = parts.getOrNull(1) ?: return null,
                            listCountType = null,
                        )
                    }
                }
            }
        }
        flush()
        return Layout(encoding ?: return null, elements)
    }

    // --- the two spellings of the data ---------------------------------------

    private fun readAscii(body: String, layout: Layout): MeshResult {
        val numbers = body.split(WHITESPACE).filter { it.isNotEmpty() }
        var at = 0
        val points = ArrayList<Vec3>()
        val faces = ArrayList<MeshFace>()
        var facesInFile = 0

        for (element in layout.elements) {
            repeat(element.count) {
                if (element.name == "vertex") {
                    val byName = HashMap<String, Double>(element.fields.size)
                    for (field in element.fields) {
                        if (at >= numbers.size) return MeshResult.Failure(MeshFailure.TRUNCATED_FILE)
                        if (field.listCountType != null) {
                            val n = numbers[at++].toIntOrNull() ?: 0
                            at += n
                        } else {
                            byName[field.name] = numbers[at++].toDoubleOrNull() ?: 0.0
                        }
                    }
                    points += Vec3(
                        byName["x"] ?: 0.0,
                        byName["y"] ?: 0.0,
                        byName["z"] ?: 0.0,
                    )
                } else if (element.name == "face") {
                    facesInFile++
                    val corners = ArrayList<Vec3>(4)
                    for (field in element.fields) {
                        if (at >= numbers.size) return MeshResult.Failure(MeshFailure.TRUNCATED_FILE)
                        if (field.listCountType == null) {
                            at++
                            continue
                        }
                        val n = numbers[at++].toIntOrNull() ?: 0
                        repeat(n) {
                            val index = numbers.getOrNull(at++)?.toIntOrNull() ?: -1
                            if (index in points.indices) corners += points[index]
                        }
                    }
                    if (corners.size >= 3 && faces.size < FACE_BUDGET) faces addIfReal MeshFace(corners)
                } else {
                    // Something this reader has no use for — colours, normals,
                    // a camera. Stepped over by the header's own description of
                    // it rather than guessed at.
                    for (field in element.fields) {
                        if (at >= numbers.size) break
                        if (field.listCountType != null) {
                            val n = numbers[at++].toIntOrNull() ?: 0
                            at += n
                        } else {
                            at++
                        }
                    }
                }
            }
        }
        return finish(faces, facesInFile)
    }

    private fun readBinary(bytes: ByteArray, start: Int, layout: Layout): MeshResult {
        val little = layout.encoding == Encoding.BINARY_LITTLE
        val cursor = Cursor(bytes, start, little)
        val points = ArrayList<Vec3>()
        val faces = ArrayList<MeshFace>()
        var facesInFile = 0

        for (element in layout.elements) {
            repeat(element.count) {
                if (!cursor.has(1)) return MeshResult.Failure(MeshFailure.TRUNCATED_FILE)
                when (element.name) {
                    "vertex" -> {
                        val byName = HashMap<String, Double>(element.fields.size)
                        for (field in element.fields) {
                            if (field.listCountType != null) {
                                val n = cursor.number(field.listCountType).toInt()
                                repeat(n) { cursor.number(field.type) }
                            } else {
                                byName[field.name] = cursor.number(field.type)
                            }
                        }
                        points += Vec3(
                            byName["x"] ?: 0.0,
                            byName["y"] ?: 0.0,
                            byName["z"] ?: 0.0,
                        )
                    }

                    "face" -> {
                        facesInFile++
                        val corners = ArrayList<Vec3>(4)
                        for (field in element.fields) {
                            if (field.listCountType == null) {
                                cursor.number(field.type)
                                continue
                            }
                            val n = cursor.number(field.listCountType).toInt()
                            repeat(n) {
                                val index = cursor.number(field.type).toInt()
                                if (index in points.indices) corners += points[index]
                            }
                        }
                        if (corners.size >= 3 && faces.size < FACE_BUDGET) {
                            faces addIfReal MeshFace(corners)
                        }
                    }

                    else -> for (field in element.fields) {
                        if (field.listCountType != null) {
                            val n = cursor.number(field.listCountType).toInt()
                            repeat(n) { cursor.number(field.type) }
                        } else {
                            cursor.number(field.type)
                        }
                    }
                }
            }
        }
        return finish(faces, facesInFile)
    }

    private fun finish(faces: List<MeshFace>, facesInFile: Int): MeshResult =
        if (faces.isEmpty()) {
            MeshResult.Failure(
                if (facesInFile > 0) MeshFailure.MALFORMED else MeshFailure.NOTHING_IN_IT,
            )
        } else {
            MeshResult.Success(Mesh(faces = faces, facesInFile = facesInFile))
        }

    /** Walks the binary body, reading whatever width the header named. */
    private class Cursor(val bytes: ByteArray, var at: Int, val little: Boolean) {

        fun has(n: Int): Boolean = at + n <= bytes.size

        /** One number of the named PLY type, as a Double. */
        fun number(type: String): Double = when (type) {
            "char", "int8" -> byte().toDouble()
            "uchar", "uint8" -> (byte().toInt() and 0xFF).toDouble()
            "short", "int16" -> integer(2, signed = true).toDouble()
            "ushort", "uint16" -> integer(2, signed = false).toDouble()
            "int", "int32" -> integer(4, signed = true).toDouble()
            "uint", "uint32" -> integer(4, signed = false).toDouble()
            "float", "float32" -> Float.fromBits(integer(4, signed = true).toInt()).toDouble()
            "double", "float64" -> Double.fromBits(integer(8, signed = true))
            else -> 0.0
        }

        private fun byte(): Byte = if (has(1)) bytes[at++] else 0

        private fun integer(width: Int, signed: Boolean): Long {
            if (!has(width)) {
                at = bytes.size
                return 0
            }
            var value = 0L
            for (i in 0 until width) {
                val b = bytes[at + if (little) i else width - 1 - i].toLong() and 0xFF
                value = value or (b shl (8 * i))
            }
            at += width
            if (signed && width < 8) {
                val sign = 1L shl (width * 8 - 1)
                if (value and sign != 0L) value -= 1L shl (width * 8)
            }
            return value
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
