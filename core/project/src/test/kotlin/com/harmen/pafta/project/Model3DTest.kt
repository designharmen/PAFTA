package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The plan stood up into a building. */
class Model3DTest {

    /** One 4m wall, 200 thick, 2800 tall, running east. */
    private fun wall(): DrawnShape.Wall = DrawnShape.Wall(
        "w1",
        Vec3(0.0, 0.0, 0.0),
        Vec3(4000.0, 0.0, 0.0),
        thicknessMm = 200.0,
        heightMm = 2800.0,
    )

    @Test
    fun `a wall with nothing in it is a box - two sides, two ends and a top`() {
        val faces = listOf(wall()).toFaces()
        assertEquals(5, faces.size)
        assertEquals(1, faces.count { it.kind == FaceKind.WALL_TOP })
        assertEquals(4, faces.count { it.kind == FaceKind.WALL })
    }

    @Test
    fun `it stands the height it was told, and no higher`() {
        val box = listOf(wall()).toFaces().bounds()
        assertEquals(0.0, box.min.z, 1e-6)
        assertEquals(2800.0, box.max.z, 1e-6)
        assertEquals(4000.0, box.size.x, 1e-6)
        assertEquals(200.0, box.size.y, 1e-6)
    }

    @Test
    fun `a wall with no height stands nothing up`() {
        assertTrue(listOf(wall().copy(heightMm = 0.0)).toFaces().isEmpty())
    }

    // --- the openings are the interesting part -------------------------------

    @Test
    fun `a doorway leaves wall either side of it and a lintel over it`() {
        val door = DrawnShape.Opening(
            id = "d1",
            wallId = "w1",
            kind = OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
            heightMm = 2100.0,
            sillMm = 0.0,
        )
        val faces = listOf(wall(), door).toFaces()

        // Each side of the wall is now three pieces: before, lintel, after.
        // A door has no sill, so there is nothing under it.
        assertEquals(6 + 2, faces.count { it.kind == FaceKind.WALL })

        // Nothing fills the doorway: no wall face spans the door's height in
        // the middle of the wall.
        val inTheOpening = faces.filter { it.kind == FaceKind.WALL }.any { face ->
            val xs = face.points.map { it.x }
            val zs = face.points.map { it.z }
            xs.min() < 2000.0 && xs.max() > 2000.0 && zs.min() < 1000.0
        }
        assertTrue(!inTheOpening, "the doorway is filled in")
    }

    @Test
    fun `a window leaves wall under it as well as over it`() {
        val window = DrawnShape.Opening(
            id = "p1",
            wallId = "w1",
            kind = OpeningKind.WINDOW,
            alongMm = 2000.0,
            widthMm = 1200.0,
            heightMm = 1400.0,
            sillMm = 900.0,
        )
        val faces = listOf(wall(), window).toFaces()

        // Four pieces a side now: before, under the sill, over the head, after.
        assertEquals(8 + 2, faces.count { it.kind == FaceKind.WALL })

        // There is wall between the floor and the sill, in the middle.
        val underTheSill = faces.filter { it.kind == FaceKind.WALL }.any { face ->
            val xs = face.points.map { it.x }
            val zs = face.points.map { it.z }
            xs.min() > 1300.0 && xs.max() < 2700.0 && zs.max() <= 900.0 + 1e-6
        }
        assertTrue(underTheSill, "the window has no wall under it")
    }

    @Test
    fun `an opening is given its reveals, so a doorway has sides and a head`() {
        val door = DrawnShape.Opening(
            "d1",
            "w1",
            OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
            heightMm = 2100.0,
        )
        val reveals = listOf(wall(), door).toFaces().filter { it.kind == FaceKind.OPENING_REVEAL }

        // Two jambs and a head.
        assertEquals(3, reveals.size)
        assertTrue(reveals.all { it.id == "d1" })
        // All of them are inside the doorway's own height.
        assertTrue(reveals.flatMap { it.points }.all { it.z <= 2100.0 + 1e-6 })
    }

    @Test
    fun `a door as tall as the wall gets jambs but no head`() {
        val full = DrawnShape.Opening(
            "d1",
            "w1",
            OpeningKind.DOOR,
            alongMm = 2000.0,
            widthMm = 900.0,
            heightMm = 2800.0,
        )
        val faces = listOf(wall(), full).toFaces()
        assertEquals(2, faces.count { it.kind == FaceKind.OPENING_REVEAL })
        // Two pieces a side, and no lintel over a hole that reaches the top.
        assertEquals(4 + 2, faces.count { it.kind == FaceKind.WALL })
    }

    @Test
    fun `an opening wider than its wall does not reach outside it`() {
        val huge = DrawnShape.Opening(
            "d1",
            "w1",
            OpeningKind.DOOR,
            alongMm = 200.0,
            widthMm = 3000.0,
            heightMm = 2100.0,
        )
        val box = listOf(wall(), huge).toFaces().bounds()
        assertTrue(box.min.x >= -1e-6, "the opening pushed the wall back past its own start")
        assertTrue(box.max.x <= 4000.0 + 1e-6)
    }

    @Test
    fun `two doorways in one wall both get through it`() {
        val first = DrawnShape.Opening("d1", "w1", OpeningKind.DOOR, 900.0, 800.0, heightMm = 2100.0)
        val second = DrawnShape.Opening("d2", "w1", OpeningKind.DOOR, 3000.0, 800.0, heightMm = 2100.0)
        val faces = listOf(wall(), first, second).toFaces()

        assertEquals(6, faces.count { it.kind == FaceKind.OPENING_REVEAL })
        // Three solid pieces and two lintels a side.
        assertEquals(10 + 2, faces.count { it.kind == FaceKind.WALL })
    }

