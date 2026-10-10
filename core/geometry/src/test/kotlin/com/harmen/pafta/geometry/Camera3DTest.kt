package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Camera3DTest {

    /** Standing due west of the origin, level with it, 10m away. */
    private val level = Camera3D(
        target = Vec3.ZERO,
        distanceMm = 10_000.0,
        yawDegrees = 0.0,
        pitchDegrees = 0.0,
    )

    @Test
    fun `the eye is where the three numbers put it`() {
        // Yaw 0 and no pitch: the camera is 10m along -X, looking east.
        assertTrue(level.eye.isCloseTo(Vec3(-10_000.0, 0.0, 0.0), 1e-6))
        assertTrue(level.forward.isCloseTo(Vec3(1.0, 0.0, 0.0), 1e-9))
    }

    @Test
    fun `turning a quarter round puts the eye on the other axis`() {
        val turned = level.orbitedBy(90.0, 0.0)
        assertTrue(turned.eye.isCloseTo(Vec3(0.0, -10_000.0, 0.0), 1e-6))
    }

    @Test
    fun `climbing lifts the eye and keeps the distance`() {
        val above = level.orbitedBy(0.0, 30.0)
        assertTrue(above.eye.z > 0.0)
        assertEquals(10_000.0, above.eye.distanceTo(Vec3.ZERO), 1e-6)
    }

    @Test
    fun `the view never tips over the top`() {
        assertEquals(85.0, level.orbitedBy(0.0, 400.0).pitchDegrees, 1e-9)
        assertEquals(-85.0, level.orbitedBy(0.0, -400.0).pitchDegrees, 1e-9)
    }

    @Test
    fun `what is looked at lands in the middle of the screen`() {
        val middle = assertNotNull(level.project(Vec3.ZERO, 800.0, 600.0))
        assertEquals(400.0, middle.screen.x, 1e-6)
        assertEquals(300.0, middle.screen.y, 1e-6)
        assertEquals(10_000.0, middle.depthMm, 1e-6)
    }

    @Test
    fun `up in the model is up the screen, which is down in screen numbers`() {
        val above = assertNotNull(level.project(Vec3(0.0, 0.0, 1000.0), 800.0, 600.0))
        assertTrue(above.screen.y < 300.0, "a point above the target drew below the middle")
        assertEquals(400.0, above.screen.x, 1e-6)
    }

    @Test
    fun `something behind the camera has no place on the screen`() {
        // 20m further west than the eye, which is itself 10m west.
        assertNull(level.project(Vec3(-30_000.0, 0.0, 0.0), 800.0, 600.0))
    }

    @Test
    fun `the further away two equal things are, the smaller the far one is`() {
        val near = assertNotNull(level.project(Vec3(0.0, 1000.0, 0.0), 800.0, 600.0))
        val far = assertNotNull(level.project(Vec3(10_000.0, 1000.0, 0.0), 800.0, 600.0))

        val nearOffset = abs(near.screen.x - 400.0)
        val farOffset = abs(far.screen.x - 400.0)
        assertTrue(farOffset < nearOffset, "perspective is not making the far one smaller")
        assertTrue(far.depthMm > near.depthMm)
    }

    @Test
    fun `zooming in halves the distance and never passes through the target`() {
        assertEquals(5000.0, level.zoomedBy(2.0).distanceMm, 1e-6)
        assertEquals(20_000.0, level.zoomedBy(0.5).distanceMm, 1e-6)
        // Clamped rather than allowed to reach zero, where the view inverts.
        assertTrue(level.zoomedBy(1e9).distanceMm >= 100.0)
    }

    @Test
    fun `panning moves what is looked at across the screen, not across the model`() {
        val moved = level.pannedBy(1000.0, 0.0)
        // Standing west of the origin looking east, with Z up, your right hand
        // points south — so "a metre to the right" is a metre south. Which way
        // that is in the model is exactly what the camera is for working out.
        assertTrue(moved.target.isCloseTo(Vec3(0.0, -1000.0, 0.0), 1e-6))
        // And it is still the middle of the screen afterwards.
        val middle = assertNotNull(moved.project(moved.target, 800.0, 600.0))
        assertEquals(400.0, middle.screen.x, 1e-6)
        assertEquals(300.0, middle.screen.y, 1e-6)
    }

    @Test
    fun `screen up is model up, so a wall is drawn standing rather than lying down`() {
        assertTrue(level.up.isCloseTo(Vec3(0.0, 0.0, 1.0), 1e-9))
        assertTrue(level.right.isCloseTo(Vec3(0.0, -1.0, 0.0), 1e-9))
    }

    @Test
    fun `looking straight down still has a side to it`() {
        val overhead = Camera3D(pitchDegrees = 85.0).orbitedBy(0.0, 90.0)
        assertEquals(85.0, overhead.pitchDegrees, 1e-9)
        assertTrue(overhead.right.length > 0.9)
        assertTrue(overhead.up.length > 0.9)
    }

    @Test
    fun `framing a building puts the whole of it on the screen`() {
        // A 20m x 15m house, 6m to the ridge.
        val house = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(20_000.0, 15_000.0, 6000.0))
        val camera = Camera3D.framing(house)

        val corners = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(20_000.0, 0.0, 0.0),
            Vec3(20_000.0, 15_000.0, 0.0),
            Vec3(0.0, 15_000.0, 0.0),
            Vec3(0.0, 0.0, 6000.0),
            Vec3(20_000.0, 15_000.0, 6000.0),
        )
        for (corner in corners) {
            val point = assertNotNull(camera.project(corner, 1000.0, 800.0), "$corner is behind us")
            assertTrue(point.screen.x in 0.0..1000.0, "$corner is off the side at ${point.screen}")
            assertTrue(point.screen.y in 0.0..800.0, "$corner is off the top at ${point.screen}")
        }
    }

    @Test
    fun `an empty building still gives a camera rather than an exception`() {
        assertEquals(Vec3.ZERO, Camera3D.framing(Aabb.EMPTY).target)
    }

    @Test
    fun `a camera inside its own target, or with no angle of view, is refused`() {
        assertFailsWith<IllegalArgumentException> { Camera3D(distanceMm = 0.0) }
        assertFailsWith<IllegalArgumentException> { Camera3D(fovDegrees = 0.0) }
        assertFailsWith<IllegalArgumentException> { Camera3D(fovDegrees = 180.0) }
    }
}
