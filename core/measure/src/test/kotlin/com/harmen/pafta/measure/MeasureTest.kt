package com.harmen.pafta.measure

import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.units.AngleUnit
import com.harmen.pafta.units.AreaUnit
import com.harmen.pafta.units.LengthFormat
import com.harmen.pafta.units.LengthUnit
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementTest {
    @Test
    fun `distance measures and labels a wall run`() {
        val m = Measurement.Distance("d1", Vec3.ZERO, Vec3(5500.0, 0.0, 0.0))
        assertEquals(5500.0, m.rawValue, 1e-9)
        assertEquals("5500mm", m.label())
        assertEquals(Vec3(2750.0, 0.0, 0.0), m.anchor)
        assertEquals(Vec3(5500.0, 0.0, 0.0), m.delta)
    }

    @Test
    fun `changing the display unit does not lose precision`() {
        val m = Measurement.Distance("d1", Vec3.ZERO, Vec3(5500.0, 0.0, 0.0))
        assertEquals("5500mm", m.label())
        assertEquals("5.5m", m.label(MeasurementDisplay(lengthFormat = LengthFormat.METRES)))
        assertEquals(
            "550cm",
            m.label(MeasurementDisplay(lengthFormat = LengthFormat(LengthUnit.CENTIMETRE))),
        )
    }

    @Test
    fun `polyline reports total and per leg lengths`() {
        val m = Measurement.Polyline(
            "p1",
            listOf(Vec3.ZERO, Vec3(3000.0, 0.0, 0.0), Vec3(3000.0, 4000.0, 0.0)),
        )
        assertEquals(7000.0, m.rawValue, 1e-9)
        assertEquals(listOf(3000.0, 4000.0), m.segmentLengths)
        assertEquals("7000mm", m.label())
    }

    @Test
    fun `angle is unsigned and labelled in degrees`() {
        val m = Measurement.Angle("a1", Vec3(1000.0, 0.0, 0.0), Vec3.ZERO, Vec3(0.0, 1000.0, 0.0))
        assertEquals(PI / 2, m.rawValue, 1e-12)
        assertEquals("90°", m.label())
        assertEquals(
            "1.6 rad",
            m.label(MeasurementDisplay(angleUnit = AngleUnit.RADIANS)),
        )
    }

    @Test
    fun `area of a room is reported in square metres`() {
        val m = Measurement.Area(
            "s1",
            listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(5500.0, 0.0, 0.0),
                Vec3(5500.0, 4500.0, 0.0),
                Vec3(0.0, 4500.0, 0.0),
            ),
        )
        assertEquals(24_750_000.0, m.rawValue, 1e-6)
        assertEquals("24.75m²", m.label())
        assertEquals(20_000.0, m.perimeter, 1e-6)
        assertTrue(m.anchor.toVec2().isCloseTo(Vec2(2750.0, 2250.0), 1e-6))
        assertEquals(
            "247500cm²",
            m.label(MeasurementDisplay(areaUnit = AreaUnit.SQUARE_CENTIMETRE, areaDecimals = 0)),
        )
    }

    @Test
    fun `degenerate measurements are rejected at construction`() {
        assertFailsWithIllegalArgument { Measurement.Area("x", listOf(Vec3.ZERO, Vec3.X)) }
        assertFailsWithIllegalArgument { Measurement.Polyline("x", listOf(Vec3.ZERO)) }
    }

    private fun assertFailsWithIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}

class MeasurementEngineTest {
    private var n = 0
    private fun engine() = MeasurementEngine { "id${++n}" }

    @Test
    fun `distance completes on the second pick`() {
        val e = engine()
        e.mode = MeasurementKind.DISTANCE
        assertEquals(2, e.remainingPicks)
        assertNull(e.addPick(Vec3.ZERO))
        assertEquals(1, e.remainingPicks)
        val m = e.addPick(Vec3(1000.0, 0.0, 0.0))
        assertIs<Measurement.Distance>(m)
        assertEquals(1000.0, m.rawValue, 1e-9)
        // State is reset, ready for the next measurement.
        assertEquals(emptyList(), e.pendingPoints)
    }

