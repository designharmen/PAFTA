package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * glTF and GLB: what everything exports today.
 *
 * Blender, SketchUp, Revit, Rhino and every web tool write it, which makes it
 * the one format worth reading carefully. It is JSON describing where the
 * numbers are, plus the numbers: a mesh points at accessors, an accessor points
 * at a slice of a buffer, and a buffer is either a binary chunk sitting behind
 * the JSON (GLB) or spelled out as base64 inside it.
 *
 * What is **not** read, and deliberately: materials, textures, animation,
 * cameras, skins. The viewer fills flat faces; anything richer would be parsed,
 * stored, and then quietly ignored.
 *
 * A buffer kept in a separate `.bin` file beside the `.gltf` cannot be read,
 * because PAFTA imports one file and copies it into the project — there is no
 * second file to go and find later. That is reported rather than guessed at,
 * and it is why GLB, which carries everything in one file, is the one to ask
 * an exporter for.
 */
public object GltfReader {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads either spelling, deciding by the four bytes at the front. */
    public fun read(bytes: ByteArray): MeshResult = when {
        bytes.size >= 12 && isGlb(bytes) -> readGlb(bytes)
        bytes.isEmpty() -> MeshResult.Failure(MeshFailure.MALFORMED)
        else -> readJson(bytes.toString(Charsets.UTF_8), embedded = null)
    }

    private fun isGlb(bytes: ByteArray): Boolean =
        bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()

    /**
     * GLB: a twelve-byte header, then chunks. The first chunk is the JSON, the
     * second — when there is one — is the binary the JSON refers to.
     */
    private fun readGlb(bytes: ByteArray): MeshResult {
        var at = 12
        var text: String? = null
        var binary: ByteArray? = null

        while (at + 8 <= bytes.size) {
            val length = readUInt32(bytes, at).toInt()
            val kind = readUInt32(bytes, at + 4)
            val from = at + 8
            val to = from + length
            if (length < 0 || to > bytes.size) return MeshResult.Failure(MeshFailure.TRUNCATED_FILE)

            when (kind) {
                CHUNK_JSON -> text = bytes.copyOfRange(from, to).toString(Charsets.UTF_8)
                CHUNK_BIN -> binary = bytes.copyOfRange(from, to)
            }
            // Chunks are padded to four bytes.
            at = to + ((4 - length % 4) % 4)
        }

        val document = text ?: return MeshResult.Failure(MeshFailure.MALFORMED)
        return readJson(document, binary)
    }

