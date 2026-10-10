package com.harmen.pafta.project

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Importing somebody else's 3D model, and opening it. */
class ModelDocumentTest {

    private lateinit var root: java.io.File
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("pafta-model").toFile()
        store = ProjectStore(root)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun binaryStl(count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(84 + 50 * count).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(count)
        repeat(count) { i ->
            buffer.putFloat(0f).putFloat(0f).putFloat(1f)
            buffer.putFloat(0f).putFloat(0f).putFloat(i.toFloat())
            buffer.putFloat(1000f).putFloat(0f).putFloat(i.toFloat())
            buffer.putFloat(0f).putFloat(1000f).putFloat(i.toFloat())
            buffer.putShort(0)
        }
        return buffer.array()
    }

    private val objBytes = """
        o govde
        v 0 0 0
        v 1000 0 0
        v 1000 1000 0
        v 0 1000 0
        f 1 2 3 4
        o cati
        v 0 0 500
        v 1000 0 500
        v 1000 1000 500
        f 5 6 7
    """.trimIndent().toByteArray()

    @Test
    fun `an STL file is imported and opens as a model`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("parca.stl", binaryStl(4)),
        ).value
        assertEquals(FileFormat.STL, entry.format)

        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val model = assertIs<StoreResult.Success<ModelDocument>>(opened.openAsModel()).value

        assertEquals(4, model.faces.size)
        assertEquals(4, model.facesInFile)
        assertTrue(!model.isTruncated)
        assertEquals(1000.0, model.bounds.size.x, 1e-6)
    }

    @Test
    fun `an OBJ file keeps the names its parts were given, as layers`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("model.obj", objBytes),
        ).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val model = assertIs<StoreResult.Success<ModelDocument>>(opened.openAsModel()).value

        assertEquals(2, model.faces.size)
        assertEquals(listOf("govde", "cati"), model.groups)
        assertEquals("govde", model.faces[0].layer)
        assertEquals("cati", model.faces[1].layer)
    }

    @Test
    fun `a model's parts become palette rows, and a hidden one stays hidden`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("model.obj", objBytes),
        ).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val model = assertIs<StoreResult.Success<ModelDocument>>(opened.openAsModel()).value

        val fresh = model.layerStates(emptyList())
        assertEquals(listOf("govde", "cati"), fresh.map { it.name })
        assertTrue(fresh.all { it.visible })

        val remembered = model.layerStates(listOf(LayerState("cati", "cati", visible = false)))
        assertTrue(remembered.first { it.id == "govde" }.visible)
        assertTrue(!remembered.first { it.id == "cati" }.visible)
    }

    @Test
    fun `a model with no part names still gets one row to turn it off with`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("parca.stl", binaryStl(2)),
        ).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val model = assertIs<StoreResult.Success<ModelDocument>>(opened.openAsModel()).value

        assertEquals(1, model.layerStates(emptyList()).size)
        assertEquals(DrawnShape.LAYER_MODEL, model.layerStates(emptyList()).single().name)
    }

    @Test
    fun `a broken model file is refused with what was wrong, not with an empty view`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("bozuk.obj", "v 0 0 0\nf 1 2 9".toByteArray()),
        ).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        val failure = assertIs<StoreResult.Failure>(opened.openAsModel()).failure
        val unreadable = assertIs<StoreFailure.Unreadable>(failure)
        assertEquals(FileFormat.OBJ, unreadable.format)
        assertEquals(UnreadableReason.MALFORMED, unreadable.reason)
    }

    @Test
    fun `a format with no reader yet says so rather than pretending`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("ev.skp", ByteArray(64) { 7 }),
        ).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        val unreadable = assertIs<StoreFailure.Unreadable>(
            assertIs<StoreResult.Failure>(opened.openAsModel()).failure,
        )
        assertEquals(FileFormat.SKP, unreadable.format)
        assertEquals(UnreadableReason.NO_VIEWER_YET, unreadable.reason)
    }

    @Test
    fun `STL and OBJ now say they can be opened, and the list knows it`() {
        assertTrue(FileFormat.STL.readable)
        assertTrue(FileFormat.OBJ.readable)
        assertEquals(ViewerKind.MODEL, FileFormat.STL.viewer)
        // And the ones that still cannot be read have not quietly changed.
        assertTrue(!FileFormat.DWG.readable)
        assertTrue(!FileFormat.SKP.readable)
    }

    @Test
    fun `a drawing is not a model and says so`() {
        val dxf = listOf(
            "0", "SECTION", "2", "ENTITIES",
            "0", "LINE", "8", "0", "10", "0.0", "20", "0.0", "11", "100.0", "21", "0.0",
            "0", "ENDSEC", "0", "EOF",
        ).joinToString("\n", postfix = "\n").toByteArray()

        val entry = assertIs<StoreResult.Success<ProjectEntry>>(store.import("plan.dxf", dxf)).value
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        assertIs<StoreResult.Failure>(opened.openAsModel())
        assertIs<StoreResult.Success<DrawingDocument>>(opened.openAsDrawing())
    }
}
