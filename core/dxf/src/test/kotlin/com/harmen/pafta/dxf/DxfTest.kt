package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A hand-written R12 drawing: a room outline, a door arc, room labels, plus one
 * entity type the reader does not model, to prove it degrades gracefully.
 */
private val SAMPLE = listOf(
    "0", "SECTION",
    "2", "HEADER",
    "9", "\$ACADVER", "1", "AC1009",
    "9", "\$INSUNITS", "70", "4",
    "0", "ENDSEC",
    "0", "SECTION",
    "2", "TABLES",
    "0", "TABLE",
    "2", "LAYER",
    "70", "3",
    "0", "LAYER", "2", "0", "70", "0", "62", "7", "6", "CONTINUOUS",
    "0", "LAYER", "2", "WALLS", "70", "0", "62", "1", "6", "CONTINUOUS",
    "0", "LAYER", "2", "HIDDEN-STUFF", "70", "1", "62", "3", "6", "CONTINUOUS",
    "0", "ENDTAB",
    "0", "ENDSEC",
    "0", "SECTION",
    "2", "ENTITIES",
    "0", "LINE", "8", "WALLS",
    "10", "0.0", "20", "0.0", "30", "0.0",
    "11", "5500.0", "21", "0.0", "31", "0.0",
    "0", "LWPOLYLINE", "8", "WALLS", "70", "1",
    "10", "0.0", "20", "0.0",
    "10", "5500.0", "20", "0.0",
    "10", "5500.0", "20", "4500.0",
    "10", "0.0", "20", "4500.0",
    "0", "CIRCLE", "8", "0", "10", "1000.0", "20", "1000.0", "40", "250.0",
    "0", "ARC", "8", "0", "10", "0.0", "20", "0.0", "40", "900.0", "50", "0.0", "51", "90.0",
    "0", "TEXT", "8", "ANNOT", "10", "2750.0", "20", "2250.0", "40", "150.0", "1", "LIVING", "72", "1",
    "0", "MTEXT", "8", "ANNOT", "10", "100.0", "20", "100.0", "40", "120.0", "1", "Floor", "3", " plan",
    "0", "HELIX", "8", "0", "10", "0.0", "20", "0.0",
    "0", "ENDSEC",
    "0", "EOF",
).joinToString("\n", postfix = "\n")

class DxfReaderTest {
    private val drawing = DxfReader.read(SAMPLE)

    @Test
    fun `header units are read`() {
        assertEquals(DxfInsUnits.MILLIMETRES, drawing.insUnits)
    }

    @Test
    fun `layers are read with colour and frozen state`() {
        assertEquals(listOf("0", "WALLS", "HIDDEN-STUFF"), drawing.layers.map { it.name })
        assertEquals(1, drawing.layer("WALLS").colour)
        assertTrue(drawing.layer("WALLS").visible)
        assertTrue(drawing.layer("HIDDEN-STUFF").frozen)
        assertFalse(drawing.layer("HIDDEN-STUFF").visible)
    }

    @Test
    fun `an unknown layer name yields a usable default`() {
        val l = drawing.layer("NOT-IN-FILE")
        assertEquals("NOT-IN-FILE", l.name)
        assertTrue(l.visible)
    }

    @Test
    fun `line endpoints are read`() {
        val line = drawing.entities.filterIsInstance<DxfEntity.Line>().single()
        assertEquals("WALLS", line.layer)
        assertEquals(Vec3(0.0, 0.0, 0.0), line.start)
        assertEquals(Vec3(5500.0, 0.0, 0.0), line.end)
    }

    @Test
    fun `lwpolyline vertices are paired correctly and closure is honoured`() {
        val p = drawing.entities.filterIsInstance<DxfEntity.Polyline>().single()
        assertTrue(p.closed)
        assertEquals(
            listOf(
                Vec2(0.0, 0.0),
                Vec2(5500.0, 0.0),
                Vec2(5500.0, 4500.0),
                Vec2(0.0, 4500.0),
            ),
            p.vertices,
        )
        // A closed polyline's outline repeats the first point so it draws shut.
        assertEquals(5, p.outline().size)
    }