    // --- everything else ------------------------------------------------------

    @Test
    fun `a column stands up as a box of its own height`() {
        val column = DrawnShape.Column("k1", Vec3(1000.0, 1000.0, 0.0), heightMm = 3000.0)
        val faces = listOf(column).toFaces()

        assertEquals(4 + 2, faces.size)
        assertEquals(3000.0, faces.bounds().max.z, 1e-6)
        assertEquals(300.0, faces.bounds().size.x, 1e-6)
    }

    @Test
    fun `a round column stands up round`() {
        val column = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 400.0, round = true, heightMm = 2800.0)
        val faces = listOf(column).toFaces()
        assertTrue(faces.size > 10, "a round column stood up as a square")
        assertEquals(400.0, faces.bounds().size.x, 1.0)
    }

    @Test
    fun `a slab is a deck as thick as it says, with its top at its level`() {
        val room = listOf(
            DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0), thicknessMm = 150.0, levelMm = 0.0),
        )
        val slabFaces = room.toFaces().filter { it.kind == FaceKind.SLAB }

        assertTrue(slabFaces.isNotEmpty(), "the slab stood nothing up")
        val zs = slabFaces.flatMap { it.points }.map { it.z }
        assertEquals(0.0, zs.max(), 1e-6)
        assertEquals(-150.0, zs.min(), 1e-6)
    }

    @Test
    fun `a flat roof stands at its own level, above the walls`() {
        val room = listOf(
            DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
            DrawnShape.Slab(
                "c1",
                Vec3(2000.0, 1500.0, 0.0),
                thicknessMm = 200.0,
                levelMm = 3000.0,
                kind = SlabKind.ROOF,
            ),
        )
        val roof = room.toFaces().filter { it.kind == FaceKind.SLAB }
        assertEquals(3000.0, roof.flatMap { it.points }.maxOf { it.z }, 1e-6)
    }

    @Test
    fun `furniture stands up at a height the plan never had to know`() {
        val bed = DrawnShape.Block("m1", CatalogueBlock.BED_DOUBLE, Vec3(2000.0, 1500.0, 0.0))
        val faces = listOf(bed).toFaces()

        assertTrue(faces.all { it.kind == FaceKind.FURNITURE })
        assertEquals(500.0, faces.bounds().max.z, 1e-6)
        // A wardrobe is nearly four times the height of a bed, which is the
        // whole reason this is a table and not one number.
        val wardrobe = listOf(DrawnShape.Block("m2", CatalogueBlock.WARDROBE, Vec3.ZERO)).toFaces()
        assertEquals(2000.0, wardrobe.bounds().max.z, 1e-6)
    }

    @Test
    fun `every piece of furniture in the library has a height`() {
        for (piece in CatalogueBlock.entries) {
            val faces = listOf(DrawnShape.Block("m", piece, Vec3.ZERO)).toFaces()
            assertTrue(faces.isNotEmpty(), "${piece.name} stands nothing up")
            assertTrue(faces.bounds().max.z > 0.0, "${piece.name} has no height")
        }
    }

    @Test
    fun `a room with nothing drawn in it stands nothing up`() {
        assertTrue(emptyList<DrawnShape>().toFaces().isEmpty())
        // Lines and rectangles are drawing, not building: they have no height
        // and nothing to make one up from.
        val linework = listOf(
            DrawnShape.Line("l1", Vec3.ZERO, Vec3(1000.0, 0.0, 0.0)),
            DrawnShape.Rectangle("r1", Vec3.ZERO, Vec3(1000.0, 1000.0, 0.0)),
            DrawnShape.Circle("c1", Vec3.ZERO, 500.0),
        )
        assertTrue(linework.toFaces().isEmpty())
    }

    @Test
    fun `every face looks outwards, so nothing is lit from inside`() {
        val column = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 400.0, depthMm = 400.0, heightMm = 2000.0)
        val sides = listOf(column).toFaces().filter { it.kind == FaceKind.COLUMN }

        for (face in sides) {
            // The middle of the column is at the origin; a side face's normal
            // must point away from it.
            val outwards = face.centre - Vec3(0.0, 0.0, 1000.0)
            assertTrue(
                (face.normal dot outwards) > 0.0,
                "a side of the column faces inwards: ${face.normal}",
            )
        }
    }

    @Test
    fun `a shape drawn the other way round still faces outwards`() {
        // The same column footprint wound the other way: a plan is drawn
        // whichever way the hand went, and half of them would otherwise come
        // out inside-out.
        val clockwise = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 400.0, depthMm = 400.0, rotationDegrees = 180.0)
        val sides = listOf(clockwise).toFaces().filter { it.kind == FaceKind.COLUMN }
        for (face in sides) {
            val outwards = face.centre - Vec3(0.0, 0.0, face.centre.z)
            assertTrue((face.normal dot outwards) > 0.0, "inside-out at ${face.normal}")
        }
    }

    @Test
    fun `a face knows which layer and which shape it came from`() {
        val faces = listOf(wall()).toFaces()
        assertTrue(faces.all { it.id == "w1" })
        assertTrue(faces.all { it.layer == wall().layer })
    }
}
