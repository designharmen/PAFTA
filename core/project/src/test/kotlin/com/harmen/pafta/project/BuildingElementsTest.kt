package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase B2: the building elements that are not walls. */
class BuildingElementsTest {

    // --- column ---------------------------------------------------------------

    @Test
    fun `a column is solid where the plan cuts through it`() {
        val column = DrawnShape.Column("k1", Vec3(1000.0, 1000.0, 0.0))
        assertTrue(column.isBand(), "a column is filled in, not outlined")

        val band = listOf(column).wallBands().single()
        assertEquals(4, band.corners.size)
        assertEquals(DrawnShape.LAYER_COLUMN, band.layer)
    }

    @Test
    fun `a square column measures what it was told to measure`() {
        val column = DrawnShape.Column("k1", Vec3(0.0, 0.0, 0.0), widthMm = 400.0, depthMm = 600.0)
        val corners = column.outline()

        assertEquals(-200.0, corners.minOf { it.x }, 1e-6)
        assertEquals(200.0, corners.maxOf { it.x }, 1e-6)
        assertEquals(-300.0, corners.minOf { it.y }, 1e-6)
        assertEquals(300.0, corners.maxOf { it.y }, 1e-6)
    }

    @Test
    fun `a turned column turns about its own middle`() {
        val column = DrawnShape.Column(
            "k1",
            Vec3(0.0, 0.0, 0.0),
            widthMm = 400.0,
            depthMm = 200.0,
            rotationDegrees = 90.0,
        )
        val corners = column.outline()
        // Turned a quarter, the 400 is now up and down.
        assertEquals(400.0, corners.maxOf { it.y } - corners.minOf { it.y }, 1e-6)
        assertEquals(200.0, corners.maxOf { it.x } - corners.minOf { it.x }, 1e-6)
    }

    @Test
    fun `a round column is filled as a circle, and exported as one`() {
        val column = DrawnShape.Column("k1", Vec3(0.0, 0.0, 0.0), widthMm = 500.0, round = true)

        // Every corner of the filled shape is on the circle.
        val band = listOf(column).wallBands().single()
        assertTrue(band.corners.size > 8, "a round column drawn as a square is a square")
        for (corner in band.corners) {
            assertEquals(250.0, corner.distanceTo(Vec2.ZERO), 1.0)
        }

        // On the way out to DXF it is one CIRCLE record, not thirty-six lines.
        assertEquals(1, column.toEntities().size)
    }

    @Test
    fun `a round column is one measurement across, a rectangular one is two`() {
        val round = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 500.0, round = true)
        assertEquals(
            listOf(ShapeDimension.DIAMETER, ShapeDimension.HEIGHT),
            round.dimensions().keys.toList(),
        )

