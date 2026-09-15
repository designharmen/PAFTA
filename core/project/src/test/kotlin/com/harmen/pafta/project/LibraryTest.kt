package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase D: the things that are drawn the same way every time. */
class LibraryTest {

    // --- the catalogue itself -------------------------------------------------

    @Test
    fun `every piece in the library has a size and something to draw`() {
        for (block in CatalogueBlock.entries) {
            assertTrue(block.widthMm > 0.0, "${block.name} has no width")
            assertTrue(block.depthMm > 0.0, "${block.name} has no depth")
            assertTrue(block.outlines().isNotEmpty(), "${block.name} draws nothing")
            for (outline in block.outlines()) {
                assertTrue(outline.size >= 2, "${block.name} has an outline of one point")
            }
        }
    }

    @Test
    fun `every piece is drawn about its own middle, inside its own size`() {
        for (block in CatalogueBlock.entries) {
            val points = block.outlines().flatten()
            val width = points.maxOf { it.x } - points.minOf { it.x }
            val depth = points.maxOf { it.y } - points.minOf { it.y }

            assertEquals(block.widthMm, width, 1.0, "${block.name} is not as wide as it says")
            assertEquals(block.depthMm, depth, 1.0, "${block.name} is not as deep as it says")
            // Centred: the two extremes are the same distance from the origin.
            assertEquals(0.0, points.maxOf { it.x } + points.minOf { it.x }, 1.0)
            assertEquals(0.0, points.maxOf { it.y } + points.minOf { it.y }, 1.0)
        }
    }

    @Test
    fun `every part of the library has something in it`() {
        for (group in BlockGroup.entries) {
            assertTrue(
                CatalogueBlock.entries.any { it.group == group },
                "$group is an empty drawer",
            )
        }
    }

    // --- placing one ----------------------------------------------------------

    @Test
    fun `a placed piece stands where it was put, at the size the library gives it`() {
        val bed = DrawnShape.Block("m1", CatalogueBlock.BED_DOUBLE, Vec3(2000.0, 1500.0, 0.0))
        val points = bed.outlines().flatten()

        assertEquals(1600.0, points.maxOf { it.x } - points.minOf { it.x }, 1.0)
        assertEquals(2000.0, points.maxOf { it.y } - points.minOf { it.y }, 1.0)
        // Centred on where it was put.
        assertEquals(2000.0, (points.maxOf { it.x } + points.minOf { it.x }) / 2.0, 1.0)
        assertEquals(1500.0, (points.maxOf { it.y } + points.minOf { it.y }) / 2.0, 1.0)
    }

    @Test
    fun `a quarter turn puts the long side the other way`() {
        val bed = DrawnShape.Block(
            "m1",
            CatalogueBlock.BED_DOUBLE,
            Vec3.ZERO,
            rotationDegrees = 90.0,
        )
        val points = bed.outlines().flatten()
        assertEquals(2000.0, points.maxOf { it.x } - points.minOf { it.x }, 1.0)
        assertEquals(1600.0, points.maxOf { it.y } - points.minOf { it.y }, 1.0)
    }

    @Test
    fun `a piece can be given a size of its own, and one table covers many`() {
        val long = DrawnShape.Block(
            "m1",
            CatalogueBlock.DINING_TABLE_FOUR,
            Vec3.ZERO,
            widthMm = 2400.0,
            depthMm = 1000.0,
        )
        val points = long.outlines().flatten()
        assertEquals(2400.0, points.maxOf { it.x } - points.minOf { it.x }, 1.0)
        assertEquals(1000.0, points.maxOf { it.y } - points.minOf { it.y }, 1.0)
    }

    @Test
    fun `its width and depth are the two numbers that can be typed into it`() {
        val sofa = DrawnShape.Block("m1", CatalogueBlock.SOFA_THREE, Vec3.ZERO)
        assertEquals(
            listOf(ShapeDimension.WIDTH, ShapeDimension.DEPTH),
            sofa.dimensions().keys.toList(),
        )
        assertEquals(2100.0, sofa.dimensions()[ShapeDimension.WIDTH]!!, 1e-6)

        val wider = assertIs<DrawnShape.Block>(sofa.withDimension(ShapeDimension.WIDTH, 2400.0))
        assertEquals(2400.0, wider.drawnWidthMm, 1e-6)
        // Its depth is still the library's, because only the width was typed.
        assertEquals(850.0, wider.drawnDepthMm, 1e-6)
    }

    @Test
    fun `a piece is picked by pointing at it, not by finding its edge`() {
        val plan = listOf(DrawnShape.Block("m1", CatalogueBlock.BATH, Vec3(1000.0, 1000.0, 0.0)))
        assertNotNull(plan.pick(Vec2(1000.0, 1000.0), toleranceMm = 1.0))
        assertNull(plan.pick(Vec2(3000.0, 3000.0), toleranceMm = 1.0))
    }

    @Test
    fun `a piece is drawn as outlines, not filled like a wall`() {
        val sofa = DrawnShape.Block("m1", CatalogueBlock.SOFA_THREE, Vec3.ZERO)
        assertTrue(!sofa.isBand(), "furniture filled in would read as building")
        assertTrue(sofa.toEntities().isNotEmpty())
        assertEquals(DrawnShape.LAYER_FURNITURE, sofa.layer)
    }

