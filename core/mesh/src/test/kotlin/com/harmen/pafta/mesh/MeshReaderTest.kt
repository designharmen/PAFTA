package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StlReaderTest {

    /** A binary STL of [count] identical triangles, with a header to match. */
    private fun binaryStl(count: Int, header: String = "PAFTA test"): ByteArray {
        val buffer = ByteBuffer.allocate(84 + 50 * count).order(ByteOrder.LITTLE_ENDIAN)
        val name = header.toByteArray()
        buffer.put(name.copyOf(80))
        buffer.putInt(count)
        repeat(count) { i ->
            // Normal, then three corners.
            buffer.putFloat(0f).putFloat(0f).putFloat(1f)
            buffer.putFloat(0f).putFloat(0f).putFloat(i.toFloat())
            buffer.putFloat(100f).putFloat(0f).putFloat(i.toFloat())
            buffer.putFloat(0f).putFloat(100f).putFloat(i.toFloat())
            buffer.putShort(0)
        }
        return buffer.array()
    }

    private fun mesh(result: MeshResult) = assertIs<MeshResult.Success>(result).mesh

    @Test
    fun `a binary file is read, triangle for triangle`() {
        val model = mesh(StlReader.read(binaryStl(3)))
        assertEquals(3, model.faces.size)
        assertEquals(3, model.faces.first().points.size)
        assertTrue(model.faces.first().points[1].isCloseTo(Vec3(100.0, 0.0, 0.0), 1e-6))
    }

    @Test
    fun `a binary file whose header begins with the word solid is still binary`() {
        // The trap: a binary STL may carry anything in its header, including
        // the word every text file starts with. Read as text it yields nothing
        // from a perfectly good file.
        val model = mesh(StlReader.read(binaryStl(2, header = "solid exported by something")))
        assertEquals(2, model.faces.size)
    }

    @Test
    fun `an ascii file is read too`() {
        val text = """
            solid kule
              facet normal 0 0 1
                outer loop
                  vertex 0 0 0
                  vertex 10 0 0
                  vertex 0 10 0
                endloop
              endfacet
              facet normal 0 0 1
                outer loop
                  vertex 0 0 5
                  vertex 10 0 5
                  vertex 0 10 5
                endloop
              endfacet
            endsolid kule
        """.trimIndent()

        val model = mesh(StlReader.read(text.toByteArray()))
        assertEquals(2, model.faces.size)
        assertEquals(listOf("kule"), model.groups)
        assertTrue(model.faces[1].points[0].isCloseTo(Vec3(0.0, 0.0, 5.0), 1e-6))
    }

    @Test
    fun `an ascii file written in capitals is read the same`() {
        val text = "SOLID X\nFACET NORMAL 0 0 1\nOUTER LOOP\n" +
            "VERTEX 0 0 0\nVERTEX 1 0 0\nVERTEX 0 1 0\nENDLOOP\nENDFACET\nENDSOLID X"
        assertEquals(1, mesh(StlReader.read(text.toByteArray())).faces.size)
    }

    @Test
    fun `how big the model is comes out of the points`() {
        val box = mesh(StlReader.read(binaryStl(4))).bounds()
        assertEquals(100.0, box.size.x, 1e-6)
        assertEquals(100.0, box.size.y, 1e-6)
        assertEquals(3.0, box.size.z, 1e-6)
    }

    @Test
    fun `a file cut short says so rather than showing half a model`() {
        val cut = binaryStl(5).copyOf(84 + 50 * 2 + 10)
        // The count says five and the bytes are not five, so it is not the
        // exact size a binary file would be: it is refused as malformed by the
        // same test that tells the two spellings apart.
        assertIs<MeshResult.Failure>(StlReader.read(cut))
    }

    @Test
    fun `an empty file is refused rather than opened as an empty model`() {
        assertEquals(
            MeshFailure.MALFORMED,
            assertIs<MeshResult.Failure>(StlReader.read(ByteArray(0))).reason,
        )
    }

    @Test
    fun `a binary file with no triangles in it is refused`() {
        assertEquals(
            MeshFailure.NOTHING_IN_IT,
            assertIs<MeshResult.Failure>(StlReader.read(binaryStl(0))).reason,
        )
    }

    @Test
    fun `a model past the budget is read up to it, and says what it really held`() {
        val huge = FACE_BUDGET + 25
        val model = mesh(StlReader.read(binaryStl(huge)))

        assertEquals(FACE_BUDGET, model.faces.size)
        assertEquals(huge, model.facesInFile)
        assertTrue(model.isTruncated, "a model cut to fit must admit it")
    }
}

