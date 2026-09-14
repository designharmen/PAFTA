package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The bug the device found twice: a wall turned round behind the user's back.
 *
 * `fillet` and `chamfer` hand each arm back with the corner end last, whichever
 * way round it was drawn in. For half of all walls that is the wall reversed —
 * and since a door holds how far it is from its wall's *start*, reversing the
 * wall sends every door in it to the other end. The first attempt at keeping
 * doors still made this case worse rather than better, which is what "daha
 * fazla kaydı" meant.
 */
class WallDirectionTest {

    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes
    private fun wallIn(shapes: List<DrawnShape>, id: String) =
        assertIs<DrawnShape.Wall>(shapes.first { it.id == id })

    /**
     * A corner drawn the way a person actually draws one: out from the corner
     * along the bottom, then out from the corner up the side. Both walls start
     * at the corner, which is the orientation `fillet` reverses.
     */
    private fun drawnFromTheCorner(): List<DrawnShape> = listOf(
        DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    /** Where the opening actually is on the sheet. */
    private fun doorAt(shapes: List<DrawnShape>): Vec2 {
        val door = shapes.filterIsInstance<DrawnShape.Opening>().single()
        val wall = shapes.filterIsInstance<DrawnShape.Wall>().first { it.id == door.wallId }
        val along = wall.b.toVec2() - wall.a.toVec2()
        return wall.a.toVec2() + along / along.length * door.alongMm
    }

    @Test
    fun `a rounded wall still runs the way it was drawn`() {
        val after = done(drawnFromTheCorner().filleted("a", "b", radiusMm = 500.0))

        // Wall `a` was drawn west to east and must still run west to east.
        val wall = wallIn(after, "a")
        assertEquals(500.0, wall.a.x, 1e-6)
        assertEquals(4000.0, wall.b.x, 1e-6)
        assertTrue(wall.b.x > wall.a.x, "the wall came back pointing the other way")
    }

    @Test
    fun `a door in a wall drawn from the corner does not move at all`() {
        val before = drawnFromTheCorner() + DrawnShape.Opening(
            id = "d1",
            wallId = "a",
            kind = OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
        )
        val after = done(before.filleted("a", "b", radiusMm = 500.0))

        // 2000mm from a start that has moved 500mm along is 1500mm from the
        // new start — and the same place on the sheet.
        assertEquals(1500.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
        assertTrue(
            doorAt(before).distanceTo(doorAt(after)) < 1e-6,
            "the door moved to ${doorAt(after)} from ${doorAt(before)}",
        )
    }

    @Test
    fun `a chamfered wall keeps its direction and its doors too`() {
        val before = drawnFromTheCorner() + DrawnShape.Opening(
            id = "d1",
            wallId = "a",
            kind = OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
        )
        val after = done(before.chamfered("a", "b", distanceMm = 400.0))

        val wall = wallIn(after, "a")
        assertTrue(wall.b.x > wall.a.x)
        assertTrue(doorAt(before).distanceTo(doorAt(after)) < 1e-6)
    }

    @Test
    fun `it holds whichever way round the two walls were drawn`() {
        // All four combinations of which end of each wall is at the corner.
        val corners = listOf(
            Vec3(0.0, 0.0, 0.0) to Vec3(4000.0, 0.0, 0.0),
            Vec3(4000.0, 0.0, 0.0) to Vec3(0.0, 0.0, 0.0),
        )
        val sides = listOf(
            Vec3(0.0, 0.0, 0.0) to Vec3(0.0, 3000.0, 0.0),
            Vec3(0.0, 3000.0, 0.0) to Vec3(0.0, 0.0, 0.0),
        )

        for ((aStart, aEnd) in corners) {
            for ((bStart, bEnd) in sides) {
                val plan = listOf<DrawnShape>(
                    DrawnShape.Wall("a", aStart, aEnd, thicknessMm = 200.0),
                    DrawnShape.Wall("b", bStart, bEnd, thicknessMm = 200.0),
                    DrawnShape.Opening("d1", "a", OpeningKind.DOOR, alongMm = 1500.0, widthMm = 900.0),
                )
                val after = done(plan.filleted("a", "b", radiusMm = 500.0))

                val wall = wallIn(after, "a")
                val was = plan.first { it.id == "a" } as DrawnShape.Wall
                assertTrue(
                    (wall.b.toVec2() - wall.a.toVec2()) dot (was.b.toVec2() - was.a.toVec2()) > 0.0,
                    "wall a turned round when drawn $aStart -> $aEnd",
                )
                assertTrue(
                    doorAt(plan).distanceTo(doorAt(after)) < 1e-6,
                    "the door moved when the walls were drawn $aStart -> $aEnd and $bStart -> $bEnd",
                )
            }
        }
    }
}

/** Two walls that nearly line up, which is the only kind a finger ever draws. */
class JoinToleranceTest {

    private fun refusal(outcome: EditOutcome) = assertIs<EditOutcome.Refused>(outcome).reason
    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes

    @Test
    fun `two pieces a few millimetres out of line are still one wall`() {
        // 6mm off over 3m: invisible on a plan, and far outside the millimetre
        // the first attempt demanded. It refused every pair it was given.
        val nearly = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("b", Vec3(2000.0, 6.0, 0.0), Vec3(5000.0, 6.0, 0.0), thicknessMm = 200.0),
        )
        val whole = assertIs<DrawnShape.Wall>(done(nearly.joined("a", "b")).single())
        assertEquals(0.0, whole.a.x, 1e-6)
        assertEquals(5000.0, whole.b.x, 1e-6)
    }

    @Test
    fun `a few millimetres of gap between them is still one wall`() {
        val gapped = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("b", Vec3(2040.0, 0.0, 0.0), Vec3(5000.0, 0.0, 0.0), thicknessMm = 200.0),
        )
        assertEquals(1, done(gapped.joined("a", "b")).size)
    }