    @Test
    fun `angle completes on the third pick`() {
        val e = engine()
        e.mode = MeasurementKind.ANGLE
        assertNull(e.addPick(Vec3(1000.0, 0.0, 0.0)))
        assertNull(e.addPick(Vec3.ZERO))
        val m = e.addPick(Vec3(0.0, 1000.0, 0.0))
        assertIs<Measurement.Angle>(m)
        assertEquals(PI / 2, m.rawValue, 1e-12)
    }

    @Test
    fun `area stays open until finish is called`() {
        val e = engine()
        e.mode = MeasurementKind.AREA
        assertNull(e.addPick(Vec3.ZERO))
        assertNull(e.addPick(Vec3(5500.0, 0.0, 0.0)))
        assertNull(e.addPick(Vec3(5500.0, 4500.0, 0.0)))
        assertNull(e.addPick(Vec3(0.0, 4500.0, 0.0)))
        val m = e.finish()
        assertIs<Measurement.Area>(m)
        assertEquals(24_750_000.0, m.rawValue, 1e-6)
    }

    @Test
    fun `finishing an area with too few picks yields nothing`() {
        val e = engine()
        e.mode = MeasurementKind.AREA
        e.addPick(Vec3.ZERO)
        e.addPick(Vec3.X)
        assertNull(e.finish())
    }

    @Test
    fun `undo removes the last pick`() {
        val e = engine()
        e.mode = MeasurementKind.POLYLINE
        e.addPick(Vec3.ZERO)
        e.addPick(Vec3(1000.0, 0.0, 0.0))
        assertTrue(e.undoPick())
        assertEquals(1, e.pendingPoints.size)
        assertTrue(e.undoPick())
        assertFalse(e.undoPick())
    }

    @Test
    fun `switching mode discards the half finished measurement`() {
        val e = engine()
        e.mode = MeasurementKind.AREA
        e.addPick(Vec3.ZERO)
        e.addPick(Vec3.X)
        e.mode = MeasurementKind.DISTANCE
        assertEquals(emptyList(), e.pendingPoints)
    }

    @Test
    fun `cancel clears pending picks`() {
        val e = engine()
        e.addPick(Vec3.ZERO)
        e.cancel()
        assertEquals(emptyList(), e.pendingPoints)
    }
}

class SnapTest {
    private val wall = Segment2(Vec2(0.0, 0.0), Vec2(5500.0, 0.0))

    @Test
    fun `endpoints win over everything nearby`() {
        val r = snap(Vec2(40.0, 30.0), listOf(wall), tolerance = 200.0)
        assertEquals(SnapKind.ENDPOINT, r.kind)
        assertTrue(r.point.toVec2().isCloseTo(Vec2.ZERO))
    }

    @Test
    fun `midpoint of a wall is snappable`() {
        val r = snap(Vec2(2750.0, 50.0), listOf(wall), tolerance = 200.0)
        assertEquals(SnapKind.MIDPOINT, r.kind)
        assertTrue(r.point.toVec2().isCloseTo(Vec2(2750.0, 0.0)))
    }

    @Test
    fun `a pick along the wall falls back to perpendicular`() {
        val r = snap(Vec2(1000.0, 50.0), listOf(wall), tolerance = 200.0)
        assertEquals(SnapKind.PERPENDICULAR, r.kind)
        assertTrue(r.point.toVec2().isCloseTo(Vec2(1000.0, 0.0)))
    }

    @Test
    fun `grid snapping applies when nothing else is in range`() {
        val r = snap(Vec2(1040.0, 980.0), listOf(wall), tolerance = 200.0, gridSpacing = 1000.0)
        assertEquals(SnapKind.GRID, r.kind)
        assertTrue(r.point.toVec2().isCloseTo(Vec2(1000.0, 1000.0)))
    }

    @Test
    fun `a pick in open space returns the original point unsnapped`() {
        val p = Vec2(12_345.0, 9_876.0)
        val r = snap(p, listOf(wall), tolerance = 200.0)
        assertEquals(SnapKind.NONE, r.kind)
        assertTrue(r.point.toVec2().isCloseTo(p))
        assertNotNull(r)
    }
}
