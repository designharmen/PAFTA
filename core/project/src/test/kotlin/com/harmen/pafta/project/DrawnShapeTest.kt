package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the user draws: geometry, picking, and moving. */
class DrawnShapeTest {

    private fun v(x: Double, y: Double) = Vec3(x, y, 0.0)

    @Test
    fun `a wall becomes a closed outline as thick as it claims to be`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(5000.0, 0.0), thicknessMm = 200.0)
        val corners = wall.outline()

        assertEquals(4, corners.size)
        // A horizontal wall's faces sit 100mm above and below the centre line.
        assertEquals(setOf(100.0, -100.0), corners.map { it.y }.toSet())
        assertEquals(setOf(0.0, 5000.0), corners.map { it.x }.toSet())

        val polyline = assertIs<DxfEntity.Polyline>(wall.toEntities().single())
        assertTrue(polyline.closed)
        // Walls land on a layer named for what they are, so the palette can
        // group them: 200mm brick walls together, 300mm concrete separately.
        assertEquals("DUVAR-TUGLA-200", polyline.layer)
    }

    @Test
    fun `a diagonal wall is the same thickness as a straight one`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(3000.0, 4000.0), thicknessMm = 240.0)
        val c = wall.outline()

        // The two faces are parallel to the centre line, 240mm apart: measure
        // across the wall rather than trusting the maths that produced it.
        val across = hypot(c[0].x - c[3].x, c[0].y - c[3].y)
        assertEquals(240.0, across, 1e-6)
        val acrossFarEnd = hypot(c[1].x - c[2].x, c[1].y - c[2].y)
        assertEquals(240.0, acrossFarEnd, 1e-6)
    }

    @Test
    fun `a wall with no length draws nothing rather than a spike`() {
        val wall = DrawnShape.Wall("w1", v(1000.0, 1000.0), v(1000.0, 1000.0))
        assertTrue(wall.outline().isEmpty())
        assertTrue(wall.toEntities().isEmpty())
    }

    @Test
    fun `a rectangle is a closed four-corner outline, and a flat one is nothing`() {
        val rect = DrawnShape.Rectangle("r1", v(0.0, 0.0), v(4000.0, 3000.0))
        val polyline = assertIs<DxfEntity.Polyline>(rect.toEntities().single())

        assertEquals(4, polyline.vertices.size)
        assertTrue(polyline.closed)
        assertTrue(DrawnShape.Rectangle("r2", v(0.0, 0.0), v(0.0, 3000.0)).toEntities().isEmpty())
    }

    @Test
    fun `a circle keeps its radius, and a zero radius draws nothing`() {
        val circle = DrawnShape.Circle("c1", v(1000.0, 1000.0), radiusMm = 700.0)
        val entity = assertIs<DxfEntity.Circle>(circle.toEntities().single())

        assertEquals(700.0, entity.radius, 1e-9)
        assertTrue(DrawnShape.Circle("c2", v(0.0, 0.0), radiusMm = 0.0).toEntities().isEmpty())
    }

    @Test
    fun `a wall is picked anywhere on its body, not just on its edge`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(5000.0, 0.0), thicknessMm = 200.0)
        val shapes = listOf<DrawnShape>(wall)

        // Dead centre of the wall: a 1px-accurate tap should not be required.
        assertEquals(wall, shapes.pick(Vec2(2500.0, 0.0), toleranceMm = 10.0))
        // Just outside the face, within the finger tolerance.
        assertEquals(wall, shapes.pick(Vec2(2500.0, 105.0), toleranceMm = 10.0))
        // Well clear of it.
        assertNull(shapes.pick(Vec2(2500.0, 400.0), toleranceMm = 10.0))
    }

    @Test
    fun `the shape on top wins when two overlap`() {
        val under = DrawnShape.Line("under", v(0.0, 0.0), v(1000.0, 0.0))
        val over = DrawnShape.Line("over", v(0.0, 0.0), v(1000.0, 0.0))

        // Drawn later means drawn on top, which is the one the user means.
        assertEquals(over, listOf(under, over).pick(Vec2(500.0, 0.0), toleranceMm = 50.0))
    }

    @Test
    fun `a circle is picked on its ring, not in its middle`() {
        val circle = DrawnShape.Circle("c1", v(0.0, 0.0), radiusMm = 1000.0)
        val shapes = listOf<DrawnShape>(circle)

        assertEquals(circle, shapes.pick(Vec2(1000.0, 0.0), toleranceMm = 20.0))
        assertNull(shapes.pick(Vec2(0.0, 0.0), toleranceMm = 20.0), "the middle is empty space")
    }

    @Test
    fun `walls are separated onto a layer by thickness and material`() {
        assertEquals("DUVAR-TUGLA-200", DrawnShape.wallLayer(200.0, WallMaterial.BRICK))
        assertEquals("DUVAR-BETON-300", DrawnShape.wallLayer(300.0, WallMaterial.CONCRETE))
        // Nothing in a layer name may need an encoding a DXF file cannot carry.
        assertTrue(
            DrawnShape.wallLayer(100.0, WallMaterial.AERATED).all { it.code < 128 },
            "layer names must stay ASCII",
        )
    }

    @Test
    fun `a wall offers its centre line to snap to, not its outline`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(5000.0, 0.0), thicknessMm = 200.0)
        val segment = wall.snapSegments().single()

        // The end of the centre line is where the next wall must start. The
        // outline's corners sit 100mm off to each side; snapping to one of those
        // leaves every junction out by half a wall.
        assertEquals(0.0, segment.a.y, 1e-9)
        assertEquals(0.0, segment.b.y, 1e-9)
        assertEquals(5000.0, segment.b.x, 1e-9)
    }

    @Test
    fun `a rectangle offers all four of its edges`() {
        val rect = DrawnShape.Rectangle("r1", v(0.0, 0.0), v(1000.0, 500.0))
        assertEquals(4, rect.snapSegments().size)
    }

    @Test
    fun `moving a shape moves all of it`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(5000.0, 0.0))
        val moved = assertIs<DrawnShape.Wall>(wall.translated(100.0, -250.0))

        assertEquals(100.0, moved.a.x, 1e-9)
        assertEquals(-250.0, moved.a.y, 1e-9)
        assertEquals(5100.0, moved.b.x, 1e-9)
        assertEquals(-250.0, moved.b.y, 1e-9)
        // Identity is kept: moving a shape is not replacing it.
        assertEquals(wall.id, moved.id)
    }
    @Test
    fun `two walls that share a corner reach past it, so the corner closes`() {
        val corner = Vec3(3000.0, 0.0, 0.0)
        val walls = listOf(
            DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), corner, thicknessMm = 200.0),
            DrawnShape.Wall("w2", corner, Vec3(3000.0, 2000.0, 0.0), thicknessMm = 200.0),
        )

        val bands = walls.wallBands().associateBy { it.id }
        assertEquals(2, bands.size)

        // The first wall reaches 100mm past the corner — half the other wall's
        // thickness, which is exactly its far face — and not at all at its free end.
        val first = bands.getValue("w1").corners
        assertEquals(0.0, first.minOf { it.x }, 1e-6)
        assertEquals(3100.0, first.maxOf { it.x }, 1e-6)

        // The second reaches back the same 100mm, and stops square at its own free end.
        val second = bands.getValue("w2").corners
        assertEquals(-100.0, second.minOf { it.y }, 1e-6)
        assertEquals(2000.0, second.maxOf { it.y }, 1e-6)
    }

    @Test
    fun `a wall standing alone is not stretched`() {
        val band = listOf(
            DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(3000.0, 0.0, 0.0)),
        ).wallBands().single()

        assertEquals(0.0, band.corners.minOf { it.x }, 1e-6)
        assertEquals(3000.0, band.corners.maxOf { it.x }, 1e-6)
    }

    @Test
    fun `a wall offers length, thickness and height, and each can be set`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(3000.0, 0.0), thicknessMm = 200.0)

        assertEquals(
            listOf(ShapeDimension.LENGTH, ShapeDimension.THICKNESS, ShapeDimension.HEIGHT),
            wall.dimensions().keys.toList(),
        )
        assertEquals(3000.0, wall.dimensions().getValue(ShapeDimension.LENGTH), 1e-6)
        assertEquals(200.0, wall.dimensions().getValue(ShapeDimension.THICKNESS), 1e-6)
        assertEquals(
            DrawnShape.DEFAULT_WALL_HEIGHT_MM,
            wall.dimensions().getValue(ShapeDimension.HEIGHT),
            1e-6,
        )

        val longer = wall.withDimension(ShapeDimension.LENGTH, 3600.0)
        assertEquals(3600.0, longer.dimensions().getValue(ShapeDimension.LENGTH), 1e-6)

        val taller = wall.withDimension(ShapeDimension.HEIGHT, 2400.0)
        assertEquals(2400.0, taller.dimensions().getValue(ShapeDimension.HEIGHT), 1e-6)
    }

    @Test
    fun `changing a wall's thickness moves it to the layer for that thickness`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(3000.0, 0.0), thicknessMm = 200.0)
        assertEquals(DrawnShape.wallLayer(200.0, WallMaterial.BRICK), wall.layer)

        val thinner = wall.withDimension(ShapeDimension.THICKNESS, 100.0)
        // Otherwise a 100mm wall would sit on the 200mm layer for good, and
        // hiding the 200mm walls would take it with them.
        assertEquals(DrawnShape.wallLayer(100.0, WallMaterial.BRICK), thinner.layer)
    }

    @Test
    fun `a rectangle is widened from the corner it was drawn from, either way round`() {
        val forwards = DrawnShape.Rectangle("r1", v(0.0, 0.0), v(1000.0, 500.0))
        val wider = forwards.withDimension(ShapeDimension.WIDTH, 2000.0)
        assertEquals(2000.0, wider.dimensions().getValue(ShapeDimension.WIDTH), 1e-6)
        assertEquals(500.0, wider.dimensions().getValue(ShapeDimension.DEPTH), 1e-6)

        // Drawn right to left, it must grow to the left rather than flip over.
        val backwards = DrawnShape.Rectangle("r2", v(0.0, 0.0), v(-1000.0, 500.0))
        val alsoWider = assertIs<DrawnShape.Rectangle>(
            backwards.withDimension(ShapeDimension.WIDTH, 2000.0),
        )
        assertEquals(-2000.0, alsoWider.opposite.x, 1e-6)
    }

    @Test
    fun `a circle is set by its diameter, not its radius`() {
        val circle = DrawnShape.Circle("c1", v(0.0, 0.0), radiusMm = 250.0)
        assertEquals(500.0, circle.dimensions().getValue(ShapeDimension.DIAMETER), 1e-6)

        val bigger = assertIs<DrawnShape.Circle>(
            circle.withDimension(ShapeDimension.DIAMETER, 800.0),
        )
        assertEquals(400.0, bigger.radiusMm, 1e-6)
    }

    @Test
    fun `a copy is the same shape under a different id`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(3000.0, 0.0), thicknessMm = 150.0)
        val copy = assertIs<DrawnShape.Wall>(wall.withId("w2"))

        assertEquals("w2", copy.id)
        // Everything else is the same, which is what makes it a copy — and the
        // id is what keeps selecting one from deleting the other.
        assertEquals(wall, copy.copy(id = wall.id))
    }

    @Test
    fun `a measurement that a shape does not have is left alone`() {
        val circle = DrawnShape.Circle("c1", v(0.0, 0.0), radiusMm = 250.0)
        assertEquals(circle, circle.withDimension(ShapeDimension.THICKNESS, 100.0))

        val line = DrawnShape.Line("l1", v(0.0, 0.0), v(1000.0, 0.0))
        assertEquals(line, line.withDimension(ShapeDimension.HEIGHT, 2400.0))
    }

}