    @Test
    fun `it moves where it is dragged`() {
        val sofa = DrawnShape.Block("m1", CatalogueBlock.ARMCHAIR, Vec3(100.0, 200.0, 0.0))
        val moved = assertIs<DrawnShape.Block>(sofa.translated(500.0, -300.0))
        assertEquals(600.0, moved.at.x, 1e-6)
        assertEquals(-100.0, moved.at.y, 1e-6)
    }

    @Test
    fun `a project keeps which piece it used, not a copy of the drawing`() {
        val root = java.nio.file.Files.createTempDirectory("pafta-library").toFile()
        val store = ProjectStore(root)
        val entry = assertNotNull(store.create("Kütüphane").valueOrNull())
        val opened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        val shapes = listOf<DrawnShape>(
            DrawnShape.Block("m1", CatalogueBlock.WC, Vec3(500.0, 500.0, 0.0), rotationDegrees = 90.0),
            DrawnShape.Block(
                "m2",
                CatalogueBlock.DINING_TABLE_SIX,
                Vec3(3000.0, 2000.0, 0.0),
                widthMm = 2400.0,
            ),
        )
        assertIs<StoreResult.Success<PaftaProject>>(store.save(opened.copy(shapes = shapes), entry.file))

        val reopened = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertEquals(shapes, reopened.shapes)
        root.deleteRecursively()
    }

    // --- the twenty wall types ------------------------------------------------

    @Test
    fun `the wall catalogue has the twenty the specification asked for`() {
        assertEquals(20, WallType.entries.size)
        // Every material is represented, and every function is used by
        // something — a catalogue with an unreachable entry is a catalogue with
        // a mistake in it.
        for (material in WallMaterial.entries) {
            assertTrue(WallType.entries.any { it.material == material }, "$material has no type")
        }
        for (function in WallFunction.entries) {
            assertTrue(WallType.entries.any { it.function == function }, "$function is never used")
        }
    }

    @Test
    fun `a wall type is a preset, and builds an ordinary wall`() {
        val wall = WallType.CONCRETE_SHEAR.wallBetween(
            "w1",
            Vec3(0.0, 0.0, 0.0),
            Vec3(4000.0, 0.0, 0.0),
        )
        assertEquals(300.0, wall.thicknessMm, 1e-6)
        assertEquals(WallMaterial.CONCRETE, wall.material)
        // The layer is the one any 300mm concrete wall lands on, whether it came
        // from the catalogue or from the thickness buttons: one wall, one layer.
        assertEquals(DrawnShape.wallLayer(300.0, WallMaterial.CONCRETE), wall.layer)
    }

    @Test
    fun `every type's code survives being written into a DXF layer name`() {
        for (type in WallType.entries) {
            assertTrue(
                type.code.all { it.code < 128 },
                "${type.name} has a code that will not export: ${type.code}",
            )
        }
    }
}

/** Turning and sizing a piece once it is down. */
class BlockEditTest {

    @Test
    fun `turning a sofa turns it where it stands`() {
        val sofa = DrawnShape.Block("m1", CatalogueBlock.SOFA_THREE, Vec3(2000.0, 1000.0, 0.0))
        val turned = assertIs<DrawnShape.Block>(sofa.turnedBy(90.0))

        assertEquals(90.0, turned.rotationDegrees, 1e-6)
        // It has not drifted: same point on the sheet.
        assertEquals(2000.0, turned.at.x, 1e-6)
        assertEquals(1000.0, turned.at.y, 1e-6)
        // And it is now as wide as it was deep.
        val points = turned.outlines().flatten()
        assertEquals(850.0, points.maxOf { it.x } - points.minOf { it.x }, 1.0)
    }

    @Test
    fun `turning twice adds up`() {
        val sofa = DrawnShape.Block("m1", CatalogueBlock.ARMCHAIR, Vec3.ZERO)
        val twice = assertIs<DrawnShape.Block>(
            assertIs<DrawnShape.Block>(sofa.turnedBy(90.0)).turnedBy(90.0),
        )
        assertEquals(180.0, twice.rotationDegrees, 1e-6)
    }

    @Test
    fun `a column turns too, which is what a 30x60 column is for`() {
        val column = DrawnShape.Column("k1", Vec3.ZERO, widthMm = 600.0, depthMm = 300.0)
        val turned = assertIs<DrawnShape.Column>(column.turnedBy(90.0))
        val corners = turned.outline()
        assertEquals(300.0, corners.maxOf { it.x } - corners.minOf { it.x }, 1.0)
        assertEquals(600.0, corners.maxOf { it.y } - corners.minOf { it.y }, 1.0)
    }

    @Test
    fun `scaling a piece scales both its sides`() {
        val bed = DrawnShape.Block("m1", CatalogueBlock.BED_DOUBLE, Vec3.ZERO)
        val half = assertIs<DrawnShape.Block>(bed.scaledBy(0.5))
        assertEquals(800.0, half.drawnWidthMm, 1e-6)
        assertEquals(1000.0, half.drawnDepthMm, 1e-6)
    }
}
