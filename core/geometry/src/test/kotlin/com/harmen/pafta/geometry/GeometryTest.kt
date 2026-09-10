package com.harmen.pafta.geometry

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Vec2Test {
    @Test
    fun `arithmetic and length`() {
        val a = Vec2(3.0, 4.0)
        assertEquals(5.0, a.length, 1e-12)
        assertEquals(Vec2(4.0, 6.0), a + Vec2(1.0, 2.0))
        assertEquals(Vec2(2.0, 2.0), a - Vec2(1.0, 2.0))
        assertEquals(Vec2(6.0, 8.0), a * 2.0)
        assertEquals(11.0, a dot Vec2(1.0, 2.0), 1e-12)
        assertEquals(2.0, a cross Vec2(1.0, 2.0), 1e-12)
    }

    @Test
    fun `normalizing a zero vector does not produce NaN`() {
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalized())
    }

    @Test
    fun `rotation by 90 degrees matches the perpendicular`() {
        val r = Vec2.X.rotated(PI / 2)
        assertTrue(r.isCloseTo(Vec2.Y), "expected +Y, got $r")
        assertTrue(Vec2.X.perpendicular().isCloseTo(Vec2.Y))
    }
}

class Vec3Test {
    @Test
    fun `cross product is right handed`() {
        assertTrue((Vec3.X cross Vec3.Y).isCloseTo(Vec3.Z))
    }

    @Test
    fun `distance in three dimensions`() {
        assertEquals(
            13.0,
            Vec3(0.0, 0.0, 0.0).distanceTo(Vec3(3.0, 4.0, 12.0)),
            1e-12,
        )
    }

    @Test
    fun `angle between perpendicular rays is 90 degrees`() {
        val a = angleBetween(Vec3(1.0, 0.0, 0.0), Vec3.ZERO, Vec3(0.0, 1.0, 0.0))
        assertEquals(PI / 2, a, 1e-12)
    }

    @Test
    fun `angle with a degenerate ray is zero rather than NaN`() {
        assertEquals(0.0, angleBetween(Vec3.ZERO, Vec3.ZERO, Vec3.X), 1e-12)
    }
}

class AabbTest {
    @Test
    fun `empty box is the identity for union`() {
        val box = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 1.0))
        assertTrue(Aabb.EMPTY.isEmpty)
        assertEquals(box, Aabb.EMPTY.union(box))
        assertEquals(box, box.union(Aabb.EMPTY))
    }

    @Test
    fun `bounds of a point cloud`() {
        val box = Aabb.of(
            listOf(Vec3(-1.0, 2.0, 0.0), Vec3(5.0, -3.0, 7.0), Vec3(0.0, 0.0, 0.0)),
        )
        assertEquals(Vec3(-1.0, -3.0, 0.0), box.min)
        assertEquals(Vec3(5.0, 2.0, 7.0), box.max)
        assertTrue(box.contains(Vec3(0.0, 0.0, 1.0)))
        assertFalse(box.contains(Vec3(9.0, 0.0, 1.0)))
    }
}

class SegmentTest {
    @Test
    fun `closest point is clamped to the segment`() {
        val s = Segment2(Vec2(0.0, 0.0), Vec2(10.0, 0.0))
        assertEquals(Vec2(5.0, 0.0), s.closestPointTo(Vec2(5.0, 3.0)))
        assertEquals(Vec2(0.0, 0.0), s.closestPointTo(Vec2(-4.0, 1.0)))
        assertEquals(Vec2(10.0, 0.0), s.closestPointTo(Vec2(14.0, 1.0)))
        assertEquals(3.0, s.distanceTo(Vec2(5.0, 3.0)), 1e-12)
    }

    @Test
    fun `crossing segments intersect at the expected point`() {
        val r = intersect(
            Segment2(Vec2(0.0, 0.0), Vec2(10.0, 10.0)),
            Segment2(Vec2(0.0, 10.0), Vec2(10.0, 0.0)),
        )
        assertIs<Intersection2.Point>(r)
        assertTrue(r.point.isCloseTo(Vec2(5.0, 5.0)))
    }

