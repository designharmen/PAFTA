package com.harmen.pafta.dxf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The colours a CAD file names.
 *
 * The first nine are the ones every drawing uses and the ones an architect
 * would notice instantly if they were wrong, so they are pinned by hand.
 */
class AciColoursTest {

    @Test
    fun `temel renkler dogru`() {
        assertEquals(0xFF0000, AciColours.rgbOf(1))
        assertEquals(0xFFFF00, AciColours.rgbOf(2))
        assertEquals(0x00FF00, AciColours.rgbOf(3))
        assertEquals(0x00FFFF, AciColours.rgbOf(4))
        assertEquals(0x0000FF, AciColours.rgbOf(5))
        assertEquals(0xFF00FF, AciColours.rgbOf(6))
        assertEquals(0xFFFFFF, AciColours.rgbOf(7))
        assertEquals(0x808080, AciColours.rgbOf(8))
        assertEquals(0xC0C0C0, AciColours.rgbOf(9))
    }

    @Test
    fun `tablo tam ve sinirlari belli`() {
        assertEquals(256, AciColours.COUNT)
        assertNull(AciColours.rgbOf(0), "0 bir renk değil, \"bloktan al\" demek")
        assertNull(AciColours.rgbOf(256), "256 bir renk değil, \"katmandan al\" demek")
        assertNull(AciColours.rgbOf(9999))
        // Every number in between names a colour.
        for (i in 1 until 256) {
            assertEquals(true, AciColours.rgbOf(i) != null, "$i numaralı renk yok")
        }
    }

    @Test
    fun `kapatilmis katmanin rengi de okunur`() {
        // A layer switched off is written as a negative number; it still has a
        // colour, and whether it is drawn is a separate question.
        assertEquals(AciColours.rgbOf(4), AciColours.rgbOf(-4))
    }

    @Test
    fun `katmandan al diyen oge katmanin rengini alir`() {
        // Which is nearly every entity in a real drawing.
        assertEquals(0x00FFFF, AciColours.resolve(AciColours.BY_LAYER, 4))
        assertEquals(0xFFFF00, AciColours.resolve(AciColours.BY_BLOCK, 2))
    }

    @Test
    fun `kendi rengini soyleyen oge katmani dinlemez`() {
        assertEquals(0xFF0000, AciColours.resolve(1, 4))
    }

    @Test
    fun `iki taraf da susuyorsa cevap yok`() {
        assertNull(AciColours.resolve(AciColours.BY_LAYER, AciColours.BY_LAYER))
    }
}
