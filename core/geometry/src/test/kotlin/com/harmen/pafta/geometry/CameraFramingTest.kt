package com.harmen.pafta.geometry

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Framing a model that came out of somebody else's file.
 *
 * A file PAFTA did not write can hold anything at all, including coordinates
 * that are not numbers. The camera is built with `require`, so a model with one
 * bad point in it took the whole app down rather than showing a model with one
 * bad point in it.
 */
class CameraFramingTest {

    @Test
    fun `a model with a point that is not a number still gives a camera`() {
        val broken = Aabb(
            Vec3(0.0, 0.0, 0.0),
            Vec3(Double.NaN, 1000.0, 1000.0),
        )
        val camera = Camera3D.framing(broken)
        assertTrue(camera.distanceMm.isFinite() && camera.distanceMm > 0.0)
        assertTrue(camera.target.x.isFinite())
    }

    @Test
    fun `a model that reaches to infinity still gives a camera`() {
        val huge = Aabb(
            Vec3(Double.NEGATIVE_INFINITY, 0.0, 0.0),
            Vec3(Double.POSITIVE_INFINITY, 1000.0, 1000.0),
        )
        val camera = Camera3D.framing(huge)
        assertTrue(camera.distanceMm.isFinite() && camera.distanceMm > 0.0)
    }

    @Test
    fun `a model of one point is framed rather than divided by nothing`() {
        val dot = Aabb(Vec3(500.0, 500.0, 500.0), Vec3(500.0, 500.0, 500.0))
        val camera = Camera3D.framing(dot)
        assertTrue(camera.distanceMm.isFinite() && camera.distanceMm > 0.0)
    }
}
