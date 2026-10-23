package com.harmen.pafta.project

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A DWG as the app meets it: imported, kept, and opened as a drawing.
 *
 * The reader itself is tested against real AutoCAD files in `core:dwg`. What is
 * tested here is the path around it — that a DWG is taken into a project at all,
 * that one too new to read is still kept so the user can act on the advice, and
 * that one which is not a DWG is caught at the door.
 */
class DwgProjectTest {

    private val dir = createTempDirectory("pafta-dwg").toFile()
    private val store = ProjectStore(dir, clock = { 1_700_000_000_000 })

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** The six bytes a DWG starts with, then enough padding to be a file. */
    private fun dwgHeader(version: String) = version.toByteArray() + ByteArray(4096)

    @Test
    fun `acilamayan surumdeki DWG ice aktarilir ve dosya kaybolmaz`() {
        val entry = store.import("plan.dwg", dwgHeader("AC1021")).valueOrNull()
        assertNotNull(entry, "DWG içe aktarılamadı")
        assertEquals(FileFormat.DWG, entry.format)
        assertTrue(entry.file.exists(), "dosya saklanmamış")
        assertEquals(1, store.list().size)
    }

    @Test
    fun `acilamayan surumdeki DWG acilirken yil soylenir`() {
        val entry = store.import("plan.dwg", dwgHeader("AC1021")).valueOrNull()!!
        val project = store.open(entry.file).valueOrNull()
        assertNotNull(project, "proje açılamadı")

        val failure = assertIs<StoreResult.Failure>(project.openAsDrawing()).failure
        val unreadable = assertIs<StoreFailure.Unreadable>(failure)
        assertEquals(FileFormat.DWG, unreadable.format)
        assertEquals(UnreadableReason.FORMAT_NOT_SUPPORTED, unreadable.reason)
        // The year is what makes the advice usable: it is the one in the user's
        // own "Save as" list.
        assertEquals(2007, unreadable.formatYear)
    }

    @Test
    fun `DWG olmayan ama DWG adi tasiyan dosya kapida reddedilir`() {
        val outcome = store.import("plan.dwg", ByteArray(2000) { 7 })
        val failure = assertIs<StoreResult.Failure>(outcome).failure
        val unreadable = assertIs<StoreFailure.Unreadable>(failure)
        assertEquals(FileFormat.DWG, unreadable.format)
        assertEquals(UnreadableReason.MALFORMED, unreadable.reason)
        assertEquals(0, store.list().size)
    }

    @Test
    fun `okunabilir surumde ama bozuk bir DWG dosya olarak saklanir`() {
        // An AutoCAD 2000 header with nothing behind it: a version PAFTA reads,
        // a file it cannot. The file is kept — refusing it would cost the user
        // the file and tell them nothing.
        val entry = store.import("eski.dwg", dwgHeader("AC1015")).valueOrNull()
        assertNotNull(entry, "okunabilir sürümdeki bozuk DWG içe aktarılamadı")
        val project = store.open(entry.file).valueOrNull()!!
        val failure = assertIs<StoreResult.Failure>(project.openAsDrawing()).failure
        assertEquals(UnreadableReason.MALFORMED, assertIs<StoreFailure.Unreadable>(failure).reason)
    }
}
