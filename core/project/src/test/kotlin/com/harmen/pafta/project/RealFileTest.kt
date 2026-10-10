package com.harmen.pafta.project

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The same three files the owner is given to try, read by the same code the
 * tablet runs.
 *
 * Every other test in this module builds its sample inline, which proves the
 * reader against what the test author believed the format was. These are whole
 * files of a small house — four walls, a floor slab and a pitched roof — and
 * they are in the repository, so "it does not open on the device" can always be
 * separated from "it does not open at all".
 */
class RealFileTest {

    private lateinit var root: java.io.File
    private lateinit var store: ProjectStore

    @BeforeTest
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("pafta-real").toFile()
        store = ProjectStore(root)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun bytesOf(name: String): ByteArray =
        assertNotNull(
            javaClass.classLoader?.getResourceAsStream(name),
            "$name is missing from the test resources",
        ).use { it.readBytes() }

    /** Imports and opens a file exactly as the app does, and hands back the model. */
    private fun opened(name: String): ModelDocument {
        val bytes = bytesOf(name)
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import(name, bytes),
            "$name would not import",
        ).value
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        return assertIs<StoreResult.Success<ModelDocument>>(
            project.openAsModel(),
            "$name imported but would not open",
        ).value
    }

    @Test
    fun `the sample house opens from OBJ, and is the size it was built`() {
        val model = opened("ornek-ev.obj")
        assertTrue(model.faces.isNotEmpty())
        // 6m x 4m on plan, floor slab 150 below, ridge at 4.3m.
        assertEquals(6000.0, model.bounds.size.x, 1.0)
        assertEquals(4000.0, model.bounds.size.y, 1.0)
        assertEquals(-150.0, model.bounds.min.z, 1.0)
        assertEquals(4300.0, model.bounds.max.z, 1.0)
        // Its two named parts came through.
        assertTrue(model.groups.contains("duvarlar"))
        assertTrue(model.groups.contains("cati"))
    }

    @Test
    fun `the same house opens from STL`() {
        val model = opened("ornek-ev.stl")
        assertTrue(model.faces.isNotEmpty())
        assertEquals(6000.0, model.bounds.size.x, 1.0)
        assertEquals(4300.0, model.bounds.max.z, 1.0)
    }

    @Test
    fun `the same house opens from GLB`() {
        val model = opened("ornek-ev.glb")
        assertTrue(model.faces.isNotEmpty())
        assertEquals(6000.0, model.bounds.size.x, 1.0)
        assertEquals(4300.0, model.bounds.max.z, 1.0)
        assertEquals(listOf("ev"), model.groups)
    }

    @Test
    fun `all three describe the same house`() {
        val sizes = listOf("ornek-ev.obj", "ornek-ev.stl", "ornek-ev.glb").map {
            opened(it).bounds.size
        }
        for (size in sizes) {
            assertEquals(sizes[0].x, size.x, 1.0)
            assertEquals(sizes[0].y, size.y, 1.0)
            assertEquals(sizes[0].z, size.z, 1.0)
        }
    }

    @Test
    fun `each one is recognised from its bytes even with its name taken away`() {
        assertEquals(FileFormat.OBJ, FileFormat.sniff(bytesOf("ornek-ev.obj")))
        assertEquals(FileFormat.STL, FileFormat.sniff(bytesOf("ornek-ev.stl")))
        assertEquals(FileFormat.GLB, FileFormat.sniff(bytesOf("ornek-ev.glb")))

        // And one imported under a name that says nothing still opens.
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("indirilen", bytesOf("ornek-ev.glb")),
        ).value
        assertEquals(FileFormat.GLB, entry.format)
    }
}
