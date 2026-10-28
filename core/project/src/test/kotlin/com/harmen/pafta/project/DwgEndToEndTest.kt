package com.harmen.pafta.project

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole way through, exactly as the tablet does it.
 *
 * The file is a real AutoCAD 2018 drawing — the one the owner was handed — and
 * it goes in through the same two calls the app makes: import by name and
 * stream, then open. Every other test in this module builds its own example,
 * which proves the reader against what the test's author believed. This one
 * proves the path against the file in the owner's hands.
 */
class DwgEndToEndTest {

    private val dir = createTempDirectory("pafta-dwg-e2e").toFile()
    private val store = ProjectStore(dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun bytes(name: String): ByteArray =
        javaClass.getResourceAsStream("/$name")!!.readBytes()

    @Test
    fun `tabletteki yol ile DWG aktarilir ve cizim olarak acilir`() {
        val name = "ornek-cizim-autocad-2018.dwg"
        val imported = store.import(name, bytes(name).inputStream(), importedFrom = "content://x")
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(imported).value
        assertEquals(FileFormat.DWG, entry.format)
        assertEquals(ViewerKind.DRAWING, entry.format!!.viewer)

        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val document = assertIs<StoreResult.Success<DrawingDocument>>(project.openAsDrawing()).value

        assertTrue(document.entityCount > 100, "çizimde öğe yok: ${document.entityCount}")
        assertTrue(
            document.layers.any { it.name == "Tavolo 3" },
            "katmanlar: ${document.layers.map { it.name }}",
        )
        assertNotNull(document.bounds)
    }

    @Test
    fun `uzantisi olmayan bir DWG de ayni sekilde acilir`() {
        // Android hands over whatever name the app the file came from chose, and
        // a download or a cloud drive routinely hands over no extension at all.
        val imported = store.import("cizim", bytes("ornek-cizim-autocad-2018.dwg").inputStream())
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(imported).value
        assertEquals(FileFormat.DWG, entry.format)
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertIs<StoreResult.Success<DrawingDocument>>(project.openAsDrawing())
    }
}
