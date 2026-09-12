package com.harmen.pafta.project

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The known-good plan, taken through the whole chain the app takes it through:
 * import into a `.pafta` container, reopen, and open as a drawing.
 *
 * This is as far as a test can follow the device path — everything after it is
 * Compose drawing to a screen. If this passes and the tablet still shows
 * nothing, the fault is in the viewport, not in reading or storing the file.
 */
class SamplePlanImportTest {

    private val sample: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "ornek/kat-plani.dxf") }
        .firstOrNull { it.isFile }
        ?: error("ornek/kat-plani.dxf not found")

    @Test
    fun `the sample plan imports, reopens and draws`() {
        val root = Files.createTempDirectory("pafta-sample").toFile()
        val store = ProjectStore(root)

        val entry = store.import("kat-plani.dxf", sample.readBytes()).valueOrNull()
        assertNotNull(entry, "import failed")
        assertEquals(FileFormat.DXF, entry.format)

        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(project.openAsDrawing()).value

        // Exactly what the plan is made of, counted rather than guessed: 2 wall
        // outlines + 8 interior wall lines + 4 doors expanded to 2 each +
        // 4 windows of 3 lines + 4 pieces of furniture + 6 labels.
        assertEquals(40, doc.entityCount)
        assertTrue(!doc.isEmpty)
        // The palette the user will see, built from the file's own layers.
        assertTrue(
            doc.layers.map { it.name }.containsAll(listOf("DUVAR", "KAPI", "PENCERE", "METIN")),
            "palette was ${doc.layers.map { it.name }}",
        )
        assertTrue(doc.layers.all { it.visible }, "every layer should start visible")

        val size = doc.bounds.size
        assertEquals(12000.0, size.x, 1.0)

        root.deleteRecursively()
    }
}
