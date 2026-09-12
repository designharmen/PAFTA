package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Block expansion.
 *
 * This is the difference between a plan and a field of markers: in a drawing
 * from any real CAD tool the doors, windows, fixtures and title block are all
 * block references, and a reader that skips the `BLOCKS` section draws none of
 * them.
 */
class DxfBlockTest {

    private fun dxf(vararg groups: String): String =
        groups.joinToString("\n", postfix = "\n")

    private fun drawingOf(blocks: List<String>, entities: List<String>): DxfDrawing =
        DxfReader.read(
            dxf(
                *listOf("0", "SECTION", "2", "BLOCKS").toTypedArray(),
                *blocks.toTypedArray(),
                *listOf("0", "ENDSEC", "0", "SECTION", "2", "ENTITIES").toTypedArray(),
                *entities.toTypedArray(),
                *listOf("0", "ENDSEC", "0", "EOF").toTypedArray(),
            ),
        )

    /** A block holding one horizontal line from its base point, 1000 long. */
    private val doorBlock = listOf(
        "0", "BLOCK", "2", "KAPI", "10", "0.0", "20", "0.0", "30", "0.0",
        "0", "LINE", "8", "0",
        "10", "0.0", "20", "0.0", "30", "0.0",
        "11", "1000.0", "21", "0.0", "31", "0.0",
        "0", "ENDBLK",
    )

    private fun assertPoint(expectedX: Double, expectedY: Double, actual: Vec3) {
        assertEquals(expectedX, actual.x, 1e-6)
        assertEquals(expectedY, actual.y, 1e-6)
    }

    @Test
    fun `a reference is replaced by the block's geometry at the insertion point`() {
        val drawing = drawingOf(
            blocks = doorBlock,
            entities = listOf("0", "INSERT", "8", "KAPILAR", "2", "KAPI", "10", "5000.0", "20", "2000.0"),
        )

        val line = drawing.entities.single() as DxfEntity.Line
        assertPoint(5000.0, 2000.0, line.start)
        assertPoint(6000.0, 2000.0, line.end)
    }

    @Test
    fun `rotation and scale of the reference are applied`() {
        val drawing = drawingOf(
            blocks = doorBlock,
            entities = listOf(
                "0", "INSERT", "8", "KAPILAR", "2", "KAPI",
                "10", "5000.0", "20", "2000.0",
                "41", "2.0", "42", "2.0",
                "50", "90.0",
            ),
        )

        val line = drawing.entities.single() as DxfEntity.Line
        assertPoint(5000.0, 2000.0, line.start)
        // 1000 long, doubled, turned a quarter turn: straight up.
        assertPoint(5000.0, 4000.0, line.end)
    }

    @Test
    fun `geometry drawn on layer zero inside a block takes the layer of the reference`() {
        val drawing = drawingOf(
            blocks = listOf(
                "0", "BLOCK", "2", "PENCERE", "10", "0.0", "20", "0.0",
                "0", "LINE", "8", "0", "10", "0.0", "20", "0.0", "11", "10.0", "21", "0.0",
                "0", "LINE", "8", "CAM", "10", "0.0", "20", "0.0", "11", "10.0", "21", "5.0",
                "0", "ENDBLK",
            ),
            entities = listOf("0", "INSERT", "8", "PENCERELER", "2", "PENCERE", "10", "0.0", "20", "0.0"),
        )

        // The first line inherits; the second keeps the layer the block gave it,
        // so the layer palette still matches what the drawing shows.
        assertEquals(listOf("PENCERELER", "CAM"), drawing.entities.map { it.layer })
    }

