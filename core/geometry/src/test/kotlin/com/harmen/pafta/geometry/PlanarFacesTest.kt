package com.harmen.pafta.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanarFacesTest {

    private fun seg(ax: Double, ay: Double, bx: Double, by: Double) =
        Segment2(Vec2(ax, ay), Vec2(bx, by))

    /** Four walls round a 4m x 3m room, drawn the way a hand draws them. */
    private fun room() = listOf(
        seg(0.0, 0.0, 4000.0, 0.0),
        seg(4000.0, 0.0, 4000.0, 3000.0),
        seg(4000.0, 3000.0, 0.0, 3000.0),
        seg(0.0, 3000.0, 0.0, 0.0),
    )

    @Test
    fun `a tap inside four walls finds the room they enclose`() {
        val face = faceContaining(room(), Vec2(2000.0, 1500.0))

        assertNotNull(face)
        assertEquals(4, face.size)
        assertEquals(4000.0 * 3000.0, area(face.map { it.a }), 1.0)
        // Anticlockwise, which is what insetPolygon relies on to know which way
        // is in.
        assertTrue(signedArea(face.map { it.a }) > 0.0, "face ran the wrong way round")
    }

    @Test
    fun `a tap outside the walls encloses nothing`() {
        assertNull(faceContaining(room(), Vec2(-500.0, 1500.0)))
    }

    @Test
    fun `a room with a wall missing is not a room`() {
        val open = room().dropLast(1)
        assertNull(faceContaining(open, Vec2(2000.0, 1500.0)))
    }

    @Test
    fun `every edge is traced back to the wall it came from`() {
        val face = faceContaining(room(), Vec2(2000.0, 1500.0))
        assertNotNull(face)
        // Four walls, four edges, one from each — which is what lets the caller
        // look up how thick each one was.
        assertEquals(setOf(0, 1, 2, 3), face.map { it.source }.toSet())
    }

    @Test
    fun `a wall through the middle makes two rooms, and the tap picks its own`() {
        val divided = room() + seg(2000.0, 0.0, 2000.0, 3000.0)

        val left = faceContaining(divided, Vec2(1000.0, 1500.0))
        val right = faceContaining(divided, Vec2(3000.0, 1500.0))

        assertNotNull(left)
        assertNotNull(right)
        assertEquals(2000.0 * 3000.0, area(left.map { it.a }), 1.0)
        assertEquals(2000.0 * 3000.0, area(right.map { it.a }), 1.0)
    }

    @Test
    fun `a wall that stops in mid-air does not become a side of the room`() {
        // A stub reaching in from the left wall, ending in the middle of nothing.
        val withSpur = room() + seg(0.0, 1500.0, 900.0, 1500.0)

        val face = faceContaining(withSpur, Vec2(2000.0, 2500.0))
        assertNotNull(face)
        // The left wall is now two pieces, so five edges — but the stub, walked
        // down and back, is gone rather than counted twice.
        assertEquals(5, face.size)
        assertEquals(4000.0 * 3000.0, area(face.map { it.a }), 1.0)
    }

    @Test
    fun `walls that only touch in the middle still divide the space`() {
        // The divider stops against the two walls rather than joining at a corner:
        // its ends are on them, not at their ends. This is the T-junction that
        // an unsplit map walks straight past.
        val divided = room() + seg(2000.0, 3000.0, 2000.0, 0.0)

        val left = faceContaining(divided, Vec2(500.0, 1500.0))
        assertNotNull(left)
        assertEquals(2000.0 * 3000.0, area(left.map { it.a }), 1.0)
    }

    @Test
    fun `the room is measured to the wall faces, not their centre lines`() {
        val face = faceContaining(room(), Vec2(2000.0, 1500.0))
        assertNotNull(face)

        // 200mm walls all round: the floor is 100mm short on each of four sides.
        val net = insetPolygon(face.map { it.a }, List(face.size) { 100.0 })
        assertNotNull(net)
        assertEquals(3800.0 * 2800.0, area(net), 1.0)
    }

    @Test
    fun `a room smaller than the walls around it is refused rather than inverted`() {
        val tiny = listOf(
            seg(0.0, 0.0, 1000.0, 0.0),
            seg(1000.0, 0.0, 1000.0, 1000.0),
            seg(1000.0, 1000.0, 0.0, 1000.0),
            seg(0.0, 1000.0, 0.0, 0.0),
        )
        val face = faceContaining(tiny, Vec2(500.0, 500.0))
        assertNotNull(face)

        assertNull(insetPolygon(face.map { it.a }, List(face.size) { 800.0 }))
    }

    @Test
    fun `an L-shaped room keeps its corner`() {
        // A 4x3 room with a 2x1 bite taken out of the top-right corner.
        val shape = listOf(
            seg(0.0, 0.0, 4000.0, 0.0),
            seg(4000.0, 0.0, 4000.0, 2000.0),
            seg(4000.0, 2000.0, 2000.0, 2000.0),
            seg(2000.0, 2000.0, 2000.0, 3000.0),
            seg(2000.0, 3000.0, 0.0, 3000.0),
            seg(0.0, 3000.0, 0.0, 0.0),
        )

        val face = faceContaining(shape, Vec2(500.0, 2500.0))
        assertNotNull(face)
        assertEquals(6, face.size)
        assertEquals(4000.0 * 3000.0 - 2000.0 * 1000.0, area(face.map { it.a }), 1.0)
    }
}
