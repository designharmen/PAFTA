package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The last three of the drawing-office edits: mirror, join, scale. */
class MirrorJoinScaleTest {

    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes
    private fun refusal(outcome: EditOutcome) = assertIs<EditOutcome.Refused>(outcome).reason
    private fun wallIn(shapes: List<DrawnShape>, id: String) =
        assertIs<DrawnShape.Wall>(shapes.first { it.id == id })

    // --- mirror ---------------------------------------------------------------

    /** A wall to the east of a north-running centre line. */
    private fun halfPlan(): List<DrawnShape> = listOf(
        DrawnShape.Wall("axis", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 4000.0, 0.0)),
        DrawnShape.Wall("w", Vec3(1000.0, 0.0, 0.0), Vec3(1000.0, 3000.0, 0.0)),
    )

    @Test
    fun `a wall is reflected to the far side, and the one drawn stays where it is`() {
        val after = done(halfPlan().mirroredAcross("w", "axis", newId = "w2"))

        assertEquals(3, after.size)
        // The original is untouched: mirroring is how the second half of a
        // symmetrical plan appears, which needs the first half to still be there.
        assertEquals(1000.0, wallIn(after, "w").a.x, 1e-6)

        val copy = wallIn(after, "w2")
        assertEquals(-1000.0, copy.a.x, 1e-6)
        assertEquals(-1000.0, copy.b.x, 1e-6)
        assertEquals(0.0, copy.a.y, 1e-6)
        assertEquals(3000.0, copy.b.y, 1e-6)
    }

    @Test
    fun `the copy keeps the original's thickness, material and layer`() {
        val thick = listOf(
            DrawnShape.Wall("axis", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 4000.0, 0.0)),
            DrawnShape.Wall(
                "w",
                Vec3(1000.0, 0.0, 0.0),
                Vec3(1000.0, 3000.0, 0.0),
                thicknessMm = 300.0,
                material = WallMaterial.CONCRETE,
            ),
        )
        val copy = wallIn(done(thick.mirroredAcross("w", "axis", "w2")), "w2")
        assertEquals(300.0, copy.thicknessMm, 1e-6)
        assertEquals(WallMaterial.CONCRETE, copy.material)
        assertEquals(thick[1].layer, copy.layer)
    }

    @Test
    fun `a circle is reflected by its centre, and keeps its size`() {
        val plan = listOf(
            DrawnShape.Wall("axis", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 4000.0, 0.0)),
            DrawnShape.Circle("c", Vec3(600.0, 500.0, 0.0), 250.0),
        )
        val copy = assertIs<DrawnShape.Circle>(
            done(plan.mirroredAcross("c", "axis", "c2")).first { it.id == "c2" },
        )
        assertEquals(-600.0, copy.centre.x, 1e-6)
        assertEquals(500.0, copy.centre.y, 1e-6)
        assertEquals(250.0, copy.radiusMm, 1e-6)
    }

    @Test
    fun `a reflected curve still runs the way an arc has to run`() {
        val plan = listOf(
            DrawnShape.Wall("axis", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 4000.0, 0.0)),
            // A quarter turn in the first quadrant, centred at (1000, 1000).
            DrawnShape.Arc("a", Vec3(1000.0, 1000.0, 0.0), 500.0, 0.0, 90.0, thicknessMm = 200.0),
        )
        val copy = assertIs<DrawnShape.Arc>(
            done(plan.mirroredAcross("a", "axis", "a2")).first { it.id == "a2" },
        )

        assertEquals(-1000.0, copy.centre.x, 1e-6)
        assertEquals(1000.0, copy.centre.y, 1e-6)
        assertEquals(500.0, copy.radiusMm, 1e-6)
        // Reflected, the quarter that ran from east to north now runs from north
        // to west — still a quarter, still anticlockwise.
        assertEquals(90.0, copy.sweepDegrees(), 1e-6)
        assertEquals(90.0, copy.startDegrees, 1e-6)
        assertEquals(180.0, copy.endDegrees, 1e-6)
        // And it is still a piece of wall.
        assertEquals(200.0, copy.thicknessMm, 1e-6)
    }

    @Test
    fun `a rectangle is refused rather than quietly straightened`() {
        val plan = listOf(
            DrawnShape.Wall("axis", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 1000.0, 0.0)),
            DrawnShape.Rectangle("r", Vec3(500.0, 0.0, 0.0), Vec3(900.0, 300.0, 0.0)),
        )
        assertEquals(
            EditRefusal.CANNOT_MIRROR,
            refusal(plan.mirroredAcross("r", "axis", "r2")),
        )
    }

    @Test
    fun `a room is not an axis to reflect about`() {
        val plan = halfPlan() + DrawnShape.Zone("z", "Salon", Vec3(500.0, 500.0, 0.0))
        assertEquals(EditRefusal.NOT_A_LINE, refusal(plan.mirroredAcross("w", "z", "w2")))
    }

    @Test
    fun `reflecting something about itself is not a reflection`() {
        assertEquals(EditRefusal.CANNOT_MIRROR, refusal(halfPlan().mirroredAcross("w", "w", "w2")))
    }

    // --- join -----------------------------------------------------------------

    /** One wall cut into two pieces that still touch end to end. */
    private fun cutInTwo(): List<DrawnShape> = listOf(
        DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("b", Vec3(2000.0, 0.0, 0.0), Vec3(5000.0, 0.0, 0.0), thicknessMm = 200.0),
    )

    @Test
    fun `two pieces of one wall become one wall, spanning both`() {
        val after = done(cutInTwo().joined("a", "b"))

        assertEquals(1, after.size)
        val whole = wallIn(after, "a")
        assertEquals(0.0, whole.a.x, 1e-6)
        assertEquals(5000.0, whole.b.x, 1e-6)
        assertEquals(200.0, whole.thicknessMm, 1e-6)
    }

    @Test
    fun `the first one picked is the one that survives`() {
        val after = done(cutInTwo().joined("b", "a"))
        val whole = wallIn(after, "b")
        assertEquals(0.0, whole.a.x, 1e-6)
        assertEquals(5000.0, whole.b.x, 1e-6)
        assertTrue(after.none { it.id == "a" })
    }

    @Test
    fun `pieces that overlap rather than touch still join`() {
        val overlapping = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(3000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(2000.0, 0.0, 0.0), Vec3(5000.0, 0.0, 0.0)),
        )
        val whole = wallIn(done(overlapping.joined("a", "b")), "a")
        assertEquals(0.0, whole.a.x, 1e-6)
        assertEquals(5000.0, whole.b.x, 1e-6)
    }

    @Test
    fun `the second piece's doors come across onto the wall that survives`() {
        val withDoor = cutInTwo() + DrawnShape.Opening(
            id = "d1",
            wallId = "b",
            kind = OpeningKind.DOOR,
            alongMm = 1000.0,
            widthMm = 900.0,
        )
        val after = done(withDoor.joined("a", "b"))
        val door = after.filterIsInstance<DrawnShape.Opening>().single()

        // The door was 1000mm along the second piece, which started at 2000mm
        // along the joined wall.
        assertEquals("a", door.wallId)
        assertEquals(3000.0, door.alongMm, 1e-6)
    }

    @Test
    fun `two walls of a corridor are not one wall`() {
        val corridor = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(0.0, 1200.0, 0.0), Vec3(4000.0, 1200.0, 0.0)),
        )
        assertEquals(EditRefusal.NOT_IN_LINE, refusal(corridor.joined("a", "b")))
    }

    @Test
    fun `two walls at a corner are not one wall`() {
        val corner = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(4000.0, 0.0, 0.0), Vec3(4000.0, 3000.0, 0.0)),
        )
        assertEquals(EditRefusal.NOT_IN_LINE, refusal(corner.joined("a", "b")))
    }

    @Test
    fun `a gap between them is not filled in with wall nobody drew`() {
        val apart = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(3000.0, 0.0, 0.0), Vec3(5000.0, 0.0, 0.0)),
        )
        assertEquals(EditRefusal.DO_NOT_TOUCH, refusal(apart.joined("a", "b")))
    }

    @Test
    fun `a room cannot be joined to a wall`() {
        val plan = cutInTwo() + DrawnShape.Zone("z", "Salon", Vec3(100.0, 100.0, 0.0))
        assertEquals(EditRefusal.NOT_A_LINE, refusal(plan.joined("a", "z")))
    }

    // --- scale ----------------------------------------------------------------

    @Test
    fun `a wall doubled stays where it is and grows both ways`() {
        val wall = DrawnShape.Wall("w", Vec3(1000.0, 0.0, 0.0), Vec3(3000.0, 0.0, 0.0))
        val bigger = assertIs<DrawnShape.Wall>(wall.scaledBy(2.0))

        assertEquals(0.0, bigger.a.x, 1e-6)
        assertEquals(4000.0, bigger.b.x, 1e-6)
        // Its middle has not moved: 2000mm before and after.
        assertEquals(2000.0, (bigger.a.x + bigger.b.x) / 2.0, 1e-6)
    }

    @Test
    fun `halving a wall halves its length`() {
        val wall = DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))
        assertEquals(2000.0, assertIs<DrawnShape.Wall>(wall.scaledBy(0.5)).lengthMm!!, 1e-6)
    }

    @Test
    fun `a circle scales by its radius`() {
        val circle = DrawnShape.Circle("c", Vec3(500.0, 500.0, 0.0), 200.0)
        val bigger = assertIs<DrawnShape.Circle>(circle.scaledBy(1.5))
        assertEquals(300.0, bigger.radiusMm, 1e-6)
        assertEquals(500.0, bigger.centre.x, 1e-6)
    }

    @Test
    fun `scaling by one, by nothing, or backwards is not a scale`() {
        val wall = DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))
        assertNull(wall.scaledBy(1.0))
        assertNull(wall.scaledBy(0.0))
        assertNull(wall.scaledBy(-2.0))
    }

    @Test
    fun `a door is not stretched, because a door is a size that was ordered`() {
        val door = DrawnShape.Opening("d", "w", OpeningKind.DOOR, alongMm = 1000.0, widthMm = 900.0)
        assertNull(door.scaledBy(2.0))
    }
}
