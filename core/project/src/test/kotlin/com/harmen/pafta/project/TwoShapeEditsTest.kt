package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TwoShapeEditsTest {

    /** Two walls meeting at a right angle at the origin. */
    private fun corner(): List<DrawnShape> = listOf(
        DrawnShape.Wall("a", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    private fun done(outcome: EditOutcome) = assertIs<EditOutcome.Done>(outcome).shapes
    private fun wallIn(shapes: List<DrawnShape>, id: String) =
        assertIs<DrawnShape.Wall>(shapes.first { it.id == id })

    // --- fillet ---------------------------------------------------------------

    @Test
    fun `rounding a corner cuts both walls back and adds the arc between them`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))

        // Three shapes now: two walls and the arc.
        assertEquals(3, after.size)
        val arc = assertIs<DrawnShape.Arc>(after.last())
        assertEquals(500.0, arc.radiusMm, 1e-6)
        assertEquals(500.0, arc.centre.x, 1e-6)
        assertEquals(500.0, arc.centre.y, 1e-6)

        // At a right angle each wall stops a radius short of the corner.
        assertEquals(500.0, wallIn(after, "a").b.x, 1e-6)
        assertEquals(500.0, wallIn(after, "b").a.y, 1e-6)
    }

    @Test
    fun `the far ends of both walls are left where they were`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        assertEquals(4000.0, wallIn(after, "a").a.x, 1e-6)
        assertEquals(3000.0, wallIn(after, "b").b.y, 1e-6)
    }

    @Test
    fun `the arc lands on the layer of the wall it was asked from`() {
        val after = done(corner().filleted("a", "b", radiusMm = 500.0))
        assertEquals(corner().first().layer, assertIs<DrawnShape.Arc>(after.last()).layer)
    }

    @Test
    fun `a radius the walls cannot give is refused, and nothing is changed`() {
        val outcome = corner().filleted("a", "b", radiusMm = 9000.0)
        assertEquals(EditRefusal.DOES_NOT_FIT, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `two parallel walls have no corner to round`() {
        val parallel = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            DrawnShape.Wall("b", Vec3(0.0, 1000.0, 0.0), Vec3(4000.0, 1000.0, 0.0)),
        )
        val outcome = parallel.filleted("a", "b", radiusMm = 100.0)
        assertEquals(EditRefusal.NO_CORNER, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `a room is not a line, so it cannot be filleted`() {
        val withRoom = corner() + DrawnShape.Zone("z1", "Salon", Vec3(100.0, 100.0, 0.0))
        val outcome = withRoom.filleted("a", "z1", radiusMm = 100.0)
        assertEquals(EditRefusal.NOT_A_LINE, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `one shape is not two, so it is not a corner`() {
        val outcome = corner().filleted("a", "a", radiusMm = 100.0)
        assertEquals(EditRefusal.NO_CORNER, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `rounding a corner does not drag the rest of the room with it`() {
        // The opposite of what changing a wall's length does, and on purpose:
        // tidying a junction is the moment the rest must stay put.
        val room = corner() + DrawnShape.Wall(
            "c",
            Vec3(0.0, 3000.0, 0.0),
            Vec3(4000.0, 3000.0, 0.0),
        )
        val after = done(room.filleted("a", "b", radiusMm = 500.0))
        assertEquals(room.first { it.id == "c" }, after.first { it.id == "c" })
    }

    // --- chamfer --------------------------------------------------------------

    @Test
    fun `a chamfer cuts both walls back and draws the flat between them`() {
        val after = done(corner().chamfered("a", "b", distanceMm = 400.0))

        assertEquals(3, after.size)
        assertEquals(400.0, wallIn(after, "a").b.x, 1e-6)
        assertEquals(400.0, wallIn(after, "b").a.y, 1e-6)

        // A wall, not a line: the flat across a chamfered wall corner is built,
        // so drawing it as linework left a hole in the building. `RoundedCornerTest`
        // is where that rule is spelled out.
        val flat = assertIs<DrawnShape.Wall>(after.last())
        assertEquals(400.0, flat.a.x, 1e-6)
        assertEquals(400.0, flat.b.y, 1e-6)
    }

    @Test
    fun `a chamfer longer than the wall is refused`() {
        val outcome = corner().chamfered("a", "b", distanceMm = 9000.0)
        assertEquals(EditRefusal.DOES_NOT_FIT, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    // --- trim -----------------------------------------------------------------

    /** A wall running east, crossed in the middle by a fence. */
    private fun crossing(): List<DrawnShape> = listOf(
        DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
        DrawnShape.Wall("f", Vec3(2000.0, -1000.0, 0.0), Vec3(2000.0, 1000.0, 0.0)),
    )

    @Test
    fun `the piece that was pointed at is the piece that goes`() {
        // Pointing at the right-hand half leaves the left-hand half.
        val after = done(crossing().trimmed("a", "f", at = Vec2(3500.0, 0.0)))
        val kept = wallIn(after, "a")
        assertEquals(0.0, kept.a.x, 1e-6)
        assertEquals(2000.0, kept.b.x, 1e-6)
    }

    @Test
    fun `pointing at the other half leaves the other half`() {
        val after = done(crossing().trimmed("a", "f", at = Vec2(500.0, 0.0)))
        val kept = wallIn(after, "a")
        assertEquals(2000.0, kept.a.x, 1e-6)
        assertEquals(4000.0, kept.b.x, 1e-6)
    }

    @Test
    fun `the boundary itself is left alone`() {
        val after = done(crossing().trimmed("a", "f", at = Vec2(3500.0, 0.0)))
        assertEquals(crossing().first { it.id == "f" }, after.first { it.id == "f" })
    }

    @Test
    fun `something that does not cross cannot trim`() {
        val apart = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0)),
            DrawnShape.Wall("f", Vec3(9000.0, -1000.0, 0.0), Vec3(9000.0, 1000.0, 0.0)),
        )
        val outcome = apart.trimmed("a", "f", at = Vec2(500.0, 0.0))
        assertEquals(EditRefusal.DOES_NOT_CROSS, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    // --- extend ---------------------------------------------------------------

    @Test
    fun `a short wall is stretched until it reaches`() {
        val short = listOf(
            DrawnShape.Wall("a", Vec3(0.0, 0.0, 0.0), Vec3(1000.0, 0.0, 0.0)),
            DrawnShape.Wall("f", Vec3(2000.0, -1000.0, 0.0), Vec3(2000.0, 1000.0, 0.0)),
        )
        val after = done(short.extended("a", "f"))
        val stretched = wallIn(after, "a")
        assertEquals(0.0, stretched.a.x, 1e-6)
        assertEquals(2000.0, stretched.b.x, 1e-6)
    }

    @Test
    fun `a wall that already reaches has nothing to stretch`() {
        val outcome = crossing().extended("a", "f")
        assertEquals(EditRefusal.ALREADY_REACHES, assertIs<EditOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `a wall keeps its thickness and its layer through every edit`() {
        val thick = listOf(
            DrawnShape.Wall(
                "a",
                Vec3(4000.0, 0.0, 0.0),
                Vec3(0.0, 0.0, 0.0),
                thicknessMm = 300.0,
                material = WallMaterial.CONCRETE,
            ),
            DrawnShape.Wall("b", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0)),
        )
        val after = done(thick.filleted("a", "b", radiusMm = 400.0))
        val wall = wallIn(after, "a")

        assertEquals(300.0, wall.thicknessMm, 1e-6)
        assertEquals(WallMaterial.CONCRETE, wall.material)
        assertEquals(thick.first().layer, wall.layer)
    }

    @Test
    fun `only a wall or a line can be worked on by these tools`() {
        assertTrue(DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)).isLine())
        assertTrue(DrawnShape.Line("l", Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)).isLine())
        assertTrue(!DrawnShape.Circle("c", Vec3(0.0, 0.0, 0.0), 100.0).isLine())
        assertTrue(!DrawnShape.Zone("z", "Salon", Vec3(0.0, 0.0, 0.0)).isLine())
    }
}
