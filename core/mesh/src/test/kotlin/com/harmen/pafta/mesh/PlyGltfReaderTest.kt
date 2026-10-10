package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlyReaderTest {

    private fun mesh(result: MeshResult) = assertIs<MeshResult.Success>(result).mesh

    private val asciiPly = """
        ply
        format ascii 1.0
        comment made by a scanner
        element vertex 4
        property float x
        property float y
        property float z
        element face 2
        property list uchar int vertex_indices
        end_header
        0 0 0
        1000 0 0
        1000 1000 0
        0 1000 0
        3 0 1 2
        3 0 2 3
    """.trimIndent()

    @Test
    fun `a text file is read by what its own header says is in it`() {
        val model = mesh(PlyReader.read(asciiPly.toByteArray()))
        assertEquals(2, model.faces.size)
        assertTrue(model.faces[0].points[1].isCloseTo(Vec3(1000.0, 0.0, 0.0), 1e-6))
        assertEquals(1000.0, model.bounds().size.x, 1e-6)
    }

    @Test
    fun `fields the reader has no use for are stepped over, not guessed at`() {
        // Colours between the coordinates and the faces: a file like this is
        // what every scanner actually writes.
        val coloured = """
            ply
            format ascii 1.0
            element vertex 3
            property float x
            property float y
            property float z
            property uchar red
            property uchar green
            property uchar blue
            element face 1
            property list uchar int vertex_indices
            end_header
            0 0 0 255 0 0
            10 0 0 0 255 0
            10 10 0 0 0 255
            3 0 1 2
        """.trimIndent()

        val model = mesh(PlyReader.read(coloured.toByteArray()))
        assertEquals(1, model.faces.size)
        assertTrue(model.faces[0].points[2].isCloseTo(Vec3(10.0, 10.0, 0.0), 1e-6))
    }

    @Test
    fun `a four-sided face stays one face`() {
        val quad = asciiPly.replace("element face 2", "element face 1")
            .replace("3 0 1 2\n3 0 2 3", "4 0 1 2 3")
        val model = mesh(PlyReader.read(quad.toByteArray()))
        assertEquals(1, model.faces.size)
        assertEquals(4, model.faces[0].points.size)
    }

    @Test
    fun `a little-endian binary file is read the same as the text one`() {
        // Built up with explicit line endings rather than a trimmed block:
        // the body begins at the byte straight after `end_header`'s newline,
        // so one stray blank line shifts every float by one byte.
        val header = "ply\nformat binary_little_endian 1.0\n" +
            "element vertex 4\nproperty float x\nproperty float y\nproperty float z\n" +
            "element face 2\nproperty list uchar int vertex_indices\nend_header\n"

        val points = listOf(
            Triple(0f, 0f, 0f),
            Triple(1000f, 0f, 0f),
            Triple(1000f, 1000f, 0f),
            Triple(0f, 1000f, 0f),
        )
        val body = ByteBuffer.allocate(4 * 12 + 2 * (1 + 12)).order(ByteOrder.LITTLE_ENDIAN)
        for ((x, y, z) in points) body.putFloat(x).putFloat(y).putFloat(z)
        body.put(3).putInt(0).putInt(1).putInt(2)
        body.put(3).putInt(0).putInt(2).putInt(3)

        val model = mesh(PlyReader.read(header.toByteArray() + body.array()))
        assertEquals(2, model.faces.size)
        assertEquals(1000.0, model.bounds().size.y, 1e-6)
    }

    @Test
    fun `a big-endian binary file is read too`() {
        val header = "ply\nformat binary_big_endian 1.0\n" +
            "element vertex 3\nproperty float x\nproperty float y\nproperty float z\n" +
            "element face 1\nproperty list uchar int vertex_indices\nend_header\n"
        val body = ByteBuffer.allocate(3 * 12 + 1 + 12).order(ByteOrder.BIG_ENDIAN)
        body.putFloat(0f).putFloat(0f).putFloat(0f)
        body.putFloat(5f).putFloat(0f).putFloat(0f)
        body.putFloat(5f).putFloat(5f).putFloat(0f)
        body.put(3).putInt(0).putInt(1).putInt(2)

        val model = mesh(PlyReader.read(header.toByteArray() + body.array()))
        assertEquals(1, model.faces.size)
        assertEquals(5.0, model.bounds().size.x, 1e-6)
    }

    @Test
    fun `something that is not a PLY file at all is refused`() {
        assertEquals(
            MeshFailure.MALFORMED,
            assertIs<MeshResult.Failure>(PlyReader.read("merhaba".toByteArray())).reason,
        )
    }

    @Test
    fun `a header with points and no faces is refused rather than shown as nothing`() {
        val noFaces = "ply\nformat ascii 1.0\nelement vertex 1\n" +
            "property float x\nproperty float y\nproperty float z\nend_header\n0 0 0\n"
        assertEquals(
            MeshFailure.NOTHING_IN_IT,
            assertIs<MeshResult.Failure>(PlyReader.read(noFaces.toByteArray())).reason,
        )
    }
}

class GltfReaderTest {

    private fun mesh(result: MeshResult) = assertIs<MeshResult.Success>(result).mesh

