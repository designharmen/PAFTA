package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilletTest {

    /** A right angle at the origin: one arm east, one arm north. */
    private val east = Segment2(Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
    private val north = Segment2(Vec2(0.0, 0.0), Vec2(0.0, 3000.0))

    @Test
    fun `a right angle is rounded off, and both arms are cut back to the arc`() {
        val result = assertNotNull(fillet(east, north, radiusMm = 500.0))

        // At a right angle the tangent length equals the radius, so each arm
        // stops 500mm short of the corner.
        assertEquals(500.0, result.first.b.x, 1e-6)
        assertEquals(0.0, result.first.b.y, 1e-6)
        assertEquals(0.0, result.second.a.x, 1e-6)
        assertEquals(500.0, result.second.a.y, 1e-6)

        // The centre is a radius from each arm, so at (500, 500).
        assertEquals(500.0, result.centre.x, 1e-6)
        assertEquals(500.0, result.centre.y, 1e-6)
        assertEquals(500.0, result.radiusMm, 1e-6)
    }

    @Test
    fun `the arc is the quarter of the corner, not the rest of the circle`() {
        val result = assertNotNull(fillet(east, north, radiusMm = 500.0))
        val sweep = (result.endDegrees - result.startDegrees + 360.0) % 360.0
        assertEquals(90.0, sweep, 1e-6)
    }

    @Test
    fun `the far ends are the ones left alone`() {
        val result = assertNotNull(fillet(east, north, radiusMm = 500.0))
        assertEquals(4000.0, result.first.a.x, 1e-6)
        assertEquals(3000.0, result.second.b.y, 1e-6)
    }

    @Test
    fun `a radius too big for the arms is refused rather than drawn wrong`() {
        val short = Segment2(Vec2(0.0, 0.0), Vec2(300.0, 0.0))
        assertNull(fillet(short, north, radiusMm = 500.0))
    }

    @Test
    fun `segments that never meet have no corner to round`() {
        val parallel = Segment2(Vec2(0.0, 1000.0), Vec2(4000.0, 1000.0))
        assertNull(fillet(east, parallel, radiusMm = 100.0))
    }

    @Test
    fun `two segments that fall short of each other are still filleted`() {
        // Neither reaches the corner at the origin; fillet works on the lines
        // they lie on, which is what makes it useful on a real drawing.
        val gapEast = Segment2(Vec2(1000.0, 0.0), Vec2(4000.0, 0.0))
        val gapNorth = Segment2(Vec2(0.0, 1000.0), Vec2(0.0, 3000.0))

        // 1500mm setback is more than either arm has spare, so it is refused;
        // 400mm fits inside both.
        assertNull(fillet(gapEast, gapNorth, radiusMm = 4000.0))
        val result = assertNotNull(fillet(gapEast, gapNorth, radiusMm = 400.0))
        assertEquals(400.0, result.centre.x, 1e-6)
        assertEquals(400.0, result.centre.y, 1e-6)
    }

    @Test
    fun `a zero or negative radius is not a fillet`() {
        assertNull(fillet(east, north, radiusMm = 0.0))
        assertNull(fillet(east, north, radiusMm = -100.0))
    }
}

class ChamferTest {

    private val east = Segment2(Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
    private val north = Segment2(Vec2(0.0, 0.0), Vec2(0.0, 3000.0))

    @Test
    fun `a corner is cut off straight, each arm by its own distance`() {
        val result = assertNotNull(chamfer(east, north, firstMm = 200.0, secondMm = 500.0))

        assertEquals(200.0, result.first.b.x, 1e-6)
        assertEquals(500.0, result.second.a.y, 1e-6)
        // The flat runs from one cut to the other.
        assertEquals(Vec2(200.0, 0.0), result.bridge.a)
        assertEquals(Vec2(0.0, 500.0), result.bridge.b)
    }

    @Test
    fun `a chamfer longer than the arm it cuts is refused`() {
        val short = Segment2(Vec2(0.0, 0.0), Vec2(300.0, 0.0))
        assertNull(chamfer(short, north, firstMm = 500.0, secondMm = 500.0))
    }
}

class OffsetTest {

    @Test
    fun `a segment moves to the left of its own direction, and right when negative`() {
        val east = Segment2(Vec2(0.0, 0.0), Vec2(1000.0, 0.0))

        assertEquals(200.0, assertNotNull(offset(east, 200.0)).a.y, 1e-6)
        assertEquals(-200.0, assertNotNull(offset(east, -200.0)).a.y, 1e-6)
        // It stays the same length and the same direction.
        assertEquals(1000.0, assertNotNull(offset(east, 200.0)).length, 1e-6)
    }

    @Test
    fun `an offset run keeps its corners closed`() {
        // An L: east then north.
        val corner = listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4000.0, 3000.0))
        val moved = assertNotNull(offsetPolyline(corner, 500.0))

        assertEquals(3, moved.size)
        // Each end simply moves sideways...
        assertEquals(Vec2(0.0, 500.0), moved[0])
        // ...and the corner lands where the two moved lines actually cross,
        // rather than leaving a gap between two separate sticks.
        assertEquals(3500.0, moved[1].x, 1e-6)
        assertEquals(500.0, moved[1].y, 1e-6)
    }

    @Test
    fun `a closed run offsets inward all the way round`() {
        val room = listOf(
            Vec2(0.0, 0.0),
            Vec2(4000.0, 0.0),
            Vec2(4000.0, 3000.0),
            Vec2(0.0, 3000.0),
        )
        val inner = assertNotNull(offsetPolyline(room, 100.0, closed = true))

        assertEquals(4, inner.size)
        // 100mm in on every side: 3800 x 2800, the same answer the room
        // measurement gives, arrived at a different way.
        assertEquals(3800.0 * 2800.0, area(inner), 1.0)
    }

    @Test
    fun `a run of one point is not a run`() {
        assertNull(offsetPolyline(listOf(Vec2.ZERO), 100.0))
    }
}