    @Test
    fun `a mirrored reference keeps an arc sweeping the right way`() {
        val drawing = drawingOf(
            blocks = listOf(
                "0", "BLOCK", "2", "YAY", "10", "0.0", "20", "0.0",
                "0", "ARC", "8", "0",
                "10", "0.0", "20", "0.0", "40", "100.0", "50", "0.0", "51", "90.0",
                "0", "ENDBLK",
            ),
            entities = listOf(
                "0", "INSERT", "8", "0", "2", "YAY",
                "10", "0.0", "20", "0.0",
                "41", "-1.0", "42", "1.0",
            ),
        )

        val arc = drawing.entities.single() as DxfEntity.Arc
        assertEquals(100.0, arc.radius, 1e-6)
        // Mirroring about the Y axis maps the 0°..90° quarter onto 90°..180°.
        // Adding the rotation to the stored angles would have produced 0°..90°
        // again — the arc in the wrong quadrant, which is exactly how a mirrored
        // door ends up opening through a wall.
        assertEquals(90.0, arc.startAngleDegrees, 1e-6)
        assertEquals(180.0, arc.endAngleDegrees, 1e-6)
    }

    @Test
    fun `a block referenced inside another block is expanded too`() {
        val drawing = drawingOf(
            blocks = listOf(
                "0", "BLOCK", "2", "IC", "10", "0.0", "20", "0.0",
                "0", "LINE", "8", "0", "10", "0.0", "20", "0.0", "11", "10.0", "21", "0.0",
                "0", "ENDBLK",
                "0", "BLOCK", "2", "DIS", "10", "0.0", "20", "0.0",
                "0", "INSERT", "8", "0", "2", "IC", "10", "100.0", "20", "0.0",
                "0", "ENDBLK",
            ),
            entities = listOf("0", "INSERT", "8", "0", "2", "DIS", "10", "1000.0", "20", "1000.0"),
        )

        val line = drawing.entities.single() as DxfEntity.Line
        assertPoint(1100.0, 1000.0, line.start)
        assertPoint(1110.0, 1000.0, line.end)
    }

    @Test
    fun `a block that refers to itself terminates instead of hanging`() {
        val drawing = drawingOf(
            blocks = listOf(
                "0", "BLOCK", "2", "DONGU", "10", "0.0", "20", "0.0",
                "0", "INSERT", "8", "0", "2", "DONGU", "10", "10.0", "20", "0.0",
                "0", "ENDBLK",
            ),
            entities = listOf("0", "INSERT", "8", "0", "2", "DONGU", "10", "0.0", "20", "0.0"),
        )

        // One marker, and an honest flag: the drawing shows less than the file
        // describes, which the screen can then say out loud.
        assertEquals(1, drawing.entities.size)
        assertTrue(drawing.entities.single() is DxfEntity.Insert)
        assertTrue(drawing.expansionTruncated)
    }

    @Test
    fun `a reference to a block the file never defines stays as a marker`() {
        val drawing = drawingOf(
            blocks = emptyList(),
            entities = listOf("0", "INSERT", "8", "0", "2", "YOK", "10", "7.0", "20", "9.0"),
        )

        val insert = drawing.entities.single() as DxfEntity.Insert
        assertEquals("YOK", insert.blockName)
        assertPoint(7.0, 9.0, insert.position)
        // Nothing was dropped: the file simply does not say what this block is.
        assertFalse(drawing.expansionTruncated)
    }

    @Test
    fun `the same block placed twice is expanded both times`() {
        val drawing = drawingOf(
            blocks = doorBlock,
            entities = listOf(
                "0", "INSERT", "8", "0", "2", "KAPI", "10", "0.0", "20", "0.0",
                "0", "INSERT", "8", "0", "2", "KAPI", "10", "0.0", "20", "500.0",
            ),
        )

        assertEquals(2, drawing.entities.size)
        assertFalse(drawing.expansionTruncated)
    }

    @Test
    fun `the file's own record types are counted, so an empty screen can be explained`() {
        val drawing = drawingOf(
            blocks = doorBlock,
            entities = listOf(
                "0", "INSERT", "8", "0", "2", "KAPI", "10", "0.0", "20", "0.0",
                "0", "INSERT", "8", "0", "2", "KAPI", "10", "0.0", "20", "500.0",
                "0", "HATCH", "8", "0", "10", "0.0", "20", "0.0",
            ),
        )

        // Counts describe the file's ENTITIES section — two references and one
        // hatch — not the 2 lines the references expanded into.
        assertEquals(mapOf("INSERT" to 2, "HATCH" to 1), drawing.entityTypeCounts)
        assertEquals(mapOf("KAPI" to 1), drawing.blockEntityCounts)
        assertEquals(setOf("HATCH"), drawing.unsupportedEntityTypes)
    }

