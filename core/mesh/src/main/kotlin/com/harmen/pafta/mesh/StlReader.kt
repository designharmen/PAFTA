package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3
import java.io.InputStream

/**
 * STL: the format every 3D printer and every CAD program can write.
 *
 * Triangles and nothing else — no units, no colours, not even a guarantee that
 * the triangles join up. It comes in two spellings of the same thing, a text
 * one and a binary one, and telling them apart is the one trap in the format:
 * a binary file is allowed to begin with the word `solid`, which is also how
 * every text file begins. Guessing from the first five bytes is the mistake
 * everybody makes once.
 *
 * So the size decides. A binary STL is exactly 84 bytes of header and count
 * plus 50 bytes a triangle; nothing else lands on that number by accident.
 */
public object StlReader {

    /** Reads either spelling, deciding which by the shape of the bytes. */
    public fun read(bytes: ByteArray): MeshResult = when {
        bytes.isEmpty() -> MeshResult.Failure(MeshFailure.MALFORMED)
        looksBinary(bytes) -> readBinary(bytes)
        else -> readText(bytes.toString(Charsets.UTF_8))
    }

    public fun read(stream: InputStream): MeshResult = read(stream.readBytes())

    /**
     * True when the byte count is exactly what a binary file of its own stated
     * triangle count would be.
     *
     * Not "does it start with solid": a binary STL written by a program that
     * put its name in the header may start with those five letters too, and
     * reading it as text then produces an empty model from a perfectly good
     * file.
     */
    internal fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER + COUNT) return false
        val count = readUInt32(bytes, HEADER)
        if (count < 0L) return false
        return bytes.size.toLong() == HEADER + COUNT + count * TRIANGLE
    }

    private fun readBinary(bytes: ByteArray): MeshResult {
        val count = readUInt32(bytes, HEADER)
        val faces = ArrayList<MeshFace>(minOf(count, FACE_BUDGET.toLong()).toInt())

        var at = HEADER + COUNT
        var read = 0L
        while (read < count && faces.size < FACE_BUDGET) {
            if (at + TRIANGLE > bytes.size) return MeshResult.Failure(MeshFailure.TRUNCATED_FILE)
            // The first three floats are the normal, which is thrown away: it
            // is often wrong in the wild, and the winding of the points says
            // the same thing without being able to disagree with them.
            val corners = (0 until 3).map { corner ->
                val base = at + 12 + corner * 12
                Vec3(
                    readFloat(bytes, base).toDouble(),
                    readFloat(bytes, base + 4).toDouble(),
                    readFloat(bytes, base + 8).toDouble(),
                )
            }
            faces += MeshFace(corners)
            at += TRIANGLE
            read++
        }

        if (faces.isEmpty()) return MeshResult.Failure(MeshFailure.NOTHING_IN_IT)
        return MeshResult.Success(Mesh(faces = faces, facesInFile = count.toInt()))
    }

    private fun readText(text: String): MeshResult {
        val faces = ArrayList<MeshFace>()
        val groups = LinkedHashSet<String>()
        var corners = ArrayList<Vec3>(3)
        var name = ""
        var inFile = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("solid", ignoreCase = true) ->
                    name = line.removePrefix("solid").removePrefix("SOLID").trim()
                        .also { if (it.isNotEmpty()) groups += it }

                line.startsWith("vertex", ignoreCase = true) -> {
                    val numbers = line.split(WHITESPACE).drop(1).mapNotNull { it.toDoubleOrNull() }
                    if (numbers.size < 3) return MeshResult.Failure(MeshFailure.MALFORMED)
                    corners += Vec3(numbers[0], numbers[1], numbers[2])
                }

                line.startsWith("endfacet", ignoreCase = true) -> {
                    inFile++
                    if (corners.size >= 3 && faces.size < FACE_BUDGET) {
                        faces += MeshFace(corners.toList(), name)
                    }
                    corners = ArrayList(3)
                }

                // `outer loop` starts a new triangle; anything left over from a
                // malformed one before it is dropped rather than joined on.
                line.startsWith("outer", ignoreCase = true) -> corners = ArrayList(3)
            }
        }

        if (faces.isEmpty()) {
            return MeshResult.Failure(
                if (inFile > 0) MeshFailure.MALFORMED else MeshFailure.NOTHING_IN_IT,
            )
        }
        return MeshResult.Success(
            Mesh(faces = faces, groups = groups.toList(), facesInFile = inFile),
        )
    }

    private const val HEADER = 80
    private const val COUNT = 4
    private const val TRIANGLE = 50

    private val WHITESPACE = Regex("\\s+")

    /** Little-endian, as the format says, whatever this machine happens to be. */
    private fun readUInt32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)

    private fun readFloat(bytes: ByteArray, at: Int): Float =
        Float.fromBits(readUInt32(bytes, at).toInt())
}
