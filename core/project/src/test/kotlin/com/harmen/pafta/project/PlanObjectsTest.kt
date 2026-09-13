package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.area
import com.harmen.pafta.geometry.containsPoint
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningTest {

    /** A 4m wall, 200mm thick, running east from the origin. */
    private fun wall(id: String = "w1") =
        DrawnShape.Wall(id, Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0)

    private fun door(alongMm: Double = 2000.0, swing: DoorSwing = DoorSwing.LEFT_IN) =
        DrawnShape.Opening(
            id = "d1",
            wallId = "w1",
            kind = OpeningKind.DOOR,
            alongMm = alongMm,
            widthMm = 900.0,
            swing = swing,
        )

    @Test
    fun `a door takes a hole out of its wall, as wide as the door`() {
        val plan = listOf(wall(), door()).openingPlans().single()

        assertEquals(4, plan.cut.size)
        // 900mm along the wall, and right through its 200mm thickness — with a
        // whisker over at each face so no hairline of wall is left behind.
        assertEquals(900.0 * (200.0 + 2.0), area(plan.cut), 1.0)
        assertTrue(containsPoint(plan.cut, Vec2(2000.0, 0.0)), "the middle of the doorway")
        assertTrue(!containsPoint(plan.cut, Vec2(1000.0, 0.0)), "a metre away is still wall")
    }

    @Test
    fun `a door is drawn open at a right angle, hinged where it is told`() {
        val plan = listOf(wall(), door(swing = DoorSwing.LEFT_IN)).openingPlans().single()

        val leaf = plan.leaf.single()
        // Hinged at the near jamb, 450mm back from the middle.
        assertEquals(1550.0, leaf.a.x, 1e-6)
        assertEquals(0.0, leaf.a.y, 1e-6)
        // Standing square to the wall, a full door's width out.
        assertEquals(1550.0, leaf.b.x, 1e-6)
        assertEquals(900.0, abs(leaf.b.y), 1e-6)

        val swing = assertNotNull(plan.swing)
        assertEquals(900.0, swing.radiusMm, 1e-6)
        assertEquals(leaf.a, swing.centre)
    }

    @Test
    fun `the swing arc is the quarter the door sweeps, not the rest of the circle`() {
        for (swing in DoorSwing.entries) {
            val arc = assertNotNull(listOf(wall(), door(swing = swing)).openingPlans().single().swing)
            // DXF arcs run anticlockwise from start to end, so the wrong way
            // round draws three quarters of a circle through the room.
            val sweep = (arc.endDegrees - arc.startDegrees + 360.0) % 360.0
            assertEquals(90.0, sweep, 1e-6, "$swing swept $sweep degrees")
        }
    }

    @Test
    fun `hinging on the other jamb puts the door on the other side`() {
        val left = listOf(wall(), door(swing = DoorSwing.LEFT_IN)).openingPlans().single()
        val right = listOf(wall(), door(swing = DoorSwing.RIGHT_IN)).openingPlans().single()

        assertEquals(1550.0, left.leaf.single().a.x, 1e-6)
        assertEquals(2450.0, right.leaf.single().a.x, 1e-6)
    }

    @Test
    fun `opening outwards puts the door on the other face`() {
        val inward = listOf(wall(), door(swing = DoorSwing.LEFT_IN)).openingPlans().single()
        val outward = listOf(wall(), door(swing = DoorSwing.LEFT_OUT)).openingPlans().single()

        assertTrue(
            inward.leaf.single().b.y * outward.leaf.single().b.y < 0.0,
            "both doors swung to the same side of the wall",
        )
    }

    @Test
    fun `a window is drawn as its frame and its glass, and has no swing`() {
        val window = DrawnShape.Opening(
            id = "p1",
            wallId = "w1",
            kind = OpeningKind.WINDOW,
            alongMm = 2000.0,
            widthMm = 1200.0,
            layer = DrawnShape.LAYER_WINDOW,
        )
        val plan = listOf(wall(), window).openingPlans().single()

        assertNull(plan.swing)
        // Both faces of the frame and the glass between them.
        assertEquals(3, plan.leaf.size)
        assertTrue(plan.leaf.all { abs(it.length - 1200.0) < 1e-6 })
    }

    @Test
    fun `an opening is kept inside its wall rather than hanging off the end`() {
        // Asked to sit 50mm from the start, where a 900mm door cannot fit.
        val plan = listOf(wall(), door(alongMm = 50.0)).openingPlans().single()

        val xs = plan.cut.map { it.x }
        assertTrue(xs.min() >= -1e-6, "the doorway ran off the start of the wall")
        assertTrue(xs.max() <= 4000.0 + 1e-6, "the doorway ran off the end of the wall")
    }

    @Test
    fun `a door wider than its wall is not drawn at all`() {
        val tooWide = door().copy(widthMm = 5000.0)
        assertTrue(listOf(wall(), tooWide).openingPlans().isEmpty())
    }

    @Test
    fun `an opening whose wall is gone is not drawn`() {
        assertTrue(listOf(door()).openingPlans().isEmpty())
    }

    @Test
    fun `an opening follows its wall when the wall is made longer`() {
        val stretched = assertIs<DrawnShape.Wall>(
            wall().withDimension(ShapeDimension.LENGTH, 6000.0),
        )
        val plan = listOf(stretched, door()).openingPlans().single()

        // Still 2000mm from the wall's start, because that is what it stores —
        // not a point on the sheet that the wall has since moved away from.
        assertEquals(2000.0, plan.cut.map { it.x }.average(), 1e-6)
    }

    @Test
    fun `a door offers a width and a height, and a window a sill as well`() {
        assertEquals(
            listOf(ShapeDimension.WIDTH, ShapeDimension.HEIGHT),
            door().dimensions().keys.toList(),
        )

        val window = door().copy(kind = OpeningKind.WINDOW)
        assertEquals(
            listOf(ShapeDimension.WIDTH, ShapeDimension.HEIGHT, ShapeDimension.SILL),
            window.dimensions().keys.toList(),
        )
        assertEquals(1500.0, window.withDimension(ShapeDimension.SILL, 1500.0).let {
            assertIs<DrawnShape.Opening>(it).sillMm
        }, 1e-6)
    }
}