class ObjReaderTest {

    private fun mesh(result: MeshResult) = assertIs<MeshResult.Success>(result).mesh

    @Test
    fun `points and faces are read, and the numbers start at one`() {
        val model = mesh(
            ObjReader.read(
                """
                v 0 0 0
                v 10 0 0
                v 10 10 0
                f 1 2 3
                """.trimIndent(),
            ),
        )
        assertEquals(1, model.faces.size)
        assertTrue(model.faces[0].points[0].isCloseTo(Vec3.ZERO, 1e-9))
        assertTrue(model.faces[0].points[2].isCloseTo(Vec3(10.0, 10.0, 0.0), 1e-9))
    }

    @Test
    fun `a negative number counts back from what has been defined so far`() {
        val model = mesh(
            ObjReader.read(
                """
                v 0 0 0
                v 1 0 0
                v 1 1 0
                f -3 -2 -1
                """.trimIndent(),
            ),
        )
        assertTrue(model.faces[0].points[0].isCloseTo(Vec3.ZERO, 1e-9))
        assertTrue(model.faces[0].points[2].isCloseTo(Vec3(1.0, 1.0, 0.0), 1e-9))
    }

    @Test
    fun `only the first number of a face token is the point`() {
        val model = mesh(
            ObjReader.read(
                """
                v 0 0 0
                v 1 0 0
                v 1 1 0
                vt 0 0
                vn 0 0 1
                f 1/1/1 2/1/1 3//1
                """.trimIndent(),
            ),
        )
        assertEquals(3, model.faces[0].points.size)
        assertTrue(model.faces[0].points[1].isCloseTo(Vec3(1.0, 0.0, 0.0), 1e-9))
    }

    @Test
    fun `a four-sided face stays one face rather than becoming two triangles`() {
        val model = mesh(
            ObjReader.read(
                """
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                f 1 2 3 4
                """.trimIndent(),
            ),
        )
        assertEquals(1, model.faces.size)
        assertEquals(4, model.faces[0].points.size)
    }

    @Test
    fun `the names the file gives its parts are kept`() {
        val model = mesh(
            ObjReader.read(
                """
                o duvar
                v 0 0 0
                v 1 0 0
                v 1 1 0
                f 1 2 3
                g cati
                f 1 2 3
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("duvar", "cati"), model.groups)
        assertEquals("duvar", model.faces[0].group)
        assertEquals("cati", model.faces[1].group)
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val model = mesh(
            ObjReader.read(
                """
                # exported by something

                mtllib model.mtl
                v 0 0 0
                v 1 0 0
                v 1 1 0
                usemtl beton
                f 1 2 3   # the only face
                """.trimIndent(),
            ),
        )
        assertEquals(1, model.faces.size)
    }

    @Test
    fun `a face of two points is a line, and is dropped`() {
        val outcome = ObjReader.read(
            """
            v 0 0 0
            v 1 0 0
            f 1 2
            """.trimIndent(),
        )
        assertEquals(MeshFailure.MALFORMED, assertIs<MeshResult.Failure>(outcome).reason)
    }

    @Test
    fun `a face pointing at a point that does not exist is refused`() {
        val outcome = ObjReader.read(
            """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            f 1 2 9
            """.trimIndent(),
        )
        assertEquals(MeshFailure.MALFORMED, assertIs<MeshResult.Failure>(outcome).reason)
    }

    @Test
    fun `a file of points and no faces is refused rather than shown as nothing`() {
        val outcome = ObjReader.read("v 0 0 0\nv 1 0 0\nv 1 1 0")
        assertEquals(MeshFailure.NOTHING_IN_IT, assertIs<MeshResult.Failure>(outcome).reason)
    }

    @Test
    fun `an empty file is refused`() {
        assertEquals(
            MeshFailure.NOTHING_IN_IT,
            assertIs<MeshResult.Failure>(ObjReader.read("")).reason,
        )
    }

    @Test
    fun `how big the model is comes out of the points`() {
        val model = mesh(
            ObjReader.read(
                """
                v -5 0 0
                v 15 0 0
                v 15 8 3
                f 1 2 3
                """.trimIndent(),
            ),
        )
        assertEquals(20.0, model.bounds().size.x, 1e-9)
        assertEquals(8.0, model.bounds().size.y, 1e-9)
        assertEquals(3.0, model.bounds().size.z, 1e-9)
    }
}
