package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a tidied corner has to look like, and what it must not disturb.
 *
 * The device showed both of these going wrong at once: the corner opened into a
 * gap with a pencil line across it, and every door in the walls being tidied
 * slid along by however much the wall had been cut back.
 */
class RoundedCornerTest {

    /** Two 200mm walls meeting at a right angle at the origin. */
    private fun corner(): List<DrawnShape> = listOf(
        DrawnShape.Wall("a", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes

    // --- the corner is filled in ---------------------------------------------

    @Test
    fun `the arc a fillet leaves is a piece of wall, as thick as the walls it joins`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        val arc = assertIs<DrawnShape.Arc>(after.last())

        assertEquals(200.0, arc.thicknessMm, 1e-6)
        assertTrue(arc.isBand(), "a rounded corner has to be filled like a wall")
    }

    @Test
    fun `the thinner of two walls decides, so the corner never overhangs a face`() {
        val mixed = listOf(
            DrawnShape.Wall("a", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 300.0),
            DrawnShape.Wall("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 100.0),
        )
        val after = done(mixed.filleted("a", "b", radiusMm = 500.0))
        assertEquals(100.0, assertIs<DrawnShape.Arc>(after.last()).thicknessMm, 1e-6)
    }

    @Test
    fun `two pencil lines are rounded off with a pencil line, not a wall`() {
        val lines = listOf(
            DrawnShape.Line("a", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0)),
            DrawnShape.Line("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0)),
        )
        val after = done(lines.filleted("a", "b", radiusMm = 500.0))
        val arc = assertIs<DrawnShape.Arc>(after.last())
        assertEquals(0.0, arc.thicknessMm, 1e-6)
        assertTrue(!arc.isBand())
    }

    @Test
    fun `the flat a chamfer leaves between two walls is itself a wall`() {
        val after = done(corner().chamfered("a", "b", distanceMm = 400.0))
        val flat = assertIs<DrawnShape.Wall>(after.last())

        assertEquals(200.0, flat.thicknessMm, 1e-6)
        assertEquals(WallMaterial.BRICK, flat.material)
        assertEquals(corner().first().layer, flat.layer)
    }

    @Test
    fun `between two lines a chamfer is still only a line`() {
        val lines = listOf(
            DrawnShape.Line("a", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0)),
            DrawnShape.Line("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0)),
        )
        assertIs<DrawnShape.Line>(done(lines.chamfered("a", "b", distanceMm = 400.0)).last())
    }

    @Test
    fun `a rounded corner is drawn as a band, and the band spans the wall's thickness`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        val bands = after.wallBands()

        // Two walls and the curve between them.
        assertEquals(3, bands.size)
        val curve = bands.first { it.id.endsWith("-yay") }

        // Every corner of the band is between the inner and outer faces of a
        // 200mm wall swept round a 500mm radius: 400mm and 600mm from the
        // centre of the arc, which sits at (500, 500).
        val centre = Vec2(500.0, 500.0)
        for (point in curve.corners) {
            val distance = point.distanceTo(centre)
            assertTrue(
                distance in 399.9..600.1,
                "corner at $distance is off the band",
            )
        }
    }

    @Test
    fun `the band has both faces, not just one`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        val curve = after.wallBands().first { it.id.endsWith("-yay") }
        val centre = Vec2(500.0, 500.0)

        assertTrue(curve.corners.any { it.distanceTo(centre) > 599.0 }, "no outer face")
        assertTrue(curve.corners.any { it.distanceTo(centre) < 401.0 }, "no inner face")
    }

    @Test
    fun `a wall that meets a rounded corner overlaps it, so no seam is left`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        val wall = after.wallBands().first { it.id == "a" }