    @Test
    fun `segments that would cross only if extended do not intersect`() {
        assertEquals(
            Intersection2.None,
            intersect(
                Segment2(Vec2(0.0, 0.0), Vec2(1.0, 1.0)),
                Segment2(Vec2(5.0, 0.0), Vec2(6.0, 1.0)),
            ),
        )
    }

    @Test
    fun `overlapping collinear segments are reported as collinear`() {
        assertEquals(
            Intersection2.Collinear,
            intersect(
                Segment2(Vec2(0.0, 0.0), Vec2(10.0, 0.0)),
                Segment2(Vec2(5.0, 0.0), Vec2(15.0, 0.0)),
            ),
        )
    }
}

class PolygonTest {
    // A 5500 x 4500 mm room, the kind of figure the reference drawing labels.
    private val room = listOf(
        Vec2(0.0, 0.0),
        Vec2(5500.0, 0.0),
        Vec2(5500.0, 4500.0),
        Vec2(0.0, 4500.0),
    )

    @Test
    fun `area and perimeter of a rectangle`() {
        assertEquals(24_750_000.0, area(room), 1e-6)
        assertEquals(20_000.0, perimeter(room), 1e-6)
    }

    @Test
    fun `winding direction flips the sign but not the magnitude`() {
        assertTrue(signedArea(room) > 0)
        assertTrue(signedArea(room.reversed()) < 0)
        assertEquals(area(room), area(room.reversed()), 1e-6)
    }

    @Test
    fun `centroid of a rectangle is its middle`() {
        assertTrue(centroid(room).isCloseTo(Vec2(2750.0, 2250.0), 1e-6))
    }

    @Test
    fun `degenerate polygons report zero area instead of throwing`() {
        assertEquals(0.0, area(listOf(Vec2.ZERO, Vec2(1.0, 1.0))), 1e-12)
        assertEquals(0.0, area(emptyList()), 1e-12)
    }

    @Test
    fun `point containment`() {
        assertTrue(containsPoint(room, Vec2(100.0, 100.0)))
        assertFalse(containsPoint(room, Vec2(-100.0, 100.0)))
        assertFalse(containsPoint(room, Vec2(6000.0, 2000.0)))
    }

    @Test
    fun `polyline length ignores the closing leg`() {
        assertEquals(15_500.0, polylineLength(room), 1e-6)
    }
}

class TessellationTest {
    @Test
    fun `a quarter arc starts and ends on the axes`() {
        val pts = tessellateArc(Vec2.ZERO, 100.0, 0.0, PI / 2, segments = 8)
        assertEquals(9, pts.size)
        assertTrue(pts.first().isCloseTo(Vec2(100.0, 0.0)))
        assertTrue(pts.last().isCloseTo(Vec2(0.0, 100.0)))
        pts.forEach { assertEquals(100.0, it.length, 1e-9) }
    }

    @Test
    fun `an arc whose end angle wraps past zero sweeps the long way round`() {
        // DXF semantics: 270 deg -> 90 deg is a 180 deg sweep through 0/360.
        val pts = tessellateArc(Vec2.ZERO, 1.0, 3 * PI / 2, PI / 2, segments = 4)
        assertTrue(pts.first().isCloseTo(Vec2(0.0, -1.0)))
        assertTrue(pts.last().isCloseTo(Vec2(0.0, 1.0)))
        assertTrue(pts[2].isCloseTo(Vec2(1.0, 0.0)), "midpoint should pass through +X, got ${pts[2]}")
    }

    @Test
    fun `a tessellated circle is closed and on the radius`() {
        val pts = tessellateCircle(Vec2(10.0, 20.0), 5.0, segments = 16)
        assertEquals(16, pts.size)
        pts.forEach { assertEquals(5.0, it.distanceTo(Vec2(10.0, 20.0)), 1e-9) }
        // Implicitly closed: area approaches PI r^2 as segments grow.
        val a = area(tessellateCircle(Vec2.ZERO, 5.0, segments = 512))
        assertEquals(PI * 25.0, a, 0.01)
    }
}