/**
 * Drawn shapes through the whole container: saved, reopened, and merged into
 * the drawing on screen.
 *
 * The point of the separation is tested here too — the imported file's bytes
 * must come back untouched no matter what was drawn on top of it.
 */
class DrawnShapePersistenceTest {

    private val dxf = listOf(
        "0", "SECTION", "2", "ENTITIES",
        "0", "LINE", "8", "DUVAR", "10", "0.0", "20", "0.0", "11", "1000.0", "21", "0.0",
        "0", "ENDSEC", "0", "EOF",
    ).joinToString("\n", postfix = "\n").toByteArray()

    private fun store(): Pair<ProjectStore, java.io.File> {
        val root = java.nio.file.Files.createTempDirectory("pafta-shapes").toFile()
        return ProjectStore(root) to root
    }

    @Test
    fun `shapes survive a save and reopen, and reach the drawing`() {
        val (store, root) = store()
        val entry = store.import("plan.dxf", dxf).valueOrNull()
        assertTrue(entry != null)

        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val withWall = opened.copy(
            shapes = listOf(
                DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(3000.0, 0.0, 0.0)),
                DrawnShape.Circle("c1", Vec3(500.0, 500.0, 0.0), 250.0),
            ),
        )
        assertIs<StoreResult.Success<PaftaProject>>(store.save(withWall, entry.file))