    @Test
    fun `a drawing whose entities are all unmodelled reports what it holds`() {
        val drawing = drawingOf(
            blocks = emptyList(),
            entities = listOf(
                "0", "HATCH", "8", "0", "10", "0.0", "20", "0.0",
                "0", "SPLINE", "8", "0", "10", "0.0", "20", "0.0",
                "0", "HATCH", "8", "0", "10", "1.0", "20", "0.0",
            ),
        )

        assertTrue(drawing.entities.isEmpty())
        assertEquals(mapOf("HATCH" to 2, "SPLINE" to 1), drawing.entityTypeCounts)
    }
}

/**
 * Byte-order marks. Several tools write one at the head of an exported DXF; it
 * is invisible in any editor, so a file rejected because of it would be a
 * mystery to whoever exported it.
 */
class DxfByteOrderMarkTest {

    private val body = listOf(
        "0", "SECTION", "2", "ENTITIES",
        "0", "LINE", "8", "0", "10", "0.0", "20", "0.0", "11", "100.0", "21", "0.0",
        "0", "ENDSEC", "0", "EOF",
    ).joinToString("\n", postfix = "\n")

    @Test
    fun `a utf-8 mark at the head of the file does not stop the read`() {
        val withMark = "ï»¿$body"
        assertEquals(1, DxfReader.read(withMark).entities.size)
    }

    @Test
    fun `a decoded mark at the head of the file does not stop the read`() {
        assertEquals(1, DxfReader.read("﻿$body").entities.size)
    }

    @Test
    fun `a file without a mark still reads`() {
        assertEquals(1, DxfReader.read(body).entities.size)
    }
}

/**
 * A damaged section must cost only itself.
 *
 * A section that never closes used to run to the end of the file, hiding every
 * section after it. The drawing then reads as empty with nothing in its
 * inventory to explain why — the hardest kind of failure to diagnose from a
 * photograph of a screen.
 */
class DxfUnclosedSectionTest {

    @Test
    fun `a section that never closes does not hide the entities after it`() {
        val text = listOf(
            "0", "SECTION",
            "2", "TABLES",
            "0", "TABLE", "2", "LAYER", "70", "1",
            "0", "LAYER", "2", "DUVAR", "70", "0", "62", "7", "6", "CONTINUOUS",
            // No ENDSEC here: this is the damage.
            "0", "SECTION",
            "2", "ENTITIES",
            "0", "LINE", "8", "DUVAR", "10", "0.0", "20", "0.0", "11", "100.0", "21", "0.0",
            "0", "ENDSEC",
            "0", "EOF",
        ).joinToString("\n", postfix = "\n")

        val drawing = DxfReader.read(text)
        assertEquals(1, drawing.entities.size)
        assertEquals(mapOf("LINE" to 1), drawing.entityTypeCounts)
        // The layer declared before the damage is still read.
        assertEquals(listOf("DUVAR"), drawing.layers.map { it.name })
    }

    @Test
    fun `a well-formed file is unaffected by the same rule`() {
        val text = listOf(
            "0", "SECTION", "2", "TABLES",
            "0", "TABLE", "2", "LAYER", "70", "1",
            "0", "LAYER", "2", "DUVAR", "70", "0", "62", "7", "6", "CONTINUOUS",
            "0", "ENDTAB", "0", "ENDSEC",
            "0", "SECTION", "2", "ENTITIES",
            "0", "LINE", "8", "DUVAR", "10", "0.0", "20", "0.0", "11", "100.0", "21", "0.0",
            "0", "ENDSEC", "0", "EOF",
        ).joinToString("\n", postfix = "\n")

        val drawing = DxfReader.read(text)
        assertEquals(1, drawing.entities.size)
        assertEquals(listOf("DUVAR"), drawing.layers.map { it.name })
    }
}