    @Test
    fun `circle and arc are read`() {
        val c = drawing.entities.filterIsInstance<DxfEntity.Circle>().single()
        assertEquals(250.0, c.radius, 1e-9)
        val a = drawing.entities.filterIsInstance<DxfEntity.Arc>().single()
        assertEquals(900.0, a.radius, 1e-9)
        assertEquals(0.0, a.startAngleDegrees, 1e-9)
        assertEquals(90.0, a.endAngleDegrees, 1e-9)
        assertTrue(a.outline(8).first().isCloseTo(Vec2(900.0, 0.0)))
        assertTrue(a.outline(8).last().isCloseTo(Vec2(0.0, 900.0)))
    }

    @Test
    fun `text keeps its value alignment and height`() {
        val living = drawing.entities.filterIsInstance<DxfEntity.Text>().first { it.value == "LIVING" }
        assertEquals(DxfTextAlign.CENTRE, living.align)
        assertEquals(150.0, living.height, 1e-9)
        assertEquals("ANNOT", living.layer)
    }

    @Test
    fun `mtext fragments are concatenated in file order`() {
        val m = drawing.entities.filterIsInstance<DxfEntity.Text>().first { it.height == 120.0 }
        assertEquals("Floor plan", m.value)
    }

    @Test
    fun `an unmodelled entity is recorded rather than failing the import`() {
        assertEquals(setOf("HELIX"), drawing.unsupportedEntityTypes)
        // ...and the entities around it were still read.
        assertTrue(drawing.entities.size >= 6)
    }

    @Test
    fun `bounds cover every entity`() {
        val b = drawing.bounds
        assertEquals(0.0, b.min.x, 1e-6)
        assertEquals(0.0, b.min.y, 1e-6)
        assertEquals(5500.0, b.max.x, 1e-6)
        assertEquals(4500.0, b.max.y, 1e-6)
    }

    @Test
    fun `a truncated or malformed file is reported rather than silently accepted`() {
        assertFailsWith<DxfParseException> { DxfReader.read("0\nSECTION\n2\n") }
        assertFailsWith<DxfParseException> { DxfReader.read("notacode\nvalue\n") }
    }

    @Test
    fun `an empty drawing reads as empty rather than throwing`() {
        val d = DxfReader.read("0\nEOF\n")
        assertTrue(d.entities.isEmpty())
        assertTrue(d.bounds.isEmpty)
    }

    @Test
    fun `classic polyline with vertex entities is collected`() {
        val text = listOf(
            "0", "SECTION", "2", "ENTITIES",
            "0", "POLYLINE", "8", "GRID", "66", "1", "70", "1",
            "0", "VERTEX", "8", "GRID", "10", "0.0", "20", "0.0",
            "0", "VERTEX", "8", "GRID", "10", "100.0", "20", "0.0",
            "0", "VERTEX", "8", "GRID", "10", "100.0", "20", "100.0",
            "0", "SEQEND", "8", "GRID",
            "0", "LINE", "8", "GRID", "10", "0.0", "20", "0.0", "11", "1.0", "21", "1.0",
            "0", "ENDSEC", "0", "EOF",
        ).joinToString("\n", postfix = "\n")

        val d = DxfReader.read(text)
        val p = d.entities.filterIsInstance<DxfEntity.Polyline>().single()
        assertTrue(p.closed)
        assertEquals(3, p.vertices.size)
        assertEquals(Vec2(100.0, 100.0), p.vertices.last())
        // The LINE following SEQEND must still be picked up.
        assertEquals(1, d.entities.filterIsInstance<DxfEntity.Line>().size)
    }

    @Test
    fun `a text value with leading spaces is not trimmed away`() {
        val text = listOf(
            "0", "SECTION", "2", "ENTITIES",
            "0", "TEXT", "8", "0", "10", "0.0", "20", "0.0", "40", "10.0", "1", "  indented",
            "0", "ENDSEC", "0", "EOF",
        ).joinToString("\n", postfix = "\n")
        val t = DxfReader.read(text).entities.filterIsInstance<DxfEntity.Text>().single()
        assertEquals("  indented", t.value)
    }
}

