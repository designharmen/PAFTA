package com.harmen.pafta.project

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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

/** The converter: every format that can become faces, becoming them. */
class ConverterTest {

    private lateinit var root: java.io.File
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("pafta-convert").toFile()
        store = ProjectStore(root)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun opened(name: String, payload: ByteArray): PaftaProject {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(store.import(name, payload)).value
        return assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
    }

    private val plyBytes = """
        ply
        format ascii 1.0
        element vertex 3
        property float x
        property float y
        property float z
        element face 1
        property list uchar int vertex_indices
        end_header
        0 0 0
        1000 0 0
        1000 1000 0
        3 0 1 2
    """.trimIndent().toByteArray()

    private fun glbBytes(): ByteArray {
        val binary = java.nio.ByteBuffer.allocate(3 * 12 + 3 * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        binary.putFloat(0f).putFloat(0f).putFloat(0f)
        binary.putFloat(1000f).putFloat(0f).putFloat(0f)
        binary.putFloat(1000f).putFloat(1000f).putFloat(0f)
        binary.putShort(0).putShort(1).putShort(2)

        val json = """
        {"asset":{"version":"2.0"},"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
         "meshes":[{"name":"kutu","primitives":[{"attributes":{"POSITION":0},"indices":1,"mode":4}]}],
         "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                      {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}],
         "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                        {"buffer":0,"byteOffset":36,"byteLength":6}],
         "buffers":[{"byteLength":42}]}
        """.trimIndent()

        fun pad(bytes: ByteArray, filler: Byte): ByteArray =
            bytes + ByteArray((4 - bytes.size % 4) % 4) { filler }

        val jsonChunk = pad(json.toByteArray(), ' '.code.toByte())
        val binChunk = pad(binary.array(), 0)
        val total = 12 + 8 + jsonChunk.size + 8 + binChunk.size

        val out = java.nio.ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("glTF".toByteArray()).putInt(2).putInt(total)
        out.putInt(jsonChunk.size).putInt(0x4E4F534A).put(jsonChunk)
        out.putInt(binChunk.size).putInt(0x004E4942).put(binChunk)
        return out.array()
    }

    @Test
    fun `a PLY file is converted and opens with no user action at all`() {
        val model = assertIs<StoreResult.Success<ModelDocument>>(
            opened("tarama.ply", plyBytes).openAsModel(),
        ).value
        assertEquals(1, model.faces.size)
        assertEquals(1000.0, model.bounds.size.x, 1e-6)
    }

    @Test
    fun `a GLB file is converted, and keeps the names its parts were given`() {
        val model = assertIs<StoreResult.Success<ModelDocument>>(
            opened("model.glb", glbBytes()).openAsModel(),
        ).value
        assertEquals(1, model.faces.size)
        assertEquals(listOf("kutu"), model.groups)
    }

    @Test
    fun `a gltf that needs a second file says so, by name, rather than showing nothing`() {
        val needsBin = """
        {"asset":{"version":"2.0"},"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
         "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"mode":4}]}],
         "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                      {"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}],
         "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                        {"buffer":0,"byteOffset":36,"byteLength":6}],
         "buffers":[{"uri":"scene.bin","byteLength":42}]}
        """.trimIndent().toByteArray()

        val unreadable = assertIs<StoreFailure.Unreadable>(
            assertIs<StoreResult.Failure>(opened("sahne.gltf", needsBin).openAsModel()).failure,
        )
        assertEquals(FileFormat.GLTF, unreadable.format)
        assertEquals(UnreadableReason.NEEDS_ANOTHER_FILE, unreadable.reason)
    }

    @Test
    fun `every format the converter claims it can read, it can`() {
        // The one place the list of what opens and the code that opens it are
        // checked against each other. A format marked readable with no entry in
        // the converter would be a file that imports, opens, and shows nothing.
        val samples = mapOf(
            FileFormat.STL to ("p.stl" to asciiStl),
            FileFormat.OBJ to ("p.obj" to objBytes2),
            FileFormat.PLY to ("p.ply" to plyBytes),
            FileFormat.GLB to ("p.glb" to glbBytes()),
        )
        for ((format, sample) in samples) {
            assertTrue(format.readable, "${format.name} is not marked readable")
            val (name, payload) = sample
            assertIs<StoreResult.Success<ModelDocument>>(
                opened(name, payload).openAsModel(),
                "${format.name} is marked readable but the converter has no entry for it",
            )
        }
    }

    private val asciiStl = (
        "solid x\nfacet normal 0 0 1\nouter loop\n" +
            "vertex 0 0 0\nvertex 1000 0 0\nvertex 0 1000 0\n" +
            "endloop\nendfacet\nendsolid x"
        ).toByteArray()

    private val objBytes2 = "v 0 0 0\nv 1000 0 0\nv 1000 1000 0\nf 1 2 3\n".toByteArray()
}

/** Recognising a file by what is in it, not by what it is called. */
class SniffTest {

