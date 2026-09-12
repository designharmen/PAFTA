package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Snap candidates: what a tap on the drawing is allowed to lock onto. */
class DxfSnappingTest {

    private fun line(layer: String, x1: Double, y1: Double, x2: Double, y2: Double) =
        DxfEntity.Line(layer, Vec3(x1, y1, 0.0), Vec3(x2, y2, 0.0))

    @Test
    fun `a line yields one segment, a closed polyline yields one per side`() {
        val drawing = DxfDrawing(
            entities = listOf(
                line("DUVAR", 0.0, 0.0, 1000.0, 0.0),
                DxfEntity.Polyline(
                    layer = "DUVAR",
                    vertices = listOf(
                        Vec2(0.0, 0.0), Vec2(100.0, 0.0),
                        Vec2(100.0, 100.0), Vec2(0.0, 100.0),
                    ),
                    closed = true,
                ),
            ),
        )

        // 1 for the line, 4 for the closed square (the closing side included).
        assertEquals(5, drawing.snapSegments().size)
    }

    @Test
    fun `text and unexpanded block references offer nothing to snap to`() {
        val drawing = DxfDrawing(
            entities = listOf(
                DxfEntity.Text("METIN", Vec3(0.0, 0.0, 0.0), 100.0, "SALON"),
                DxfEntity.Insert("KAPI", "YOK", Vec3(0.0, 0.0, 0.0)),
                line("DUVAR", 0.0, 0.0, 10.0, 0.0),
            ),
        )

        // Only the wall: a label has no edge, and a marker is not geometry.
        assertEquals(1, drawing.snapSegments().size)
    }

    @Test
    fun `hidden layers can be excluded, so a snap never lands on what is not shown`() {
        val drawing = DxfDrawing(
            entities = listOf(
                line("DUVAR", 0.0, 0.0, 10.0, 0.0),
                line("GIZLI", 0.0, 5.0, 10.0, 5.0),
            ),
        )

        assertEquals(2, drawing.snapSegments().size)
        assertEquals(1, drawing.snapSegments(visibleLayers = setOf("DUVAR")).size)
    }

    @Test
    fun `the segment count is capped, so a huge plan cannot stall a tap`() {
        val many = (0 until 5_000).map { line("DUVAR", it.toDouble(), 0.0, it + 1.0, 0.0) }
        val drawing = DxfDrawing(entities = many)

        assertEquals(5_000, drawing.snapSegments().size)
        assertEquals(100, drawing.snapSegments(limit = 100).size)
    }

    @Test
    fun `a zero-length segment is not offered`() {
        val drawing = DxfDrawing(entities = listOf(line("DUVAR", 7.0, 7.0, 7.0, 7.0)))
        assertTrue(drawing.snapSegments().isEmpty())
    }

    @Test
    fun `only segments near the tap are considered`() {
        val drawing = DxfDrawing(
            entities = listOf(
                line("DUVAR", 0.0, 0.0, 100.0, 0.0),
                line("DUVAR", 50_000.0, 0.0, 50_100.0, 0.0),
            ),
        )
        val all = drawing.snapSegments()

        assertEquals(2, all.size)
        assertEquals(1, all.near(Vec2(10.0, 10.0), radius = 500.0).size)
        assertEquals(2, all.near(Vec2(10.0, 10.0), radius = 100_000.0).size)
    }

    @Test
    fun `an arc is approximated coarsely enough to stay pickable`() {
        val drawing = DxfDrawing(
            entities = listOf(
                DxfEntity.Arc("DUVAR", Vec3(0.0, 0.0, 0.0), 900.0, 0.0, 90.0),
            ),
        )
        val segments = drawing.snapSegments(arcSegments = 12)

        // Twelve steps along the arc means twelve points and eleven chords.
        assertTrue(segments.size in 8..16, "arc produced ${segments.size} segments")
    }
}