    private fun readJson(text: String, embedded: ByteArray?): MeshResult {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return MeshResult.Failure(MeshFailure.MALFORMED)

        val buffers = root["buffers"]?.jsonArray.orEmpty().map { buffer ->
            val uri = buffer.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            when {
                uri == null -> embedded
                uri.startsWith("data:") -> decodeDataUri(uri)
                // A buffer in a file of its own, which is not here.
                else -> null
            }
        }
        if (buffers.isNotEmpty() && buffers.all { it == null }) {
            return MeshResult.Failure(MeshFailure.NEEDS_ANOTHER_FILE)
        }

        val views = root["bufferViews"]?.jsonArray.orEmpty()
        val accessors = root["accessors"]?.jsonArray.orEmpty()
        val meshes = root["meshes"]?.jsonArray.orEmpty()

        val reader = Accessors(buffers, views, accessors)
        val faces = ArrayList<MeshFace>()
        val groups = LinkedHashSet<String>()
        var facesInFile = 0

        // Every node that has a mesh, with the transform it stands at. A node
        // under another node is drawn where its parent puts it, which is what
        // makes a model of many parts come out assembled rather than piled on
        // the origin.
        for ((meshIndex, placement, name) in placements(root)) {
            val mesh = meshes.getOrNull(meshIndex)?.jsonObject ?: continue
            val label = name.ifEmpty {
                mesh["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            if (label.isNotEmpty()) groups += label

            for (piece in mesh["primitives"]?.jsonArray.orEmpty()) {
                val primitive = piece.jsonObject
                // Mode 4 is triangles. The line and point modes have no faces
                // to fill, and the strip and fan modes are rare enough that
                // reading them wrongly would be worse than not reading them.
                val mode = primitive["mode"]?.jsonPrimitive?.intOrNull ?: 4
                if (mode != 4) continue

                val positionIndex = primitive["attributes"]?.jsonObject
                    ?.get("POSITION")?.jsonPrimitive?.intOrNull ?: continue
                val positions = reader.vectors(positionIndex) ?: continue
                val indices = primitive["indices"]?.jsonPrimitive?.intOrNull
                    ?.let { reader.integers(it) }
                    ?: positions.indices.toList()

                var i = 0
                while (i + 2 < indices.size) {
                    facesInFile++
                    if (faces.size < FACE_BUDGET) {
                        val corners = (0..2).mapNotNull { positions.getOrNull(indices[i + it]) }
                        if (corners.size == 3) {
                            faces addIfReal MeshFace(corners.map { placement.applyTo(it) }, label)
                        }
                    }
                    i += 3
                }
            }
        }

        if (faces.isEmpty()) {
            return MeshResult.Failure(
                if (facesInFile > 0) MeshFailure.MALFORMED else MeshFailure.NOTHING_IN_IT,
            )
        }
        return MeshResult.Success(
            Mesh(faces = faces, groups = groups.toList(), facesInFile = facesInFile),
        )
    }

    // --- where each mesh stands ----------------------------------------------

    private data class Placement(
        val meshIndex: Int,
        val transform: Transform,
        val name: String,
    )

    private operator fun Placement.component1() = meshIndex
    private operator fun Placement.component2() = transform
    private operator fun Placement.component3() = name

    /** Every node holding a mesh, with the transform that gets it into place. */
    private fun placements(root: JsonObject): List<Placement> {
        val nodes = root["nodes"]?.jsonArray.orEmpty()
        val found = ArrayList<Placement>()
        val visited = HashSet<Int>()

        fun walk(index: Int, parent: Transform) {
            // A scene that refers to itself would otherwise never stop.
            if (!visited.add(index)) return
            val node = nodes.getOrNull(index)?.jsonObject ?: return
            val here = parent.then(transformOf(node))

            node["mesh"]?.jsonPrimitive?.intOrNull?.let {
                found += Placement(it, here, node["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
            }
            for (child in node["children"]?.jsonArray.orEmpty()) {
                child.jsonPrimitive.intOrNull?.let { walk(it, here) }
            }
        }

        val roots = root["scenes"]?.jsonArray.orEmpty()
            .flatMap { it.jsonObject["nodes"]?.jsonArray.orEmpty() }
            .mapNotNull { it.jsonPrimitive.intOrNull }

        if (roots.isEmpty()) {
            for (i in nodes.indices) walk(i, Transform.NONE)
        } else {
            for (i in roots) walk(i, Transform.NONE)
        }
        return found
    }

    private fun transformOf(node: JsonObject): Transform {
        node["matrix"]?.jsonArray?.let { raw ->
            if (raw.size == 16) {
                val m = raw.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
                return Transform(m)
            }
        }
        val t = node["translation"]?.jsonArray?.map { it.jsonPrimitive.double }
        val r = node["rotation"]?.jsonArray?.map { it.jsonPrimitive.double }
        val s = node["scale"]?.jsonArray?.map { it.jsonPrimitive.double }
        return Transform.of(t, r, s)
    }

    /**
     * A 4x4 placement, held in glTF's own column-major order.
     *
     * Only ever used to put a point where it belongs, so there is no inverse,
     * no decomposition and no matrix algebra beyond multiplying two of them.
     */
    private class Transform(private val m: List<Double>) {

        fun applyTo(p: Vec3): Vec3 = Vec3(
            m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
            m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
            m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
        )

        fun then(child: Transform): Transform {
            val out = DoubleArray(16)
            for (col in 0 until 4) {
                for (row in 0 until 4) {
                    var sum = 0.0
                    for (k in 0 until 4) sum += m[k * 4 + row] * child.m[col * 4 + k]
                    out[col * 4 + row] = sum
                }
            }
            return Transform(out.toList())
        }

        companion object {
            val NONE = Transform(
                listOf(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )

            /** Move, turn and size, combined the way glTF says: T * R * S. */
            fun of(t: List<Double>?, r: List<Double>?, s: List<Double>?): Transform {
                if (t == null && r == null && s == null) return NONE
                val sx = s?.getOrNull(0) ?: 1.0
                val sy = s?.getOrNull(1) ?: 1.0
                val sz = s?.getOrNull(2) ?: 1.0

                // The rotation is a quaternion, x y z w.
                val qx = r?.getOrNull(0) ?: 0.0
                val qy = r?.getOrNull(1) ?: 0.0
                val qz = r?.getOrNull(2) ?: 0.0
                val qw = r?.getOrNull(3) ?: 1.0

                val m = DoubleArray(16)
                m[0] = (1 - 2 * (qy * qy + qz * qz)) * sx
                m[1] = (2 * (qx * qy + qz * qw)) * sx
                m[2] = (2 * (qx * qz - qy * qw)) * sx
                m[4] = (2 * (qx * qy - qz * qw)) * sy
                m[5] = (1 - 2 * (qx * qx + qz * qz)) * sy
                m[6] = (2 * (qy * qz + qx * qw)) * sy
                m[8] = (2 * (qx * qz + qy * qw)) * sz
                m[9] = (2 * (qy * qz - qx * qw)) * sz
                m[10] = (1 - 2 * (qx * qx + qy * qy)) * sz
                m[12] = t?.getOrNull(0) ?: 0.0
                m[13] = t?.getOrNull(1) ?: 0.0
                m[14] = t?.getOrNull(2) ?: 0.0
                m[15] = 1.0
                return Transform(m.toList())
            }
        }
    }

    // --- reading the numbers out of a buffer ---------------------------------

    /** Turns an accessor index into the numbers it describes. */
    private class Accessors(
        val buffers: List<ByteArray?>,
        val views: List<JsonElement>,
        val accessors: List<JsonElement>,
    ) {
        fun vectors(index: Int): List<Vec3>? {
            val accessor = accessors.getOrNull(index)?.jsonObject ?: return null
            if (accessor["type"]?.jsonPrimitive?.contentOrNull != "VEC3") return null
            val raw = numbers(accessor, components = 3) ?: return null
            return (raw.indices step 3).mapNotNull { i ->
                if (i + 2 < raw.size) Vec3(raw[i], raw[i + 1], raw[i + 2]) else null
            }
        }

        fun integers(index: Int): List<Int>? {
            val accessor = accessors.getOrNull(index)?.jsonObject ?: return null
            return numbers(accessor, components = 1)?.map { it.toInt() }
        }

        private fun numbers(accessor: JsonObject, components: Int): List<Double>? {
            val count = accessor["count"]?.jsonPrimitive?.intOrNull ?: return null
            val type = accessor["componentType"]?.jsonPrimitive?.intOrNull ?: return null
            val width = widthOf(type) ?: return null

            val viewIndex = accessor["bufferView"]?.jsonPrimitive?.intOrNull ?: return null
            val view = views.getOrNull(viewIndex)?.jsonObject ?: return null
            val buffer = buffers.getOrNull(
                view["buffer"]?.jsonPrimitive?.intOrNull ?: return null,
            ) ?: return null

            val viewStart = view["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0
            val accessorStart = accessor["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0
            // A stride is how far apart two records are when a buffer holds
            // several attributes interleaved; zero means packed tight.
            val stride = view["byteStride"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it > 0 } ?: (width * components)

            val out = ArrayList<Double>(count * components)
            for (i in 0 until count) {
                val base = viewStart + accessorStart + i * stride
                for (c in 0 until components) {
                    val at = base + c * width
                    if (at + width > buffer.size) return null
                    out += readNumber(buffer, at, type)
                }
            }
            return out
        }

        private fun widthOf(componentType: Int): Int? = when (componentType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> null
        }

        private fun readNumber(bytes: ByteArray, at: Int, componentType: Int): Double =
            when (componentType) {
                5120 -> bytes[at].toDouble()
                5121 -> (bytes[at].toInt() and 0xFF).toDouble()
                5122 -> readUInt(bytes, at, 2).toShort().toDouble()
                5123 -> readUInt(bytes, at, 2).toDouble()
                5125 -> readUInt(bytes, at, 4).toDouble()
                5126 -> Float.fromBits(readUInt(bytes, at, 4).toInt()).toDouble()
                else -> 0.0
            }
    }

    private fun decodeDataUri(uri: String): ByteArray? {
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        return runCatching { Base64.getDecoder().decode(uri.substring(comma + 1)) }.getOrNull()
    }

    private fun readUInt32(bytes: ByteArray, at: Int): Long = readUInt(bytes, at, 4)

    private fun readUInt(bytes: ByteArray, at: Int, width: Int): Long {
        var value = 0L
        for (i in 0 until width) {
            if (at + i >= bytes.size) return value
            value = value or ((bytes[at + i].toLong() and 0xFF) shl (8 * i))
        }
        return value
    }

    private const val CHUNK_JSON = 0x4E4F534AL
    private const val CHUNK_BIN = 0x004E4942L
}