    private lateinit var root: java.io.File
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("pafta-sniff").toFile()
        store = ProjectStore(root)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private val objText = "# bir ev\nv 0 0 0\nv 1000 0 0\nv 1000 1000 0\nf 1 2 3\n"
    private val stlText = "solid ev\nfacet normal 0 0 1\nouter loop\n" +
        "vertex 0 0 0\nvertex 1000 0 0\nvertex 0 1000 0\nendloop\nendfacet\nendsolid ev"
    private val plyText = "ply\nformat ascii 1.0\nelement vertex 3\n" +
        "property float x\nproperty float y\nproperty float z\n" +
        "element face 1\nproperty list uchar int vertex_indices\nend_header\n" +
        "0 0 0\n1 0 0\n1 1 0\n3 0 1 2\n"

    @Test
    fun `each readable format is recognised from its own bytes`() {
        assertEquals(FileFormat.OBJ, FileFormat.sniff(objText.toByteArray()))
        assertEquals(FileFormat.STL, FileFormat.sniff(stlText.toByteArray()))
        assertEquals(FileFormat.PLY, FileFormat.sniff(plyText.toByteArray()))

        val glb = "glTF".toByteArray() + ByteArray(32)
        assertEquals(FileFormat.GLB, FileFormat.sniff(glb))

        val gltf = """{"asset":{"version":"2.0"},"meshes":[]}""".toByteArray()
        assertEquals(FileFormat.GLTF, FileFormat.sniff(gltf))

        val dxf = "  0\nSECTION\n  2\nENTITIES\n  0\nENDSEC\n  0\nEOF\n".toByteArray()
        assertEquals(FileFormat.DXF, FileFormat.sniff(dxf))
    }

    @Test
    fun `a binary STL is recognised by its size, not by the word at the front`() {
        val buffer = java.nio.ByteBuffer.allocate(84 + 50 * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // The trap: a binary file whose header begins with `solid`.
        buffer.put("solid exported by something".toByteArray().copyOf(80))
        buffer.putInt(2)
        repeat(2) {
            repeat(12) { buffer.putFloat(1f) }
            buffer.putShort(0)
        }
        assertEquals(FileFormat.STL, FileFormat.sniff(buffer.array()))
    }

    @Test
    fun `something that is not a model at all is not guessed at`() {
        assertNull(FileFormat.sniff("merhaba dünya".toByteArray()))
        assertNull(FileFormat.sniff(ByteArray(0)))
        assertNull(FileFormat.sniff(ByteArray(200) { 0 }))
    }

    @Test
    fun `a file whose name says nothing is imported by what is in it`() {
        // What a download or a cloud drive actually hands over: no extension.
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("indirilen dosya", objText.toByteArray()),
        ).value
        assertEquals(FileFormat.OBJ, entry.format)

        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertEquals(1, assertIs<StoreResult.Success<ModelDocument>>(opened.openAsModel()).value.faces.size)
    }

    @Test
    fun `the name still wins when it says something`() {
        // An OBJ named .obj stays an OBJ; sniffing is the fallback, not a vote.
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("ev.obj", objText.toByteArray()),
        ).value
        assertEquals(FileFormat.OBJ, entry.format)
        assertEquals("ev.obj", entry.manifest.source.fileName)
    }

    @Test
    fun `a file with neither a usable name nor recognisable contents is still refused`() {
        val outcome = store.import("birsey", ByteArray(500) { 3 })
        assertIs<StoreFailure.UnknownFormat>(assertIs<StoreResult.Failure>(outcome).failure)
    }

    @Test
    fun `a DWG is recognised by its version string`() {
        // Every DWG opens with the AutoCAD release that wrote it. Recognising it
        // from the bytes is what lets a file called `plan` with no extension —
        // which is what a phone's download folder often holds — still open.
        assertEquals(FileFormat.DWG, FileFormat.sniff("AC1032".toByteArray() + ByteArray(300) { 0 }))
        assertEquals(FileFormat.DWG, FileFormat.sniff("AC1015".toByteArray() + ByteArray(300) { 0 }))
        // `AC10` with something else after it is not a version string.
        assertNull(FileFormat.sniff("AC10xx".toByteArray() + ByteArray(300) { 0 }))
    }
}