class DxfWriterTest {
    @Test
    fun `a written drawing reads back identically`() {
        val original = DxfDrawing(
            layers = listOf(
                DxfLayer("0"),
                DxfLayer("WALLS", colour = 1),
                DxfLayer("DIMS", colour = 32, frozen = true),
            ),
            entities = listOf(
                DxfEntity.Line("WALLS", Vec3.ZERO, Vec3(5500.0, 0.0, 0.0)),
                DxfEntity.Polyline(
                    "WALLS",
                    listOf(Vec2(0.0, 0.0), Vec2(5500.0, 0.0), Vec2(5500.0, 4500.0)),
                    closed = true,
                ),
                DxfEntity.Circle("0", Vec3(1000.0, 1000.0, 0.0), 250.0),
                DxfEntity.Arc("0", Vec3.ZERO, 900.0, 0.0, 90.0),
                DxfEntity.Text(
                    "DIMS",
                    Vec3(10.0, 20.0, 0.0),
                    150.0,
                    "5500mm",
                    align = DxfTextAlign.CENTRE,
                ),
                DxfEntity.Point("0", Vec3(1.5, -2.25, 0.0)),
                DxfEntity.Insert("0", "DOOR", Vec3(100.0, 200.0, 0.0), rotationDegrees = 90.0),
            ),
            insUnits = DxfInsUnits.MILLIMETRES,
        )

        val reread = DxfReader.read(DxfWriter.writeToString(original))

        assertEquals(DxfInsUnits.MILLIMETRES, reread.insUnits)
        assertEquals(original.layers.map { it.name }, reread.layers.map { it.name })
        assertEquals(1, reread.layer("WALLS").colour)
        assertTrue(reread.layer("DIMS").frozen)
        assertTrue(reread.unsupportedEntityTypes.isEmpty(), "writer emitted something it cannot read")
        assertEquals(original.entities.size, reread.entities.size)

        assertEquals(
            Vec3(5500.0, 0.0, 0.0),
            reread.entities.filterIsInstance<DxfEntity.Line>().single().end,
        )

        val poly = reread.entities.filterIsInstance<DxfEntity.Polyline>().single()
        assertTrue(poly.closed)
        assertEquals(3, poly.vertices.size)
        assertEquals(Vec2(5500.0, 4500.0), poly.vertices.last())

        assertEquals(
            90.0,
            reread.entities.filterIsInstance<DxfEntity.Arc>().single().endAngleDegrees,
            1e-9,
        )
        assertEquals(
            250.0,
            reread.entities.filterIsInstance<DxfEntity.Circle>().single().radius,
            1e-9,
        )

        val label = reread.entities.filterIsInstance<DxfEntity.Text>().single()
        assertEquals("5500mm", label.value)
        assertEquals(DxfTextAlign.CENTRE, label.align)
        assertEquals(150.0, label.height, 1e-9)

        assertEquals(
            -2.25,
            reread.entities.filterIsInstance<DxfEntity.Point>().single().position.y,
            1e-9,
        )

        val ins = reread.entities.filterIsInstance<DxfEntity.Insert>().single()
        assertEquals("DOOR", ins.blockName)
        assertEquals(90.0, ins.rotationDegrees, 1e-9)
        assertEquals(1.0, ins.scale.x, 1e-9)
    }

    @Test
    fun `layer zero is synthesised when the drawing does not declare it`() {
        val reread = DxfReader.read(
            DxfWriter.writeToString(DxfDrawing(layers = listOf(DxfLayer("WALLS")))),
        )
        assertEquals(listOf("0", "WALLS"), reread.layers.map { it.name })
    }

    @Test
    fun `output declares the R12 dialect and terminates properly`() {
        val text = DxfWriter.writeToString(DxfDrawing())
        assertTrue(text.contains("AC1009"), "should declare the R12 version")
        assertTrue(text.trimEnd().endsWith("EOF"))
    }

    @Test
    fun `large and small coordinates are written without exponents`() {
        val text = DxfWriter.writeToString(
            DxfDrawing(entities = listOf(DxfEntity.Point("0", Vec3(0.0000001, 1.0E9, 0.0)))),
        )
        assertFalse(text.contains("E+") || text.contains("e+"), "DXF reals must not use exponents")
        val p = DxfReader.read(text).entities.filterIsInstance<DxfEntity.Point>().single()
        assertEquals(1.0E9, p.position.y, 1.0)
    }

    @Test
    fun `a by-layer colour is omitted so files stay small`() {
        val text = DxfWriter.writeToString(
            DxfDrawing(entities = listOf(DxfEntity.Line("0", Vec3.ZERO, Vec3.X))),
        )
        assertFalse(text.contains("\n62\n256\n"), "by-layer colour should not be written")
    }
}
