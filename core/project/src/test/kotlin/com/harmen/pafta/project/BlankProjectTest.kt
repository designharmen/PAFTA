package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec3
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Starting a project here rather than importing one.
 *
 * The whole point is that an empty sheet is not an error: the reader refuses a
 * file that holds nothing drawable, and a project with nothing drawn in it yet
 * looks exactly like one until something says otherwise.
 */
class BlankProjectTest {

    private lateinit var root: File
    private var now = 1_700_000_000_000L
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("pafta-blank").toFile()
        store = ProjectStore(root, clock = { now })
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun created(name: String = "Yeni proje"): ProjectEntry =
        assertIs<StoreResult.Success<ProjectEntry>>(store.create(name)).value

    @Test
    fun `a new project is written, listed, and named what was asked for`() {
        val entry = created("Villa zemin kat")

        assertTrue(entry.file.exists())
        assertEquals("Villa zemin kat", entry.name)
        assertEquals(listOf("Villa zemin kat"), store.list().map { it.name })
    }

    @Test
    fun `a name already taken gets a number rather than a second project of the same name`() {
        created("Yeni proje")
        val second = created("Yeni proje")

        assertEquals("Yeni proje-2", second.name)
        // Two rows, two different names: the library is never two identical lines.
        assertEquals(2, store.list().size)
        assertEquals(2, store.list().map { it.name }.distinct().size)
    }

    @Test
    fun `a project with no name is refused rather than written as 'project'`() {
        val outcome = store.create("   ")
        assertEquals(
            IoCause.NAME_REQUIRED,
            assertIs<StoreFailure.Io>(assertIs<StoreResult.Failure>(outcome).failure).cause,
        )
    }

    @Test
    fun `an empty new project opens instead of failing as unreadable`() {
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(created().file)).value
        assertTrue(opened.manifest.blank)

        val drawing = assertIs<StoreResult.Success<DrawingDocument>>(opened.openAsDrawing()).value
        assertEquals(0, drawing.entityCount)
    }

    @Test
    fun `it opens on a sheet to draw on, not on a view of nothing`() {
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(created().file)).value
        val drawing = assertIs<StoreResult.Success<DrawingDocument>>(opened.openAsDrawing()).value

        assertFalse(drawing.bounds.isEmpty)
        assertEquals(20_000.0, drawing.bounds.size.x, 1e-6)
        assertEquals(15_000.0, drawing.bounds.size.y, 1e-6)
    }

    @Test
    fun `once a wall is drawn the view frames the wall, not the empty sheet`() {
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(created().file)).value
        val withWall = opened.copy(
            shapes = listOf(
                DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(3000.0, 0.0, 0.0)),
            ),
        )

        val drawing = assertIs<StoreResult.Success<DrawingDocument>>(withWall.openAsDrawing()).value
        assertEquals(3000.0, drawing.bounds.size.x, 1.0)
    }

    @Test
    fun `an imported file that holds nothing drawable is still reported, not opened blank`() {
        // The flag must not have turned the "there is nothing in your file"
        // answer off for the case it was written for.
        val empty = newProject(
            projectName = "Bos",
            fileName = "bos.dxf",
            payload = com.harmen.pafta.dxf.DxfWriter
                .writeToString(com.harmen.pafta.dxf.DxfDrawing())
                .toByteArray(),
            nowEpochMs = now,
        )
        assertFalse(empty.manifest.blank)
        assertIs<StoreResult.Failure>(empty.openAsDrawing())
    }

    @Test
    fun `what was drawn on an empty sheet is still there after saving and reopening`() {
        val entry = created()
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val drawn = opened.copy(
            shapes = listOf(
                DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            ),
        )
        assertIs<StoreResult.Success<PaftaProject>>(store.save(drawn, entry.file))

        val reopened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertEquals(1, reopened.shapes.size)
        assertTrue(reopened.manifest.blank)
    }
}