        val reopened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertEquals(2, reopened.shapes.size)
        assertIs<DrawnShape.Wall>(reopened.shapes[0])
        assertIs<DrawnShape.Circle>(reopened.shapes[1])

        // The imported file is untouched: drawing on top must never rewrite it.
        assertTrue(reopened.payload.contentEquals(dxf))

        // The document carries the FILE's geometry only — one line. What the user
        // drew is kept live in the editor instead, because a copy frozen at open
        // time would stay on screen unchanged while the real one was edited.
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(reopened.openAsDrawing()).value
        assertEquals(1, doc.entityCount)
        // The view is still framed around both, and both layers are in the palette.
        assertTrue(doc.bounds.contains(Vec3(3000.0, 0.0, 0.0)), "bounds were ${doc.bounds}")
        assertTrue(
            doc.layers.any { it.name.startsWith(DrawnShape.LAYER_WALL) },
            "palette was ${doc.layers.map { it.name }}",
        )

        root.deleteRecursively()
    }

    @Test
    fun `a drawing that holds nothing but hand-drawn shapes still opens`() {
        val (store, root) = store()
        val empty = "0\nSECTION\n2\nENTITIES\n0\nENDSEC\n0\nEOF\n".toByteArray()
        val entry = store.import("bos.dxf", empty).valueOrNull()
        assertTrue(entry != null)

        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        // Before anything is drawn there is genuinely nothing to show.
        assertIs<StoreResult.Failure>(opened.openAsDrawing())

        val drawn = opened.copy(
            shapes = listOf(DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))),
        )
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(drawn.openAsDrawing()).value

        // The file itself is still empty; the wall is not copied into it.
        assertEquals(0, doc.entityCount)
        // The wall's layer joins the palette, so it can be hidden like any other.
        assertTrue(
            doc.layers.any { it.name.startsWith(DrawnShape.LAYER_WALL) },
            "palette was ${doc.layers.map { it.name }}",
        )

        root.deleteRecursively()
    }
}

