package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfWriter
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A small but valid DXF: one room outline. */
internal fun sampleDxfBytes(layer: String = "WALLS"): ByteArray = DxfWriter.writeToString(
    com.harmen.pafta.dxf.DxfDrawing(
        layers = listOf(com.harmen.pafta.dxf.DxfLayer("0"), com.harmen.pafta.dxf.DxfLayer(layer)),
        entities = listOf(
            DxfEntity.Polyline(
                layer,
                listOf(Vec2(0.0, 0.0), Vec2(5500.0, 0.0), Vec2(5500.0, 4500.0), Vec2(0.0, 4500.0)),
                closed = true,
            ),
            DxfEntity.Line(layer, Vec3.ZERO, Vec3(5500.0, 0.0, 0.0)),
        ),
        insUnits = com.harmen.pafta.dxf.DxfInsUnits.MILLIMETRES,
    ),
).toByteArray()

class ProjectStoreTest {

    private lateinit var root: File
    private var now = 1_700_000_000_000L
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("pafta-store").toFile()
        store = ProjectStore(root, clock = { now })
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `importing a dxf creates a project the library can list`() {
        val result = store.import("ground-floor.dxf", sampleDxfBytes())
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(result).value

        assertTrue(entry.file.exists())
        assertEquals("ground-floor.pafta", entry.file.name)
        assertEquals("ground-floor", entry.name)
        assertEquals(FileFormat.DXF, entry.format)
        assertEquals(listOf("ground-floor"), store.list().map { it.name })
    }

    @Test
    fun `the imported payload is kept byte for byte`() {
        val bytes = sampleDxfBytes()
        val entry = store.import("plan.dxf", bytes).valueOrNull()!!
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertTrue(bytes.contentEquals(opened.payload))
        assertEquals("plan.dxf", opened.manifest.source.fileName)
    }

    @Test
    fun `importing from a stream works the same way`() {
        val entry = store
            .import("stream.dxf", ByteArrayInputStream(sampleDxfBytes()))
            .valueOrNull()
        assertNotNull(entry)
        assertEquals("stream.pafta", entry.file.name)
    }

    @Test
    fun `a second import of the same name does not overwrite the first`() {
        val a = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        val b = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        val c = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!

        assertEquals("plan.pafta", a.file.name)
        assertEquals("plan-2.pafta", b.file.name)
        assertEquals("plan-3.pafta", c.file.name)
        assertTrue(a.file.exists() && b.file.exists() && c.file.exists())
        assertEquals(3, store.list().size)
    }

