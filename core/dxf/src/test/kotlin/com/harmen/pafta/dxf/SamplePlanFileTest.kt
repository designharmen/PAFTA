package com.harmen.pafta.dxf

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The known-good drawing in `ornek/kat-plani.dxf`.
 *
 * It exists because the first device test of the drawing screen was run against
 * a DXF downloaded from a search result, which turned out to hold nothing to
 * draw — so the test said nothing about PAFTA. A file whose contents are known
 * and asserted here removes that whole class of ambiguity: if this one does not
 * draw on a device, the fault is ours.
 *
 * It is also a realistic fixture. Everything a CAD floor plan carries is in it:
 * layers, closed wall outlines, doors placed as block references — rotated and
 * mirrored — windows, furniture and room labels.
 */
class SamplePlanFileTest {

    private val file: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "ornek/kat-plani.dxf") }
        .firstOrNull { it.isFile }
        ?: error("ornek/kat-plani.dxf not found from ${File(".").absolutePath}")

    private val drawing = DxfReader.read(file.inputStream())

    @Test
    fun `it parses, in millimetres`() {
        assertEquals(DxfInsUnits.MILLIMETRES, drawing.insUnits)
    }

    @Test
    fun `it declares the layers a plan is drawn on`() {
        assertTrue(
            drawing.layers.map { it.name }
                .containsAll(listOf("DUVAR", "KAPI", "PENCERE", "MOBILYA", "METIN")),
            "layers were ${drawing.layers.map { it.name }}",
        )
    }

    @Test
    fun `every entity type in it is one the reader models`() {
        assertEquals(emptySet(), drawing.unsupportedEntityTypes)
    }

    @Test
    fun `the four door references are expanded into real geometry`() {
        assertEquals(4, drawing.entityTypeCounts["INSERT"])
        // Nothing is left as a marker: each reference became its block's two
        // entities, on the layer of the reference.
        assertTrue(drawing.entities.none { it is DxfEntity.Insert })
        assertEquals(8, drawing.entities.count { it.layer == "KAPI" })
        assertFalse(drawing.expansionTruncated)
    }

    @Test
    fun `the room labels are there to be drawn`() {
        val labels = drawing.entities.filterIsInstance<DxfEntity.Text>().map { it.value }
        assertTrue(labels.contains("SALON"), "labels were $labels")
        assertTrue(labels.contains("YATAK ODASI"), "labels were $labels")
    }

    @Test
    fun `it is a twelve by nine metre plan sitting at the origin`() {
        val bounds = drawing.bounds
        assertNotNull(bounds)
        assertEquals(0.0, bounds.min.x, 1.0)
        assertEquals(0.0, bounds.min.y, 1.0)
        assertEquals(12000.0, bounds.max.x, 1.0)
        // The door arcs reach a little past the top wall in the mirrored case;
        // the plan itself is 9000 tall.
        assertTrue(bounds.max.y >= 9000.0, "top was ${bounds.max.y}")
        assertTrue(bounds.max.y < 10000.0, "top was ${bounds.max.y}")
    }
}
