package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfLayer
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingDocumentTest {

    private fun project(payload: ByteArray, fileName: String = "plan.dxf") =
        newProject("Plan", fileName, payload, 0)

    @Test
    fun `a dxf project opens as a drawing with bounds and layers`() {
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(
            project(sampleDxfBytes()).openAsDrawing(),
        ).value

        assertEquals(2, doc.entityCount)
        assertFalse(doc.isEmpty)
        assertEquals(5500.0, doc.bounds.max.x, 1e-6)
        assertEquals(4500.0, doc.bounds.max.y, 1e-6)
        assertEquals(listOf("0", "WALLS"), doc.layers.map { it.name })
        assertTrue(doc.layers.all { it.visible })
    }

    @Test
    fun `a payload that is not a drawing format is refused with a reason`() {
        val f = assertIs<StoreResult.Failure>(
            project(byteArrayOf(1, 2, 3), "model.glb").openAsDrawing(),
        )
        val failure = assertIs<StoreFailure.Unreadable>(f.failure)
        assertEquals(FileFormat.GLB, failure.format)
        assertEquals(UnreadableReason.NO_VIEWER_YET, failure.reason)
    }

    @Test
    fun `an unrecognised payload format is reported as unknown`() {
        val f = assertIs<StoreResult.Failure>(
            project(byteArrayOf(1), "notes.docx").openAsDrawing(),
        )
        assertIs<StoreFailure.UnknownFormat>(f.failure)
    }

    @Test
    fun `a malformed dxf payload is reported rather than drawn as empty`() {
        val f = assertIs<StoreResult.Failure>(
            project("0\nSECTION\n2\n".toByteArray()).openAsDrawing(),
        )
        assertEquals(UnreadableReason.MALFORMED, assertIs<StoreFailure.Unreadable>(f.failure).reason)
    }

    @Test
    fun `saved visibility and opacity survive reopening the drawing`() {
        val saved = project(sampleDxfBytes()).copy(
            layers = listOf(
                LayerState("WALLS", "WALLS", visible = false, opacity = 0.4, colour = "#C97D5D"),
            ),
        )
        val doc = assertIs<StoreResult.Success<DrawingDocument>>(saved.openAsDrawing()).value

        val walls = doc.layers.single { it.name == "WALLS" }
        assertFalse(walls.visible)
        assertEquals(40, walls.opacityPercent)
        assertEquals("#C97D5D", walls.colour)

        // A layer with no saved override comes back at its file defaults.
        val zero = doc.layers.single { it.name == "0" }
        assertTrue(zero.visible)
        assertEquals(100, zero.opacityPercent)
        assertNull(zero.colour)
    }
}

class MergeLayersTest {

    @Test
    fun `layers referenced by entities but absent from the tables still appear`() {
        // Files exported by other tools routinely draw on undeclared layers.
        val drawing = DxfDrawing(
            layers = listOf(DxfLayer("0")),
            entities = listOf(
                DxfEntity.Line("GHOST", Vec3.ZERO, Vec3(1.0, 1.0, 0.0)),
                DxfEntity.Line("0", Vec3.ZERO, Vec3(1.0, 0.0, 0.0)),
            ),
        )
        assertEquals(listOf("0", "GHOST"), mergeLayers(drawing, emptyList()).map { it.name })
    }

    @Test
    fun `a frozen layer starts hidden`() {
        val drawing = DxfDrawing(layers = listOf(DxfLayer("HIDDEN", frozen = true)))
        assertFalse(mergeLayers(drawing, emptyList()).single().visible)
    }

    @Test
    fun `the users saved choice overrides the files frozen flag`() {
        val drawing = DxfDrawing(layers = listOf(DxfLayer("HIDDEN", frozen = true)))
        val saved = listOf(LayerState("HIDDEN", "HIDDEN", visible = true))
        assertTrue(mergeLayers(drawing, saved).single().visible)
    }

    @Test
    fun `a layer added by a revised drawing appears visible`() {
        val drawing = DxfDrawing(layers = listOf(DxfLayer("OLD"), DxfLayer("NEW")))
        val saved = listOf(LayerState("OLD", "OLD", visible = false))
        val merged = mergeLayers(drawing, saved)

        assertFalse(merged.single { it.name == "OLD" }.visible)
        assertTrue(merged.single { it.name == "NEW" }.visible)
    }

    @Test
    fun `saved state for a layer no longer in the file is dropped`() {
        val drawing = DxfDrawing(layers = listOf(DxfLayer("STILL-HERE")))
        val saved = listOf(
            LayerState("STILL-HERE", "STILL-HERE", opacity = 0.5),
            LayerState("GONE", "GONE", visible = false),
        )
        val merged = mergeLayers(drawing, saved)

        assertEquals(listOf("STILL-HERE"), merged.map { it.name })
        assertEquals(50, merged.single().opacityPercent)
    }

    @Test
    fun `the file order is preserved so the palette matches the drawing`() {
        val drawing = DxfDrawing(
            layers = listOf(DxfLayer("C"), DxfLayer("A"), DxfLayer("B")),
        )
        assertEquals(listOf("C", "A", "B"), mergeLayers(drawing, emptyList()).map { it.name })
    }

    @Test
    fun `an empty drawing yields an empty palette rather than throwing`() {
        assertEquals(emptyList(), mergeLayers(DxfDrawing(), emptyList()))
    }
}