class TrimAndExtendTest {

    private val east = Segment2(Vec2(0.0, 0.0), Vec2(4000.0, 0.0))
    private val fence = Segment2(Vec2(2000.0, -1000.0), Vec2(2000.0, 1000.0))

    @Test
    fun `the half that was pointed at is the half that survives`() {
        val left = assertNotNull(trim(east, fence, keep = Vec2(500.0, 0.0)))
        assertEquals(0.0, left.a.x, 1e-6)
        assertEquals(2000.0, left.b.x, 1e-6)

        val right = assertNotNull(trim(east, fence, keep = Vec2(3500.0, 0.0)))
        assertEquals(2000.0, right.a.x, 1e-6)
        assertEquals(4000.0, right.b.x, 1e-6)
    }

    @Test
    fun `a boundary that does not cross trims nothing, rather than guessing`() {
        val elsewhere = Segment2(Vec2(9000.0, -1000.0), Vec2(9000.0, 1000.0))
        assertNull(trim(east, elsewhere, keep = Vec2(500.0, 0.0)))
    }

    @Test
    fun `extending reaches the boundary from whichever end is nearer`() {
        val short = Segment2(Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        val stretched = assertNotNull(extend(short, fence))
        assertEquals(0.0, stretched.a.x, 1e-6)
        assertEquals(2000.0, stretched.b.x, 1e-6)

        // Drawn the other way round, the other end is the one that moves.
        val backwards = Segment2(Vec2(4000.0, 0.0), Vec2(3000.0, 0.0))
        val alsoStretched = assertNotNull(extend(backwards, fence))
        assertEquals(2000.0, alsoStretched.b.x, 1e-6)
        assertEquals(4000.0, alsoStretched.a.x, 1e-6)
    }

    @Test
    fun `a segment that already crosses has nothing to extend to`() {
        assertNull(extend(east, fence))
    }

    @Test
    fun `a boundary the segment would miss even at full stretch is refused`() {
        // The fence is only 2m tall and sits well above this line's path.
        val tooHigh = Segment2(Vec2(2000.0, 5000.0), Vec2(2000.0, 9000.0))
        val short = Segment2(Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        assertNull(extend(short, tooHigh))
    }
}

class TransformTest {

    private val square = listOf(
        Vec2(0.0, 0.0),
        Vec2(1000.0, 0.0),
        Vec2(1000.0, 1000.0),
        Vec2(0.0, 1000.0),
    )

    @Test
    fun `a quarter turn about the origin sends east to north`() {
        val turned = rotated(square, Vec2.ZERO, 90.0)
        assertEquals(0.0, turned[1].x, 1e-6)
        assertEquals(1000.0, turned[1].y, 1e-6)
        // Turning changes where a shape is, never how big it is.
        assertEquals(area(square), area(turned), 1e-6)
    }

    @Test
    fun `a full turn puts everything back`() {
        rotated(square, Vec2(123.0, 456.0), 360.0).forEachIndexed { i, p ->
            assertTrue(p.distanceTo(square[i]) < 1e-6)
        }
    }

    @Test
    fun `mirroring in a vertical line flips left and right, not up and down`() {
        val flipped = mirrored(square, Vec2(0.0, 0.0), Vec2(0.0, 1.0))
        assertEquals(-1000.0, flipped[1].x, 1e-6)
        assertEquals(0.0, flipped[1].y, 1e-6)
        assertEquals(area(square), area(flipped), 1e-6)
    }

    @Test
    fun `mirroring twice in the same line is doing nothing`() {
        val there = mirrored(square, Vec2(500.0, 0.0), Vec2(1.0, 1.0))
        val back = mirrored(there, Vec2(500.0, 0.0), Vec2(1.0, 1.0))
        back.forEachIndexed { i, p -> assertTrue(p.distanceTo(square[i]) < 1e-6) }
    }

    @Test
    fun `doubling a shape quadruples its area`() {
        val bigger = scaled(square, Vec2.ZERO, 2.0)
        assertEquals(area(square) * 4.0, area(bigger), 1e-6)
        assertEquals(2000.0, bigger[2].x, 1e-6)
    }

    @Test
    fun `scaling about a point leaves that point where it is`() {
        val pivot = Vec2(1000.0, 1000.0)
        val bigger = scaled(square, pivot, 3.0)
        assertTrue(abs(bigger[2].x - pivot.x) < 1e-6 && abs(bigger[2].y - pivot.y) < 1e-6)
    }
}
