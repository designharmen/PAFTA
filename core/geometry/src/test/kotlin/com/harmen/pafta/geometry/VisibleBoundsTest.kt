package com.harmen.pafta.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What part of the drawing a screen is showing.
 *
 * Worth pinning because the sign is easy to get wrong: a screen counts down
 * from the top and a drawing counts up from the bottom, so the top of the
 * screen is the *larger* model y. Getting it backwards would cull exactly the
 * entities that are on screen — a blank canvas with the drawing loaded.
 */
class VisibleBoundsTest {

    private val v = Viewport2D(scale = 2.0, panX = 10.0, panY = 10.0)

    @Test
    fun `gorunen alan ekranin kostekleriyle uyusur`() {
        val window = v.visibleBounds(100.0, 50.0)
        assertEquals(-5.0, window.min.x, 1e-9)
        assertEquals(45.0, window.max.x, 1e-9)
        assertEquals(-20.0, window.min.y, 1e-9)
        assertEquals(5.0, window.max.y, 1e-9)
    }

    @Test
    fun `ekranin ortasindaki nokta gorunen alanin icinde`() {
        val window = v.visibleBounds(100.0, 50.0)
        val centre = v.toModel(Vec2(50.0, 25.0))
        assertTrue(centre.x in window.min.x..window.max.x, "x dışarıda: ${centre.x}")
        assertTrue(centre.y in window.min.y..window.max.y, "y dışarıda: ${centre.y}")
    }

    @Test
    fun `koseler de icinde`() {
        val window = v.visibleBounds(100.0, 50.0)
        for (corner in listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 50.0), Vec2(100.0, 50.0))) {
            val p = v.toModel(corner)
            assertTrue(p.x in window.min.x..window.max.x && p.y in window.min.y..window.max.y, "$corner")
        }
    }

    @Test
    fun `tek koordinat donusumu toScreen ile ayni`() {
        val p = Vec2(123.0, -45.0)
        assertEquals(v.toScreen(p).x, v.screenX(p.x), 1e-12)
        assertEquals(v.toScreen(p).y, v.screenY(p.y), 1e-12)
    }

    @Test
    fun `olcekli bir gorunum bos alan vermez`() {
        // The type itself rules out a zero scale, so a view always shows
        // something; the culling never has an empty window to reason about.
        assertTrue(!Viewport2D(scale = 1e-9).visibleBounds(100.0, 50.0).isEmpty)
        assertTrue(!Viewport2D(scale = 1e9).visibleBounds(100.0, 50.0).isEmpty)
    }
}
