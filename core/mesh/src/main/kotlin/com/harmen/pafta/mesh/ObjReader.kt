package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3
import java.io.InputStream

/**
 * Wavefront OBJ: the plainest 3D format there is, and the one everything exports.
 *
 * A list of points, then a list of faces that refer to them by number. Three
 * things about it bite, and all three are handled here:
 *
 *  * the numbers start at **one**, not zero;
 *  * a **negative** number counts back from the end of the list so far, which
 *    is how a file written in one pass refers to what it has just defined;
 *  * a face index may be written `7`, `7/2`, `7//3` or `7/2/3` — only the first
 *    number is the point, and the rest are texture and normal references that
 *    this reader has nothing to do with.
 *
 * Faces are kept as they were written rather than cut into triangles. The
 * viewer fills any flat outline, so a four-sided face stays one face, which is
 * both fewer faces to sort and a tidier picture.
 */
public object ObjReader {

    public fun read(text: String): MeshResult {
        val points = ArrayList<Vec3>()
        val faces = ArrayList<MeshFace>()
        val groups = LinkedHashSet<String>()
        var group = ""
        var inFile = 0

        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(WHITESPACE)

            when (parts[0]) {
                "v" -> {
                    val numbers = parts.drop(1).mapNotNull { it.toDoubleOrNull() }
                    if (numbers.size < 3) return MeshResult.Failure(MeshFailure.MALFORMED)
                    points += Vec3(numbers[0], numbers[1], numbers[2])
                }

                "g", "o" -> {
                    group = parts.drop(1).joinToString(" ")
                    if (group.isNotEmpty()) groups += group
                }

                "f" -> {
                    inFile++
                    if (faces.size >= FACE_BUDGET) continue
                    val corners = ArrayList<Vec3>(parts.size - 1)
                    for (token in parts.drop(1)) {
                        val index = token.substringBefore('/').toIntOrNull() ?: continue
                        val resolved = when {
                            index > 0 -> index - 1
                            // Negative counts back from the end of what has been
                            // defined so far, not from the end of the file.
                            index < 0 -> points.size + index
                            else -> continue
                        }
                        if (resolved !in points.indices) {
                            return MeshResult.Failure(MeshFailure.MALFORMED)
                        }
                        corners += points[resolved]
                    }
                    // A face of two points is a line, not a face; it is dropped
                    // rather than treated as a sliver of no area.
                    if (corners.size >= 3) faces addIfReal MeshFace(corners, group)
                }
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

    public fun read(bytes: ByteArray): MeshResult = read(bytes.toString(Charsets.UTF_8))

    public fun read(stream: InputStream): MeshResult = read(stream.readBytes())

    private val WHITESPACE = Regex("\\s+")
}