    @Test
    fun `an unknown extension is refused with a message naming it`() {
        val f = assertIs<StoreResult.Failure>(store.import("notes.docx", byteArrayOf(1, 2, 3)))
        val failure = assertIs<StoreFailure.UnknownFormat>(f.failure)
        assertEquals("docx", failure.extension)
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `a file with no extension is refused rather than guessed at`() {
        val f = assertIs<StoreResult.Failure>(store.import("drawing", byteArrayOf(1)))
        assertIs<StoreFailure.UnknownFormat>(f.failure)
    }

    @Test
    fun `an empty file is refused`() {
        val f = assertIs<StoreResult.Failure>(store.import("empty.dxf", ByteArray(0)))
        assertEquals(StoreFailure.EmptyFile, f.failure)
    }

    @Test
    fun `a file over the limit is refused with both sizes in the message`() {
        val small = ProjectStore(root, clock = { now }, importLimitBytes = 1024)
        val f = assertIs<StoreResult.Failure>(small.import("big.dxf", ByteArray(2048)))
        val failure = assertIs<StoreFailure.TooLarge>(f.failure)
        assertEquals(2048L, failure.sizeBytes)
        assertEquals(1024L, failure.limitBytes)
    }

    @Test
    fun `a dxf that will not parse is refused at import instead of opening empty`() {
        val f = assertIs<StoreResult.Failure>(
            store.import("broken.dxf", "0\nSECTION\n2\n".toByteArray()),
        )
        val failure = assertIs<StoreFailure.Unreadable>(f.failure)
        assertEquals(FileFormat.DXF, failure.format)
        assertEquals(UnreadableReason.MALFORMED, failure.reason)
        assertEquals(emptyList(), store.list(), "nothing should be left behind")
    }

    @Test
    fun `a dxf with no drawable entities is refused`() {
        val f = assertIs<StoreResult.Failure>(store.import("blank.dxf", "0\nEOF\n".toByteArray()))
        val failure = assertIs<StoreFailure.Unreadable>(f.failure)
        assertEquals(UnreadableReason.NO_DRAWABLE_CONTENT, failure.reason)
    }

    @Test
    fun `a format PAFTA cannot draw yet still imports so the file is safe`() {
        // GLB has no viewer until the 3D phase, but importing must still work.
        val entry = store.import("model.glb", byteArrayOf(1, 2, 3, 4)).valueOrNull()
        assertNotNull(entry)
        assertEquals(FileFormat.GLB, entry.format)
        assertFalse(FileFormat.GLB.readable)
    }

    @Test
    fun `the library is listed newest first`() {
        now = 1_000
        store.import("oldest.dxf", sampleDxfBytes())
        now = 3_000
        store.import("newest.dxf", sampleDxfBytes())
        now = 2_000
        store.import("middle.dxf", sampleDxfBytes())

        assertEquals(listOf("newest", "middle", "oldest"), store.list().map { it.name })
    }

    @Test
    fun `one corrupt project does not make the library unlistable`() {
        store.import("good.dxf", sampleDxfBytes())
        File(root, "corrupt.pafta").writeText("this is not a zip at all")
        File(root, "notes.txt").writeText("ignored entirely")

        assertEquals(listOf("good"), store.list().map { it.name })
        assertEquals(listOf("corrupt.pafta"), store.listUnreadable().map { it.name })
    }

    @Test
    fun `saving stamps the modified time and the library reorders`() {
        now = 1_000
        val a = store.import("a.dxf", sampleDxfBytes()).valueOrNull()!!
        now = 2_000
        store.import("b.dxf", sampleDxfBytes())
        assertEquals(listOf("b", "a"), store.list().map { it.name })

        now = 3_000
        val opened = store.open(a.file).valueOrNull()!!
        val saved = assertIs<StoreResult.Success<PaftaProject>>(store.save(opened, a.file)).value
        assertEquals(3_000L, saved.manifest.modifiedAtEpochMs)
        assertEquals(listOf("a", "b"), store.list().map { it.name })
    }

    @Test
    fun `saving preserves edits made to the overlay`() {
        val entry = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        val opened = store.open(entry.file).valueOrNull()!!

        val edited = opened.copy(
            annotations = listOf(Annotation.Pin("p1", Vec3(1.0, 2.0, 0.0), "check this")),
            layers = listOf(LayerState("WALLS", "WALLS", visible = false, opacity = 0.5)),
        )
        store.save(edited, entry.file)

        val reread = store.open(entry.file).valueOrNull()!!
        assertEquals(1, reread.annotations.size)
        assertEquals("check this", (reread.annotations.single() as Annotation.Pin).label)
        assertFalse(reread.layers.single().visible)
        assertEquals(50, reread.layers.single().opacityPercent)
    }

    @Test
    fun `renaming moves the file and updates the display name`() {
        val entry = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        val renamed = assertIs<StoreResult.Success<ProjectEntry>>(
            store.rename(entry, "Villa Ground Floor"),
        ).value

        assertEquals("Villa Ground Floor", renamed.name)
        assertEquals("Villa Ground Floor.pafta", renamed.file.name)
        assertFalse(entry.file.exists(), "the old file should be gone")
        assertEquals(listOf("Villa Ground Floor"), store.list().map { it.name })
    }

    @Test
    fun `renaming onto a taken name keeps the file but applies the new name`() {
        store.import("taken.dxf", sampleDxfBytes())
        val other = store.import("other.dxf", sampleDxfBytes()).valueOrNull()!!

        val renamed = store.rename(other, "taken").valueOrNull()!!
        assertEquals("taken", renamed.name)
        assertEquals("other.pafta", renamed.file.name, "must not clobber taken.pafta")
        assertTrue(File(root, "taken.pafta").exists())
        assertEquals(2, store.list().size)
    }

    @Test
    fun `renaming to blank is refused`() {
        val entry = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        assertIs<StoreResult.Failure>(store.rename(entry, "   "))
        assertTrue(entry.file.exists())
    }

    @Test
    fun `deleting removes the project from the library`() {
        val entry = store.import("plan.dxf", sampleDxfBytes()).valueOrNull()!!
        assertTrue(store.delete(entry.file))
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `opening something that is not a project fails with an explanation`() {
        val stray = File(root, "stray.pafta").apply { writeText("nope") }
        val f = assertIs<StoreResult.Failure>(store.open(stray))
        assertEquals(IoCause.NOT_A_PROJECT, assertIs<StoreFailure.Io>(f.failure).cause)
    }

    @Test
    fun `a name with path separators cannot escape the library directory`() {
        val entry = store
            .import("x.dxf", sampleDxfBytes(), projectName = "../../etc/passwd")
            .valueOrNull()!!
        assertEquals(root, entry.file.parentFile)
        assertFalse(entry.file.name.contains('/'))
        assertFalse(entry.file.name.contains(".."))
    }

    @Test
    fun `awkward names are sanitised into usable file names`() {
        assertEquals("project", ProjectStore.sanitise(""))
        assertEquals("project", ProjectStore.sanitise("..."))
        assertEquals("a-b", ProjectStore.sanitise("a/b"))
        assertEquals("Unit 101 - Lvl 2", ProjectStore.sanitise("Unit 101 – Lvl 2"))
        assertEquals("plan", ProjectStore.sanitise("  plan  "))
        assertTrue(ProjectStore.sanitise("x".repeat(300)).length <= 80)
    }

    @Test
    fun `the default project name drops the extension`() {
        assertEquals("ground-floor", ProjectStore.defaultProjectName("ground-floor.dxf"))
        assertEquals("archive.tar", ProjectStore.defaultProjectName("archive.tar.gz"))
        assertEquals("Untitled", ProjectStore.defaultProjectName(".dxf"))
    }

    @Test
    fun `the store creates its directory if it does not exist`() {
        val nested = File(root, "deep/nested/library")
        val s = ProjectStore(nested, clock = { now })
        assertTrue(nested.isDirectory)
        assertEquals(emptyList(), s.list())
    }
}

class StoreFailureTest {

    @Test
    fun `failures carry data rather than a message, so wording stays in Turkish resources`() {
        // A regression guard for the Turkish-only rule: if someone reintroduces a
        // `message` on a failure type, it will be English prose on a Turkish
        // screen. The structured fields below are all the UI needs.
        val unknown = StoreFailure.UnknownFormat("docx")
        assertEquals("docx", unknown.extension)

        val tooLarge = StoreFailure.TooLarge(2048, 1024)
        assertEquals(2048L, tooLarge.sizeBytes)
        assertEquals(1024L, tooLarge.limitBytes)

        val unreadable = StoreFailure.Unreadable(FileFormat.DWG, UnreadableReason.NO_VIEWER_YET)
        assertEquals(FileFormat.DWG, unreadable.format)
        assertEquals(UnreadableReason.NO_VIEWER_YET, unreadable.reason)

        // The platform's own message is kept for logs only, never for the screen.
        val io = StoreFailure.Io(IoCause.PERMISSION_DENIED, diagnostic = "EACCES")
        assertEquals(IoCause.PERMISSION_DENIED, io.cause)
        assertEquals("EACCES", io.diagnostic)
        assertNull(StoreFailure.Io(IoCause.NAME_REQUIRED).diagnostic)
    }

    @Test
    fun `every io cause and unreadable reason is distinct so the ui can map each one`() {
        assertEquals(IoCause.entries.size, IoCause.entries.toSet().size)
        assertEquals(UnreadableReason.entries.size, UnreadableReason.entries.toSet().size)
        assertEquals(6, IoCause.entries.size)
        assertEquals(3, UnreadableReason.entries.size)
    }
}

class FileFormatTest {
    @Test
    fun `formats resolve from a file name or a bare extension`() {
        assertEquals(FileFormat.DXF, FileFormat.of("plan.dxf"))
        assertEquals(FileFormat.DXF, FileFormat.of("PLAN.DXF"))
        assertEquals(FileFormat.DXF, FileFormat.of("dxf"))
        assertEquals(FileFormat.DXF, FileFormat.of(".dxf"))
        assertEquals(FileFormat.GLB, FileFormat.of("/storage/emulated/0/Download/villa.glb"))
        assertEquals(FileFormat.THREE_DS, FileFormat.of("old.3ds"))
        assertEquals(FileFormat.PAFTA, FileFormat.of("project.pafta"))
    }

    @Test
    fun `an unrecognised or missing extension resolves to null`() {
        assertNull(FileFormat.of("notes.docx"))
        assertNull(FileFormat.of(""))
        assertNull(FileFormat.of("."))
        assertNull(FileFormat.of("README"))
    }

    @Test
    fun `only the formats with a viewer are marked readable`() {
        assertTrue(FileFormat.DXF.readable)
        assertFalse(FileFormat.DWG.readable, "DWG needs LibreDWG, a later phase")
        assertFalse(FileFormat.GLB.readable, "glTF needs the Filament phase")
    }

    @Test
    fun `a revit file is recognised so it can be kept rather than refused`() {
        // No free library reads .rvt and none is planned — the route in is
        // Revit's own export. Recognising it is what lets the app take the file
        // into a project and say so, instead of claiming the extension is
        // unknown.
        assertEquals(FileFormat.RVT, FileFormat.of("proje.rvt"))
        assertEquals(FileFormat.RVT, FileFormat.of("PROJE.RVT"))
        assertFalse(FileFormat.RVT.readable)
        assertEquals(ViewerKind.MODEL, FileFormat.RVT.viewer)
    }

    @Test
    fun `formats without a viewer still import so the file is never lost`() {
        val dir = createTempDirectory("pafta-fmt").toFile()
        try {
            val s = ProjectStore(dir, clock = { 1_700_000_000_000 })
            for (name in listOf("plan.dwg", "bina.rvt", "model.ifc", "ev.skp")) {
                val entry = s.import(name, byteArrayOf(1, 2, 3, 4)).valueOrNull()
                assertNotNull(entry, "$name içe aktarılamadı")
                assertFalse(entry.format!!.readable)
            }
            assertEquals(4, s.list().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the picker offers every format except pafta itself`() {
        val offered = FileFormat.importableExtensions
        assertTrue("dxf" in offered)
        assertTrue("ifc" in offered)
        assertFalse(PAFTA_EXTENSION in offered)
    }

    @Test
    fun `a manifest reports its payload format`() {
        val m = newProject("x", "plan.DXF", byteArrayOf(1), 0).manifest
        assertEquals(FileFormat.DXF, m.format)
    }
}