class ZoneTest {

    /** Four 200mm walls round a 4m x 3m room, measured on their centre lines. */
    private fun room(): List<DrawnShape> = listOf(
        DrawnShape.Wall("w1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w2", Vec3(4000.0, 0.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w3", Vec3(4000.0, 3000.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w4", Vec3(0.0, 3000.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
    )

    private fun zone(name: String = "Salon") =
        DrawnShape.Zone("z1", name, Vec3(2000.0, 1500.0, 0.0))

    @Test
    fun `a room is measured to the wall faces, not their centre lines`() {
        val plan = (room() + zone()).zonePlans().single()

        assertTrue(!plan.isOpen)
        // 3800 x 2800, because 100mm of each 200mm wall is inside the room's
        // centre-line outline. 10.64 square metres.
        assertEquals(3800.0 * 2800.0, plan.areaMm2, 1.0)
        assertEquals("Salon", plan.name)
    }

    @Test
    fun `thicker walls leave less floor`() {
        val thick = room().map {
            assertIs<DrawnShape.Wall>(it).copy(thicknessMm = 400.0)
        }
        val plan = (thick + zone()).zonePlans().single()

        assertEquals(3600.0 * 2600.0, plan.areaMm2, 1.0)
    }

    @Test
    fun `moving a wall changes the room, because the room is not stored`() {
        val widened = room().map { shape ->
            val wall = assertIs<DrawnShape.Wall>(shape)
            // Push the east wall out by a metre.
            fun moved(p: Vec3) = if (p.x > 3999.0) Vec3(5000.0, p.y, p.z) else p
            wall.copy(a = moved(wall.a), b = moved(wall.b))
        }

        val plan = (widened + zone()).zonePlans().single()
        assertEquals(4800.0 * 2800.0, plan.areaMm2, 1.0)
    }

    @Test
    fun `a room whose walls no longer close says so instead of showing a stale one`() {
        val broken = room().dropLast(1)
        val plan = (broken + zone()).zonePlans().single()

        assertTrue(plan.isOpen)
        assertEquals(0.0, plan.areaMm2, 1e-6)
        // The name still belongs to the room, so nothing the user typed is lost.
        assertEquals("Salon", plan.name)
    }

    @Test
    fun `a door in a wall does not open the room`() {
        val withDoor = room() + DrawnShape.Opening(
            id = "d1",
            wallId = "w1",
            kind = OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
        )
        val plan = (withDoor + zone()).zonePlans().single()

        // A doorway is a hole in a wall, not a gap in the room: the floor is
        // exactly what it was.
        assertEquals(3800.0 * 2800.0, plan.areaMm2, 1.0)
    }

    @Test
    fun `a name and an outline reach the exported drawing`() {
        val entities = (room() + zone()).toEntities()

        val outline = entities.filterIsInstance<DxfEntity.Polyline>()
            .single { it.layer == DrawnShape.LAYER_ZONE }
        assertTrue(outline.closed)
        assertEquals(4, outline.vertices.size)

        val label = entities.filterIsInstance<DxfEntity.Text>()
            .single { it.layer == DrawnShape.LAYER_ZONE }
        assertEquals("Salon", label.value)
    }

    @Test
    fun `an unnamed room is drawn without a label rather than with an empty one`() {
        val entities = (room() + zone(name = "")).toEntities()
        assertTrue(entities.none { it is DxfEntity.Text && it.layer == DrawnShape.LAYER_ZONE })
    }

    @Test
    fun `a room has nothing for the user to type, because it is measured`() {
        assertTrue(zone().dimensions().isEmpty())
    }

    @Test
    fun `a tap inside a room picks the room, but a tap on a wall picks the wall`() {
        val shapes = room() + zone()

        assertIs<DrawnShape.Zone>(shapes.pickResolved(Vec2(2000.0, 1500.0), toleranceMm = 50.0))
        assertIs<DrawnShape.Wall>(shapes.pickResolved(Vec2(2000.0, 0.0), toleranceMm = 50.0))
    }

    @Test
    fun `a tap in a doorway picks the door, not the wall it is cut into`() {
        val shapes = room() + DrawnShape.Opening(
            id = "d1",
            wallId = "w1",
            kind = OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
        )

        assertIs<DrawnShape.Opening>(shapes.pickResolved(Vec2(2000.0, 0.0), toleranceMm = 50.0))
        assertIs<DrawnShape.Wall>(shapes.pickResolved(Vec2(500.0, 0.0), toleranceMm = 50.0))
    }

    @Test
    fun `openings and rooms survive being saved and read back`() {
        val shapes = room() + listOf(
            DrawnShape.Opening(
                id = "d1",
                wallId = "w1",
                kind = OpeningKind.DOOR,
                alongMm = 2000.0,
                widthMm = 900.0,
                swing = DoorSwing.RIGHT_OUT,
            ),
            zone(),
        )

        // The same settings the container uses; what matters here is that the
        // two new shapes round-trip, not how the file is laid out.
        val format = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
        val shapesOf = ListSerializer(DrawnShape.serializer())
        val back = format.decodeFromString(shapesOf, format.encodeToString(shapesOf, shapes))

        assertEquals(shapes, back)
        assertEquals(3800.0 * 2800.0, back.zonePlans().single().areaMm2, 1.0)
    }
}