    /** Three points and three indices, as glTF lays them out in a buffer. */
    private fun triangleBuffer(): ByteArray {
        val buffer = ByteBuffer.allocate(3 * 12 + 3 * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(0f).putFloat(0f).putFloat(0f)
        buffer.putFloat(1000f).putFloat(0f).putFloat(0f)
        buffer.putFloat(1000f).putFloat(1000f).putFloat(0f)
        buffer.putShort(0).putShort(1).putShort(2)
        return buffer.array()
    }

    private fun document(bufferUri: String?, node: String): String {
        val uri = if (bufferUri == null) "" else "\"uri\": \"$bufferUri\","
        return """
        {
          "asset": { "version": "2.0" },
          "scenes": [ { "nodes": [0] } ],
          "nodes": [ $node ],
          "meshes": [ {
            "name": "govde",
            "primitives": [ {
              "attributes": { "POSITION": 0 },
              "indices": 1,
              "mode": 4
            } ]
          } ],
          "accessors": [
            { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
            { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
          ],
          "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
            { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
          ],
          "buffers": [ { $uri "byteLength": 42 } ]
        }
        """.trimIndent()
    }

    /** A real GLB: header, JSON chunk, binary chunk, each padded to four. */
    private fun glb(json: String, binary: ByteArray): ByteArray {
        fun pad(bytes: ByteArray, filler: Byte): ByteArray {
            val extra = (4 - bytes.size % 4) % 4
            return bytes + ByteArray(extra) { filler }
        }

        val jsonChunk = pad(json.toByteArray(), ' '.code.toByte())
        val binChunk = pad(binary, 0)
        val total = 12 + 8 + jsonChunk.size + 8 + binChunk.size

        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put("glTF".toByteArray())
        out.putInt(2)
        out.putInt(total)
        out.putInt(jsonChunk.size).putInt(0x4E4F534A).put(jsonChunk)
        out.putInt(binChunk.size).putInt(0x004E4942).put(binChunk)
        return out.array()
    }

    @Test
    fun `a GLB is read - header, then json, then the numbers behind it`() {
        val model = mesh(
            GltfReader.read(glb(document(null, """{ "mesh": 0 }"""), triangleBuffer())),
        )
        assertEquals(1, model.faces.size)
        assertEquals(3, model.faces[0].points.size)
        assertTrue(model.faces[0].points[1].isCloseTo(Vec3(1000.0, 0.0, 0.0), 1e-3))
        assertEquals(listOf("govde"), model.groups)
    }

    @Test
    fun `a gltf with its numbers spelled out inside it is read too`() {
        val uri = "data:application/octet-stream;base64," +
            Base64.getEncoder().encodeToString(triangleBuffer())
        val model = mesh(GltfReader.read(document(uri, """{ "mesh": 0 }""").toByteArray()))
        assertEquals(1, model.faces.size)
    }

    @Test
    fun `a node puts its mesh where the node stands`() {
        val moved = """{ "mesh": 0, "translation": [5000, 0, 0] }"""
        val model = mesh(GltfReader.read(glb(document(null, moved), triangleBuffer())))

        assertTrue(model.faces[0].points[0].isCloseTo(Vec3(5000.0, 0.0, 0.0), 1e-3))
        assertEquals(5000.0, model.bounds().min.x, 1.0)
        assertEquals(6000.0, model.bounds().max.x, 1.0)
    }

    @Test
    fun `a node that scales its mesh scales what is drawn`() {
        val doubled = """{ "mesh": 0, "scale": [2, 2, 2] }"""
        val model = mesh(GltfReader.read(glb(document(null, doubled), triangleBuffer())))
        assertEquals(2000.0, model.bounds().size.x, 1.0)
    }

    @Test
    fun `a quarter turn about the up axis turns what is drawn`() {
        // A quaternion for 90 degrees about Y: (0, sin45, 0, cos45).
        val turned = """{ "mesh": 0, "rotation": [0, 0.70710678, 0, 0.70710678] }"""
        val model = mesh(GltfReader.read(glb(document(null, turned), triangleBuffer())))

        // What was 1000 along X is now 1000 along -Z.
        assertEquals(1000.0, model.bounds().size.z, 1.0)
        assertTrue(model.bounds().size.x < 1.0)
    }

    @Test
    fun `a gltf pointing at a bin file that is not here says exactly that`() {
        val outcome = GltfReader.read(document("scene.bin", """{ "mesh": 0 }""").toByteArray())
        assertEquals(
            MeshFailure.NEEDS_ANOTHER_FILE,
            assertIs<MeshResult.Failure>(outcome).reason,
        )
    }

    @Test
    fun `lines and points are passed over, because there is no face to fill`() {
        val lines = document(null, """{ "mesh": 0 }""").replace("\"mode\": 4", "\"mode\": 1")
        assertEquals(
            MeshFailure.NOTHING_IN_IT,
            assertIs<MeshResult.Failure>(
                GltfReader.read(glb(lines, triangleBuffer())),
            ).reason,
        )
    }

    @Test
    fun `a scene that refers to itself does not go round for ever`() {
        val loop = """
        {
          "asset": { "version": "2.0" },
          "scenes": [ { "nodes": [0] } ],
          "nodes": [
            { "children": [1] },
            { "children": [0], "mesh": 0 }
          ],
          "meshes": [ { "primitives": [ {
            "attributes": { "POSITION": 0 }, "indices": 1, "mode": 4
          } ] } ],
          "accessors": [
            { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
            { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
          ],
          "bufferViews": [
            { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
            { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
          ],
          "buffers": [ { "byteLength": 42 } ]
        }
        """.trimIndent()

        assertEquals(1, mesh(GltfReader.read(glb(loop, triangleBuffer()))).faces.size)
    }

    @Test
    fun `rubbish is refused rather than opened as an empty model`() {
        assertIs<MeshResult.Failure>(GltfReader.read("{ not json".toByteArray()))
        assertIs<MeshResult.Failure>(GltfReader.read(ByteArray(0)))
    }
}