    @Test
    fun `how straight is straight enough comes from how thick the wall is`() {
        // 60mm out of line: inside a 200mm wall, outside a 100mm one.
        fun pair(thickness: Double) = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0), thicknessMm = thickness),
            DrawnShape.Wall("b", Vec3(2000.0, 60.0, 0.0), Vec3(5000.0, 60.0, 0.0), thicknessMm = thickness),
        )
        assertEquals(1, done(pair(200.0).joined("a", "b")).size)
        assertEquals(EditRefusal.NOT_IN_LINE, refusal(pair(100.0).joined("a", "b")))
    }

    @Test
    fun `a corridor is still two walls, however thick they are`() {
        val corridor = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 300.0),
            DrawnShape.Wall("b", Vec3(0.0, 1200.0, 0.0), Vec3(4000.0, 1200.0, 0.0), thicknessMm = 300.0),
        )
        assertEquals(EditRefusal.NOT_IN_LINE, refusal(corridor.joined("a", "b")))
    }

    @Test
    fun `two pencil lines are held to a tighter line than two walls`() {
        val lines = listOf(
            DrawnShape.Line("a", Vec3(0.0, 0.0, 0.0), Vec3(2000.0, 0.0, 0.0)),
            DrawnShape.Line("b", Vec3(2000.0, 60.0, 0.0), Vec3(5000.0, 60.0, 0.0)),
        )
        assertEquals(EditRefusal.NOT_IN_LINE, refusal(lines.joined("a", "b")))
    }
}

/** Stretching one wall to another, from either end of the conversation. */
class ExtendBothWaysTest {

    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes
    private fun refusal(outcome: EditOutcome) = assertIs<EditOutcome.Refused>(outcome).reason
    private fun wallIn(shapes: List<DrawnShape>, id: String) =
        assertIs<DrawnShape.Wall>(shapes.first { it.id == id })

    /** A short wall aiming at a long one it does not reach. */
    private fun shortAndLong(): List<DrawnShape> = listOf(
        DrawnShape.Wall("short", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("long", Vec3(3000.0, -2000.0, 0.0), Vec3(3000.0, 2000.0, 0.0), thicknessMm = 200.0),
    )

    @Test
    fun `picking the short one first stretches the short one`() {
        val after = done(shortAndLong().extended("short", "long"))
        assertEquals(3000.0, wallIn(after, "short").b.x, 1e-6)
    }

    @Test
    fun `picking the long one first still stretches the short one`() {
        // Which of two walls is short is obvious looking at the plan. Refusing
        // because they were pointed at in the other order is the tool being
        // difficult for its own sake.
        val after = done(shortAndLong().extended("long", "short"))
        assertEquals(3000.0, wallIn(after, "short").b.x, 1e-6)
        assertEquals(4000.0, wallIn(after, "long").b.y - wallIn(after, "long").a.y, 1e-6)
    }

    @Test
    fun `a wall reaches the face of the wall it meets, not the line up its middle`() {
        // The target's centre line stops 50mm short of where the short wall's
        // line crosses — but the wall is 300mm thick, so its face is there.
        val past = listOf(
            DrawnShape.Wall("short", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("long", Vec3(3000.0, -2000.0, 0.0), Vec3(3000.0, -50.0, 0.0), thicknessMm = 300.0),
        )
        assertEquals(3000.0, wallIn(done(past.extended("short", "long")), "short").b.x, 1e-6)
    }

    @Test
    fun `a wall that would miss even at full stretch says so`() {
        val miss = listOf(
            DrawnShape.Wall("short", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0), thicknessMm = 200.0),
            // Well above the short wall's line and short enough not to reach it.
            DrawnShape.Wall("long", Vec3(3000.0, 5000.0, 0.0), Vec3(3000.0, 9000.0, 0.0), thicknessMm = 200.0),
        )
        assertEquals(EditRefusal.WOULD_MISS, refusal(miss.extended("short", "long")))
    }

    @Test
    fun `two walls that already cross have nothing to stretch`() {
        val crossing = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            DrawnShape.Wall("f", Vec3(2000.0, -1000.0, 0.0), Vec3(2000.0, 1000.0, 0.0)),
        )
        assertEquals(EditRefusal.ALREADY_REACHES, refusal(crossing.extended("a", "f")))
    }

    @Test
    fun `two parallel walls are still refused as having no corner`() {
        val parallel = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(0.0, 1000.0, 0.0), Vec3(4000.0, 1000.0, 0.0)),
        )
        assertEquals(EditRefusal.NO_CORNER, refusal(parallel.extended("a", "b")))
    }

    @Test
    fun `a stretched wall keeps its doors where they are`() {
        val before = shortAndLong() + DrawnShape.Opening(
            "d1",
            "short",
            OpeningKind.DOOR,
            alongMm = 500.0,
            widthMm = 700.0,
        )
        val after = done(before.extended("short", "long"))
        assertEquals(500.0, after.filterIsInstance<DrawnShape.Opening>().single().alongMm, 1e-6)
    }
}
