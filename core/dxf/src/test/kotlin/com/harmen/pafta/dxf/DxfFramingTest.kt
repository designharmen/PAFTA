package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the view starts when a drawing opens.
 *
 * The two cases pull in opposite directions and both are ordinary: a plan with
 * one mark left a long way out must not open on a blank screen, and a plan that
 * genuinely covers a lot of ground must not be cropped.
 */
class DxfFramingTest {

    private fun line(x: Double, y: Double) = DxfEntity.Line(
        layer = "0",
        start = Vec3(x, y, 0.0),
        end = Vec3(x + 100.0, y + 100.0, 0.0),
    )

    /** A building: forty entities inside a ten-metre square. */
    private fun building(): List<DxfEntity> =
        (0 until 40).map { line((it % 8) * 1_000.0, (it / 8) * 1_000.0) }

    @Test
    fun `tek bir kacak isaret acilis gorunumunu bozmaz`() {
        val withStray = building() + line(2_000_000.0, -1_500_000.0)
        val opening = withStray.openingBounds()

        assertTrue(
            opening.max.x - opening.min.x < 50_000.0,
            "açılış görünümü kaçak işarete göre ayarlanmış: ${opening.max.x - opening.min.x}",
        )
        // The stray mark is still in the drawing, and still reachable.
        val whole = DxfDrawing(entities = withStray).bounds
        assertTrue(whole.max.x > 1_000_000.0)
    }

    @Test
    fun `gercekten genis bir cizim kirpilmaz`() {
        // A site plan: the same building, plus a boundary drawn right round a
        // field. Nothing here is stray, so nothing may be left out.
        val site = building() + (0 until 20).map { line(it * 40_000.0, it * 30_000.0) }
        val opening = site.openingBounds()
        val whole = DxfDrawing(entities = site).bounds
        assertEquals(whole.min.x, opening.min.x, 1e-9)
        assertEquals(whole.max.x, opening.max.x, 1e-9)
        assertEquals(whole.max.y, opening.max.y, 1e-9)
    }

    @Test
    fun `az sayida ogede hicbir sey kirpilmaz`() {
        // With a handful of entities there is no "most of the drawing" to
        // measure against, so the whole of it is the answer.
        val few = listOf(line(0.0, 0.0), line(5_000_000.0, 0.0))
        val opening = few.openingBounds()
        assertTrue(opening.max.x > 1_000_000.0)
    }

    @Test
    fun `bos cizim bos sinir verir`() {
        assertTrue(emptyList<DxfEntity>().openingBounds().isEmpty)
    }

    @Test
    fun `yogun bir kume binayi kacak gostermez`() {
        // The case that broke the first attempt: a hundred points in one
        // cupboard make the drawing's *typical* spread tiny, and the building
        // around them then looks like the outlier. Measuring from the edges
        // rather than the middle is what fixes it.
        val cupboard = (0 until 100).map {
            DxfEntity.Point(layer = "0", position = Vec3(5_000.0 + it, 5_000.0, 0.0))
        }
        val opening = (cupboard + building()).openingBounds()
        assertTrue(
            opening.min.x < 1_000.0 && opening.max.x > 7_000.0,
            "bina kırpılmış: ${opening.min.x} .. ${opening.max.x}",
        )
    }
}