/**
 * Setting an exact length after drawing.
 *
 * A finger cannot land on 3600mm. The wall is drawn roughly and then told what
 * it is, which is how every CAD tool works and the only way touch drawing can
 * produce a measured plan.
 */
class DrawnShapeLengthTest {

    private fun v(x: Double, y: Double) = Vec3(x, y, 0.0)

    @Test
    fun `a wall keeps its start and direction when its length is set`() {
        val wall = DrawnShape.Wall("w1", v(1000.0, 500.0), v(1000.0, 2000.0))
        assertEquals(1500.0, wall.lengthMm!!, 1e-9)

        val exact = assertIs<DrawnShape.Wall>(wall.withLength(3600.0))

        // The start stays put: it is usually snapped to a corner that matters.
        assertEquals(1000.0, exact.a.x, 1e-9)
        assertEquals(500.0, exact.a.y, 1e-9)
        // The end moves along the same direction, to exactly the length asked for.
        assertEquals(1000.0, exact.b.x, 1e-9)
        assertEquals(4100.0, exact.b.y, 1e-9)
        assertEquals(3600.0, exact.lengthMm!!, 1e-9)
    }

    @Test
    fun `a diagonal wall reaches the exact length it is given`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(300.0, 400.0))
        assertEquals(500.0, wall.lengthMm!!, 1e-9)
        assertEquals(2500.0, wall.withLength(2500.0).lengthMm!!, 1e-9)
    }

    @Test
    fun `a circle's length is its diameter, and setting it resizes the circle`() {
        val circle = DrawnShape.Circle("c1", v(0.0, 0.0), radiusMm = 500.0)
        assertEquals(1000.0, circle.lengthMm!!, 1e-9)

        val exact = assertIs<DrawnShape.Circle>(circle.withLength(1600.0))
        assertEquals(800.0, exact.radiusMm, 1e-9)
    }

    @Test
    fun `a shape with no length is left alone rather than guessed at`() {
        val dot = DrawnShape.Wall("w1", v(10.0, 10.0), v(10.0, 10.0))
        assertEquals(dot, dot.withLength(1000.0))
        // A rectangle is described by two corners, not by one length.
        val rect = DrawnShape.Rectangle("r1", v(0.0, 0.0), v(100.0, 50.0))
        assertEquals(null, rect.lengthMm)
        assertEquals(rect, rect.withLength(1000.0))
    }

    @Test
    fun `a length of zero or less changes nothing`() {
        val wall = DrawnShape.Wall("w1", v(0.0, 0.0), v(1000.0, 0.0))
        assertEquals(wall, wall.withLength(0.0))
        assertEquals(wall, wall.withLength(-5.0))
    }
}
