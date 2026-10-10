package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfReader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A DXF that is a 3D model rather than a plan.
 *
 * The owner's own file was the reason for this: a DXF exported from a 3D
 * program, which PAFTA opened and then reported as holding nothing to draw. It
 * held plenty — just not in any of the shapes the reader knew.
 */
class Dxf3DTest {

    private lateinit var root: java.io.File
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("pafta-dxf3d").toFile()
        store = ProjectStore(root)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun dxf(vararg groups: String): ByteArray =
        (listOf("0", "SECTION", "2", "ENTITIES") + groups.toList() + listOf("0", "ENDSEC", "0", "EOF"))
            .joinToString("\n", postfix = "\n")
            .toByteArray()

    /** One square `3DFACE`, a metre up in the air. */
    private val oneFace = dxf(
        "0", "3DFACE", "8", "KABUK",
        "10", "0.0", "20", "0.0", "30", "1000.0",
        "11", "1000.0", "21", "0.0", "31", "1000.0",
        "12", "1000.0", "22", "1000.0", "32", "1000.0",
        "13", "0.0", "23", "1000.0", "33", "1000.0",
    )

    @Test
    fun `a 3DFACE is read, and it keeps its height`() {
        val drawing = DxfReader.read(oneFace.inputStream())
        val face = assertIs<DxfEntity.Face>(drawing.entities.single())

        assertEquals(4, face.points.size)
        assertEquals("KABUK", face.layer)
        assertTrue(face.points.all { it.z == 1000.0 }, "a 3D face was flattened onto the ground")
    }

    @Test
    fun `a triangle written as a four-cornered face comes back a triangle`() {
        // Every program writes a triangle by repeating the last corner, which
        // read literally gives a face with two corners in the same place.
        val triangle = dxf(
            "0", "3DFACE", "8", "0",
            "10", "0.0", "20", "0.0", "30", "0.0",
            "11", "1000.0", "21", "0.0", "31", "0.0",
            "12", "0.0", "22", "1000.0", "32", "0.0",
            "13", "0.0", "23", "1000.0", "33", "0.0",
        )
        val face = assertIs<DxfEntity.Face>(DxfReader.read(triangle.inputStream()).entities.single())
        assertEquals(3, face.points.size)
    }

    @Test
    fun `a filled SOLID has its corners put back in order round the edge`() {
        // SOLID numbers its corners in a Z. Read literally the outline crosses
        // itself and the shape comes out as a bow tie.
        val solid = dxf(
            "0", "SOLID", "8", "0",
            "10", "0.0", "20", "0.0", "30", "0.0",
            "11", "1000.0", "21", "0.0", "31", "0.0",
            "12", "0.0", "22", "1000.0", "32", "0.0",
            "13", "1000.0", "23", "1000.0", "33", "0.0",
        )
        val face = assertIs<DxfEntity.Face>(DxfReader.read(solid.inputStream()).entities.single())
        assertEquals(4, face.points.size)
        // Going round the edge, the third corner is the far one.
        assertEquals(1000.0, face.points[2].x, 1e-9)
        assertEquals(1000.0, face.points[2].y, 1e-9)
    }

    @Test
    fun `a polyface mesh becomes one face for each face it declares`() {
        // Four corners, then two triangles made from them. This is how a 3D
        // model has been written to DXF since before 3DFACE was common.
        val mesh = dxf(
            "0", "POLYLINE", "8", "KAFES", "70", "64", "71", "4", "72", "2",
            "0", "VERTEX", "8", "KAFES", "70", "192", "10", "0.0", "20", "0.0", "30", "0.0",
            "0", "VERTEX", "8", "KAFES", "70", "192", "10", "1000.0", "20", "0.0", "30", "0.0",
            "0", "VERTEX", "8", "KAFES", "70", "192", "10", "1000.0", "20", "1000.0", "30", "500.0",
            "0", "VERTEX", "8", "KAFES", "70", "192", "10", "0.0", "20", "1000.0", "30", "500.0",
            "0", "VERTEX", "8", "KAFES", "70", "128", "71", "1", "72", "2", "73", "3",
            "0", "VERTEX", "8", "KAFES", "70", "128", "71", "1", "72", "3", "73", "4",
            "0", "SEQEND",
        )
        val entities = DxfReader.read(mesh.inputStream()).entities
        assertEquals(2, entities.size, "a polyface mesh came back as something else")
        for (entity in entities) {
            val face = assertIs<DxfEntity.Face>(entity)
            assertEquals(3, face.points.size)
            assertEquals("KAFES", face.layer)
        }
        // The corners were looked up, not invented: one of them is 500 up.
        assertTrue(entities.flatMap { assertIs<DxfEntity.Face>(it).points }.any { it.z == 500.0 })
    }

