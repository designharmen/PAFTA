package com.harmen.pafta.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Viewport2DTest {

    // A 5500 x 4500 room on a 1000 x 800 surface.
    private val room = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(5500.0, 4500.0, 0.0))

    @Test
    fun `screen and model coordinates round trip`() {
        val v = Viewport2D(scale = 0.15, panX = 120.0, panY = 640.0)
        for (p in listOf(Vec2.ZERO, Vec2(5500.0, 4500.0), Vec2(-1200.5, 333.25))) {
            val back = v.toModel(v.toScreen(p))
            assertTrue(back.isCloseTo(p, 1e-6), "expected $p, got $back")
        }
    }

    @Test
    fun `the y axis is flipped because model space is y up`() {
        val v = Viewport2D(scale = 1.0, panX = 0.0, panY = 0.0)
        assertEquals(Vec2(10.0, -20.0), v.toScreen(Vec2(10.0, 20.0)))
    }

    @Test
    fun `a non positive scale is rejected`() {
        assertFailsWith<IllegalArgumentException> { Viewport2D(scale = 0.0) }
        assertFailsWith<IllegalArgumentException> { Viewport2D(scale = -2.0) }
        assertFailsWith<IllegalArgumentException> { Viewport2D(scale = Double.NaN) }
    }

    @Test
    fun `fit centres the drawing on the surface`() {
        val v = Viewport2D.fit(room, 1000.0, 800.0, paddingPixels = 50.0)
        val centre = v.toScreen(Vec2(2750.0, 2250.0))
        assertTrue(centre.isCloseTo(Vec2(500.0, 400.0), 1e-6), "centre landed at $centre")
    }

    @Test
    fun `fit keeps the whole drawing inside the padded surface`() {
        val v = Viewport2D.fit(room, 1000.0, 800.0, paddingPixels = 50.0)
        val corners = listOf(
            Vec2(0.0, 0.0),
            Vec2(5500.0, 0.0),
            Vec2(5500.0, 4500.0),
            Vec2(0.0, 4500.0),
        ).map { v.toScreen(it) }

        corners.forEach {
            assertTrue(it.x >= 50.0 - 1e-6 && it.x <= 950.0 + 1e-6, "x out of bounds: $it")
            assertTrue(it.y >= 50.0 - 1e-6 && it.y <= 750.0 + 1e-6, "y out of bounds: $it")
        }
    }

    @Test
    fun `fit uses the tighter axis so the aspect ratio is preserved`() {
        // Usable surface is 900 x 700. Width would allow 900/5500 = 0.1636 px per
        // unit, height only 700/4500 = 0.1556, so height binds and fit must take
        // the smaller of the two.
        val v = Viewport2D.fit(room, 1000.0, 800.0, paddingPixels = 50.0)
        assertEquals(700.0 / 4500.0, v.scale, 1e-12)
        assertTrue(v.scale < 900.0 / 5500.0)
    }

    @Test
    fun `fit on an empty drawing still produces a usable viewport`() {
        val v = Viewport2D.fit(Aabb.EMPTY, 1000.0, 800.0)
        assertEquals(1.0, v.scale, 1e-12)
        assertTrue(v.toScreen(Vec2.ZERO).isCloseTo(Vec2(500.0, 400.0), 1e-6))
    }

    @Test
    fun `fit on a single point does not divide by zero`() {
        val point = Aabb(Vec3(10.0, 10.0, 0.0), Vec3(10.0, 10.0, 0.0))
        val v = Viewport2D.fit(point, 1000.0, 800.0, fallbackScale = 2.0)
        assertEquals(2.0, v.scale, 1e-12)
        assertTrue(v.toScreen(Vec2(10.0, 10.0)).isCloseTo(Vec2(500.0, 400.0), 1e-6))
    }

    @Test
    fun `fit on a horizontal line fits the width`() {
        val line = Aabb(Vec3(0.0, 5.0, 0.0), Vec3(1000.0, 5.0, 0.0))
        val v = Viewport2D.fit(line, 1000.0, 800.0, paddingPixels = 50.0)
        assertEquals(900.0 / 1000.0, v.scale, 1e-12)
    }

    @Test
    fun `fit on a zero sized surface does not throw`() {
        val v = Viewport2D.fit(room, 0.0, 0.0)
        assertEquals(1.0, v.scale, 1e-12)
    }

    @Test
    fun `panning moves the drawing by the screen delta`() {
        val v = Viewport2D(scale = 2.0)
        val before = v.toScreen(Vec2(100.0, 100.0))
        val after = v.pannedBy(30.0, -15.0).toScreen(Vec2(100.0, 100.0))
        assertTrue((after - before).isCloseTo(Vec2(30.0, -15.0), 1e-9))
    }

    @Test
    fun `zooming holds the model point under the pivot still`() {
        val v = Viewport2D.fit(room, 1000.0, 800.0)
        val pivot = Vec2(300.0, 220.0)
        val modelUnderPivot = v.toModel(pivot)

        var zoomed = v
        repeat(5) { zoomed = zoomed.zoomedAbout(pivot, 1.35) }

        val stillThere = zoomed.toScreen(modelUnderPivot)
        assertTrue(stillThere.isCloseTo(pivot, 1e-6), "pivot drifted to $stillThere")
        assertTrue(zoomed.scale > v.scale)
    }

    @Test
    fun `zooming out holds the pivot too`() {
        val v = Viewport2D.fit(room, 1000.0, 800.0)
        val pivot = Vec2(812.0, 91.0)
        val model = v.toModel(pivot)
        val zoomed = v.zoomedAbout(pivot, 0.4)
        assertTrue(zoomed.toScreen(model).isCloseTo(pivot, 1e-6))
        assertTrue(zoomed.scale < v.scale)
    }

    @Test
    fun `zoom is clamped and a clamped zoom does not shift the view`() {
        val v = Viewport2D(scale = 1.0, panX = 7.0, panY = 9.0)
        val atMax = v.zoomedAbout(Vec2(10.0, 10.0), 1000.0, maxScale = 4.0)
        assertEquals(4.0, atMax.scale, 1e-12)

        // Already at the ceiling: a further zoom must be a no-op, pan included.
        assertEquals(atMax, atMax.zoomedAbout(Vec2(10.0, 10.0), 2.0, maxScale = 4.0))
        assertEquals(v, v.zoomedAbout(Vec2(10.0, 10.0), 0.0))
        assertEquals(v, v.zoomedAbout(Vec2(10.0, 10.0), Double.NaN))
    }

    @Test
    fun `lengths convert both ways`() {
        val v = Viewport2D(scale = 0.25)
        assertEquals(25.0, v.lengthToScreen(100.0), 1e-12)
        assertEquals(100.0, v.lengthToModel(25.0), 1e-12)
    }
}