        val square = DrawnShape.Column("k2", Vec3.ZERO)
        assertEquals(
            listOf(ShapeDimension.WIDTH, ShapeDimension.DEPTH, ShapeDimension.HEIGHT),
            square.dimensions().keys.toList(),
        )
    }

    @Test
    fun `typing a diameter into a round column keeps it round`() {
        val round = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 300.0, depthMm = 300.0, round = true)
        val bigger = assertIs<DrawnShape.Column>(
            round.withDimension(ShapeDimension.DIAMETER, 500.0),
        )
        assertEquals(500.0, bigger.widthMm, 1e-6)
        assertEquals(500.0, bigger.depthMm, 1e-6)
        assertTrue(bigger.round)
    }

    @Test
    fun `a column is picked by touching its body, not by finding its edge`() {
        val plan = listOf(DrawnShape.Column("k1", Vec3(1000.0, 1000.0, 0.0), widthMm = 400.0, depthMm = 400.0))

        // Dead centre, which is nowhere near any edge.
        assertNotNull(plan.pick(Vec2(1000.0, 1000.0), toleranceMm = 1.0))
        assertNull(plan.pick(Vec2(1400.0, 1400.0), toleranceMm = 1.0))
    }

    // --- beam -----------------------------------------------------------------

    @Test
    fun `a beam is drawn as an outline, because it is over your head`() {
        val beam = DrawnShape.Beam("b1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))
        assertTrue(!beam.isBand(), "a filled beam would read as a wall")

        val entity = beam.toEntities().single()
        assertIs<com.harmen.pafta.dxf.DxfEntity.Polyline>(entity)
    }

    @Test
    fun `a beam is as wide as it was told, either side of its line`() {
        val beam = DrawnShape.Beam(
            "b1",
            Vec3(0.0, 0.0, 0.0),
            Vec3(4000.0, 0.0, 0.0),
            widthMm = 300.0,
        )
        val corners = beam.outline()
        assertEquals(-150.0, corners.minOf { it.y }, 1e-6)
        assertEquals(150.0, corners.maxOf { it.y }, 1e-6)
    }

    @Test
    fun `a beam has a length, a width and a depth to type into`() {
        val beam = DrawnShape.Beam("b1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))
        assertEquals(
            listOf(ShapeDimension.LENGTH, ShapeDimension.WIDTH, ShapeDimension.DEPTH),
            beam.dimensions().keys.toList(),
        )
        assertEquals(4000.0, beam.lengthMm)

        val longer = assertIs<DrawnShape.Beam>(beam.withDimension(ShapeDimension.LENGTH, 5000.0))
        assertEquals(0.0, longer.a.x, 1e-6)
        assertEquals(5000.0, longer.b.x, 1e-6)
    }

    @Test
    fun `a wall can be run up to a beam, because a beam offers its line`() {
        val beam = DrawnShape.Beam("b1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0))
        assertEquals(1, beam.snapSegments().size)
    }

    // --- slab -----------------------------------------------------------------

    /** Four walls round a 4m x 3m room, with a slab tapped inside it. */
    private fun room(): List<DrawnShape> = listOf(
        DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    @Test
    fun `a slab takes its outline from the walls around where it was tapped`() {
        val plan = room() + DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))
        val slab = plan.slabPlans().single()

        assertTrue(!slab.isOpen)
        // To the middle of the walls: the slab is poured under them, so it is
        // the full 4m x 3m rather than the room's 3.8 x 2.8.
        assertEquals(4000.0 * 3000.0, slab.areaMm2, 1.0)
    }

    @Test
    fun `a slab and the room above it are measured differently, and both are right`() {
        val plan = room() +
            DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0)) +
            DrawnShape.Zone("z1", "Salon", Vec3(2000.0, 1500.0, 0.0))

        val slab = plan.slabPlans().single()
        val roomPlan = plan.zonePlans().single()

        // The structural area and the usable area.
        assertEquals(4000.0 * 3000.0, slab.areaMm2, 1.0)
        assertEquals(3800.0 * 2800.0, roomPlan.areaMm2, 1.0)
        assertTrue(slab.areaMm2 > roomPlan.areaMm2)
    }

    @Test
    fun `a slab tapped where the walls do not close reports itself open`() {
        val threeSides = room().filterNot { it.id == "w" }
        val plan = threeSides + DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))
        assertTrue(plan.slabPlans().single().isOpen)
    }

    @Test
    fun `a slab follows the walls when one of them is moved`() {
        val plan = room() + DrawnShape.Slab("d1", Vec3(1000.0, 1500.0, 0.0))
        assertEquals(4000.0 * 3000.0, plan.slabPlans().single().areaMm2, 1.0)

        // Pull the east wall in by a metre. The slab is not redrawn: it never
        // held an outline of its own, so it simply measures less.
        val narrower = plan.map {
            when (it.id) {
                "n" -> DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(3000.0, 3000.0, 0.0), thicknessMm = 200.0)
                "e" -> DrawnShape.Wall("e", Vec3(3000.0, 3000.0, 0.0), Vec3(3000.0, 0.0, 0.0), thicknessMm = 200.0)
                "s" -> DrawnShape.Wall("s", Vec3(3000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0)
                else -> it
            }
        }
        assertEquals(3000.0 * 3000.0, narrower.slabPlans().single().areaMm2, 1.0)
    }

    @Test
    fun `a flat roof is a slab with nothing on top, not a different thing`() {
        val roof = DrawnShape.Slab("c1", Vec3.ZERO, kind = SlabKind.ROOF, levelMm = 2800.0)
        assertEquals(SlabKind.ROOF, roof.kind)
        assertEquals(2800.0, roof.levelMm, 1e-6)
        // It measures the same way and carries the same numbers.
        assertEquals(listOf(ShapeDimension.THICKNESS), roof.dimensions().keys.toList())
    }

    @Test
    fun `a slab's thickness is typed in, and nothing else about it is`() {
        val slab = DrawnShape.Slab("d1", Vec3.ZERO)
        val thicker = assertIs<DrawnShape.Slab>(slab.withDimension(ShapeDimension.THICKNESS, 250.0))
        assertEquals(250.0, thicker.thicknessMm, 1e-6)
        assertEquals(slab, slab.withDimension(ShapeDimension.LENGTH, 5000.0))
    }

    @Test
    fun `the finish catalogue has the twenty the specification asked for, in four families`() {
        assertEquals(20, FloorFinish.entries.size)
        assertEquals(4, FinishFamily.entries.size)
        for (family in FinishFamily.entries) {
            assertTrue(
                FloorFinish.entries.any { it.family == family },
                "$family has no finishes in it",
            )
        }
        // Every code is ASCII, because they travel into exported DXF names.
        for (finish in FloorFinish.entries) {
            assertTrue(
                finish.code.all { it.code < 128 },
                "${finish.name} has a code that will not survive export: ${finish.code}",
            )
        }
    }

    // --- they all survive being saved and reopened ---------------------------

    private val emptyDxf = listOf(
        "0", "SECTION", "2", "ENTITIES",
        "0", "LINE", "8", "DUVAR", "10", "0.0", "20", "0.0", "11", "1000.0", "21", "0.0",
        "0", "ENDSEC", "0", "EOF",
    ).joinToString("\n", postfix = "\n").toByteArray()

    @Test
    fun `all three go into a project and come back out unchanged`() {
        val root = java.nio.file.Files.createTempDirectory("pafta-b2").toFile()
        val store = ProjectStore(root)
        val entry = assertNotNull(store.import("plan.dxf", emptyDxf).valueOrNull())
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        val shapes = listOf<DrawnShape>(
            DrawnShape.Column(
                "k1",
                Vec3(1000.0, 1000.0, 0.0),
                widthMm = 400.0,
                depthMm = 600.0,
                round = false,
                rotationDegrees = 30.0,
            ),
            DrawnShape.Beam("b1", Vec3(0.0, 0.0, 0.0), Vec3(4000.0, 0.0, 0.0), widthMm = 300.0),
            DrawnShape.Slab(
                "d1",
                Vec3(2000.0, 1500.0, 0.0),
                thicknessMm = 250.0,
                levelMm = 2800.0,
                kind = SlabKind.ROOF,
                finish = FloorFinish.PORCELAIN,
            ),
        )

        assertIs<StoreResult.Success<PaftaProject>>(
            store.save(opened.copy(shapes = shapes), entry.file),
        )
        val reopened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        assertEquals(shapes, reopened.shapes)
        root.deleteRecursively()
    }
}

