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
        assertEquals(DrawnShape.LAYER_WALL, polyline.layer)
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

        // And the drawing shows the file's line plus both drawn shapes.
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(reopened.openAsDrawing()).value
        assertEquals(3, doc.entityCount)

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

        assertEquals(1, doc.entityCount)
        // The wall's layer joins the palette, so it can be hidden like any other.
        assertTrue(doc.layers.any { it.name == DrawnShape.LAYER_WALL })

        root.deleteRecursively()
    }
}
