package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deciding what is on screen.
 *
 * The rule the tests hold it to: it may say yes about something that turns out
 * not to be visible, and it may never say no about something that is. One extra
 * line drawn costs nothing; one wall missing is a wrong drawing.
 */
class DxfCullingTest {

    private val window = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(100.0, 100.0, 0.0))

    @Test
    fun `icerideki ogeler gorunur sayilir`() {
        assertTrue(DxfEntity.Line("0", Vec3(10.0, 10.0, 0.0), Vec3(20.0, 20.0, 0.0)).mayBeVisible(window))
        assertTrue(DxfEntity.Point("0", Vec3(50.0, 50.0, 0.0)).mayBeVisible(window))
        assertTrue(DxfEntity.Circle("0", Vec3(50.0, 50.0, 0.0), 5.0).mayBeVisible(window))
        assertTrue(DxfEntity.Polyline("0", listOf(Vec2(5.0, 5.0), Vec2(90.0, 90.0))).mayBeVisible(window))
    }

    @Test
    fun `uzaktaki ogeler atlanir`() {
        assertFalse(
            DxfEntity.Line("0", Vec3(500.0, 500.0, 0.0), Vec3(600.0, 600.0, 0.0)).mayBeVisible(window),
        )
        assertFalse(DxfEntity.Point("0", Vec3(-10.0, 50.0, 0.0)).mayBeVisible(window))
        assertFalse(DxfEntity.Circle("0", Vec3(500.0, 50.0, 0.0), 5.0).mayBeVisible(window))
        assertFalse(
            DxfEntity.Polyline("0", listOf(Vec2(200.0, 200.0), Vec2(300.0, 300.0))).mayBeVisible(window),
        )
    }

    @Test
    fun `pencereyi kesen oge atlanmaz`() {
        // A wall that runs right across the room and out the other side: both
        // of its ends are off screen and it is still visible.
        assertTrue(
            DxfEntity.Line("0", Vec3(-500.0, 50.0, 0.0), Vec3(500.0, 50.0, 0.0)).mayBeVisible(window),
        )
        // A circle whose centre is off screen but whose edge crosses it.
        assertTrue(DxfEntity.Circle("0", Vec3(-20.0, 50.0, 0.0), 40.0).mayBeVisible(window))
        // A polyline that merely passes through.
        assertTrue(
            DxfEntity.Polyline("0", listOf(Vec2(-50.0, -50.0), Vec2(150.0, 150.0))).mayBeVisible(window),
        )
    }

    @Test
    fun `pencere yoksa her sey cizilir`() {
        // No window means nothing is known about the view, so nothing is
        // dropped: an empty answer must not empty the screen.
        val none = Aabb.EMPTY
        assertTrue(DxfEntity.Line("0", Vec3(1e9, 1e9, 0.0), Vec3(2e9, 2e9, 0.0)).mayBeVisible(none))
    }

    @Test
    fun `yazi ve blok isareti kendi yerinden degerlendirilir`() {
        assertTrue(
            DxfEntity.Text("0", Vec3(50.0, 50.0, 0.0), height = 2.5, value = "SALON")
                .mayBeVisible(window),
        )
        assertFalse(
            DxfEntity.Text("0", Vec3(-900.0, 50.0, 0.0), height = 2.5, value = "SALON")
                .mayBeVisible(window),
        )
        assertTrue(DxfEntity.Insert("0", "KAPI", Vec3(10.0, 10.0, 0.0)).mayBeVisible(window))
    }

    @Test
    fun `bos oge cizilmez`() {
        assertFalse(DxfEntity.Polyline("0", emptyList()).mayBeVisible(window))
        assertFalse(DxfEntity.Face("0", emptyList()).mayBeVisible(window))
    }
}