/** Tapping on a floor that has both a room and a slab on it. */
class SlabAndRoomPickTest {

    private fun room(): List<DrawnShape> = listOf(
        DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    @Test
    fun `the one put down most recently is the one a tap means`() {
        val slabLast = room() +
            DrawnShape.Zone("z1", "Salon", Vec3(2000.0, 1500.0, 0.0)) +
            DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))
        assertIs<DrawnShape.Slab>(slabLast.pickResolved(Vec2(2000.0, 1500.0), toleranceMm = 10.0))

        val roomLast = room() +
            DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0)) +
            DrawnShape.Zone("z1", "Salon", Vec3(2000.0, 1500.0, 0.0))
        assertIs<DrawnShape.Zone>(roomLast.pickResolved(Vec2(2000.0, 1500.0), toleranceMm = 10.0))
    }

    @Test
    fun `a wall still wins over the floor under it`() {
        val plan = room() + DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))
        // On the south wall's centre line, which the slab also covers.
        assertIs<DrawnShape.Wall>(plan.pickResolved(Vec2(2000.0, 0.0), toleranceMm = 10.0))
    }

    @Test
    fun `a column standing on the slab is picked before the slab`() {
        val plan = room() +
            DrawnShape.Slab("d1", Vec3(500.0, 500.0, 0.0)) +
            DrawnShape.Column("k1", Vec3(2000.0, 1500.0, 0.0), widthMm = 400.0, depthMm = 400.0)
        assertIs<DrawnShape.Column>(plan.pickResolved(Vec2(2000.0, 1500.0), toleranceMm = 10.0))
    }
}

/** Where the two labels on one floor are written. */
class SlabLabelPlacementTest {

    private fun room(): List<DrawnShape> = listOf(
        DrawnShape.Wall("n", Vec3(0.0, 3000.0, 0.0), Vec3(4000.0, 3000.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("e", Vec3(4000.0, 3000.0, 0.0), Vec3(4000.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("s", Vec3(4000.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), thicknessMm = 200.0),
        DrawnShape.Wall("w", Vec3(0.0, 0.0, 0.0), Vec3(0.0, 3000.0, 0.0), thicknessMm = 200.0),
    )

    @Test
    fun `the slab writes itself below the room, so neither label is buried`() {
        val plan = room() +
            DrawnShape.Zone("z1", "Salon", Vec3(2000.0, 1500.0, 0.0)) +
            DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))

        val roomAnchor = plan.zonePlans().single().anchor
        val slabAnchor = plan.slabPlans().single().anchor

        assertEquals(roomAnchor.x, slabAnchor.x, 1.0)
        assertTrue(slabAnchor.y < roomAnchor.y, "the two labels are written on each other")
        // A quarter of a 3m room: 750mm apart, which stays apart at any zoom.
        assertEquals(750.0, roomAnchor.y - slabAnchor.y, 1.0)
    }

    @Test
    fun `an open slab still has somewhere to say so`() {
        val threeSides = room().filterNot { it.id == "w" }
        val plan = threeSides + DrawnShape.Slab("d1", Vec3(2000.0, 1500.0, 0.0))
        val anchor = plan.slabPlans().single().anchor
        assertEquals(2000.0, anchor.x, 1e-6)
        assertEquals(1500.0, anchor.y, 1e-6)
    }
}