    @Test
    fun `an ordinary polyline is still an ordinary polyline`() {
        val flat = dxf(
            "0", "POLYLINE", "8", "0", "70", "1",
            "0", "VERTEX", "8", "0", "10", "0.0", "20", "0.0",
            "0", "VERTEX", "8", "0", "10", "1000.0", "20", "0.0",
            "0", "VERTEX", "8", "0", "10", "1000.0", "20", "1000.0",
            "0", "SEQEND",
        )
        val line = assertIs<DxfEntity.Polyline>(DxfReader.read(flat.inputStream()).entities.single())
        assertEquals(3, line.vertices.size)
        assertTrue(line.closed)
    }

    // --- and what the whole app does with such a file ------------------------

    @Test
    fun `a DXF that is only 3D geometry opens instead of reporting nothing to draw`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("model.dxf", oneFace),
        ).value
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        // This is the failure the owner saw: opened, and then told there was
        // nothing in it.
        val document = assertIs<StoreResult.Success<DrawingDocument>>(
            project.openAsDrawing(),
            "a drawing made only of 3D faces was refused as empty",
        ).value
        assertEquals(1, document.entityCount)
    }

    @Test
    fun `its faces reach the 3D view, standing where they were drawn`() {
        val drawing = DxfReader.read(oneFace.inputStream())
        assertTrue(drawing.hasModel, "a drawing of 3D faces does not know it is a model")

        val faces = drawing.toFaces()
        assertEquals(1, faces.size)
        assertEquals(FaceKind.MODEL, faces.single().kind)
        assertEquals("KABUK", faces.single().layer)
        assertEquals(1000.0, faces.bounds().min.z, 1e-9)
    }

    @Test
    fun `a flat plan knows it is not a model, so it opens as a plan`() {
        val plan = dxf(
            "0", "LINE", "8", "0",
            "10", "0.0", "20", "0.0", "11", "1000.0", "21", "0.0",
        )
        val drawing = DxfReader.read(plan.inputStream())
        assertTrue(!drawing.hasModel)
        assertTrue(drawing.toFaces().isEmpty())
    }

    @Test
    fun `a face survives being written out and read back`() {
        val drawing = DxfReader.read(oneFace.inputStream())
        val text = com.harmen.pafta.dxf.DxfWriter.writeToString(drawing)
        val again = DxfReader.read(text.byteInputStream())

        val face = assertIs<DxfEntity.Face>(again.entities.single())
        assertEquals(4, face.points.size)
        assertTrue(face.points.all { it.z == 1000.0 })
    }

    @Test
    fun `a 3DSOLID is still counted and named, because that is all we can do`() {
        // ACIS solids are a geometry kernel's worth of work and are not read.
        // What matters is that the file is kept and the user is told what is in
        // it by name — which is what the owner's own file reported.
        val solid = dxf("0", "3DSOLID", "8", "0", "70", "1")
        val drawing = DxfReader.read(solid.inputStream())

        assertTrue(drawing.entities.isEmpty())
        assertTrue(drawing.unsupportedEntityTypes.contains("3DSOLID"))
        assertEquals(1, drawing.entityTypeCounts["3DSOLID"])
    }
}