        // Wall `a` runs east to west and now stops at x = 500, where the arc
        // begins. Its band reaches a millimetre past that so the two merge.
        assertTrue(
            wall.corners.any { it.x < 500.0 },
            "the band stops exactly where the curve starts, which can leave a hairline",
        )
    }

    @Test
    fun `rounding a corner of a room leaves the room closed`() {
        val room = listOf(
            DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Zone("z", "Salon", Vec3(2000.0, 1500.0, 0.0)),
        )
        assertTrue(!room.zonePlans().single().isOpen, "the room was open before it was tidied")

        val tidied = done(room.filleted("s", "w", radiusMm = 500.0))
        val plan = tidied.zonePlans().single()

        assertTrue(!plan.isOpen, "tidying a corner must not open the room")
        // A rounded corner takes a little off the floor, so the area falls
        // slightly rather than staying exactly 3800 x 2800.
        assertTrue(plan.areaMm2 < 3800.0 * 2800.0)
        assertTrue(plan.areaMm2 > 3800.0 * 2800.0 * 0.97)
    }

    // --- the doors stay where they were --------------------------------------

    /** A wall running west with a door 1000mm from its start. */
    private fun wallWithDoor(): List<DrawnShape> = corner() + DrawnShape.Opening(
        id = "d1",
        wallId = "a",
        kind = OpeningKind.DOOR,
        alongMm = 1000.0,
        widthMm = 900.0,
    )

    /** Where the opening actually is on the sheet. */
    private fun doorAt(shapes: List<DrawnShape>): Vec2 {
        val door = shapes.filterIsInstance<DrawnShape.Opening>().single()
        val wall = shapes.filterIsInstance<DrawnShape.Wall>().first { it.id == door.wallId }
        val along = wall.b.toVec2() - wall.a.toVec2()
        return wall.a.toVec2() + along / along.length * door.alongMm
    }

    @Test
    fun `rounding a corner does not slide the doors in the walls being rounded`() {
        val before = wallWithDoor()
        val after = done(before.filleted("a", "b", radiusMm = 500.0))

        // Wall `a` runs from (4000,0) to (0,0), so the fillet moves its *end*,
        // not its start, and the door should not care at all.
        assertEquals(1000.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }

    @Test
    fun `cutting the start of a wall back re-measures its doors from the new start`() {
        // Wall `b` runs from the corner outwards, so a fillet moves its start —
        // and this is the case that slid every door along on the device.
        val before = corner() + DrawnShape.Opening(
            id = "d1",
            wallId = "b",
            kind = OpeningKind.DOOR,
            alongMm = 1500.0,
            widthMm = 900.0,
        )
        val after = done(before.filleted("a", "b", radiusMm = 500.0))

        // The start moved 500mm along the wall, so the door is now 500mm nearer
        // to it — and has not moved a millimetre on the sheet.
        assertEquals(1000.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }

    @Test
    fun `a chamfer leaves the doors where they are too`() {
        val before = corner() + DrawnShape.Opening(
            id = "d1",
            wallId = "b",
            kind = OpeningKind.DOOR,
            alongMm = 1500.0,
            widthMm = 900.0,
        )
        val after = done(before.chamfered("a", "b", distanceMm = 400.0))
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }

    @Test
    fun `trimming a wall back leaves the doors on the part that stays`() {
        val before = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("f", Vec3(1000.0, -1000.0, 0.0), Vec3(1000.0, 1000.0, 0.0)),
            DrawnShape.Opening("d1", "a", OpeningKind.DOOR, alongMm = 2500.0, widthMm = 900.0),
        )
        // Point at the western piece, so the wall is left running 1000..4000.
        val after = done(before.trimmed("a", "f", at = Vec2(200.0, 0.0)))

        assertEquals(1500.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }

    @Test
    fun `a door that no longer fits is kept inside the wall rather than sent off the end`() {
        val before = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("f", Vec3(3600.0, -1000.0, 0.0), Vec3(3600.0, 1000.0, 0.0)),
            DrawnShape.Opening("d1", "a", OpeningKind.DOOR, alongMm = 3400.0, widthMm = 900.0),
        )
        // Trim the eastern piece away: the wall now runs 0..3600 and the door,
        // 900 wide, cannot sit centred at 3400 any more.
        val after = done(before.trimmed("a", "f", at = Vec2(3900.0, 0.0)))
        val door = after.filterIsInstance<DrawnShape.Opening>().single()

        assertTrue(door.alongMm <= 3600.0 - 450.0 + 1e-6, "the door hangs off the end")
        assertTrue(door.alongMm >= 450.0 - 1e-6)
    }

    @Test
    fun `stretching a wall to reach another leaves its doors alone`() {
        val before = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("f", Vec3(3000.0, -1000.0, 0.0), Vec3(3000.0, 1000.0, 0.0)),
            DrawnShape.Opening("d1", "a", OpeningKind.DOOR, alongMm = 500.0, widthMm = 700.0),
        )
        val after = done(before.extended("a", "f"))
        assertEquals(500.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }
}
