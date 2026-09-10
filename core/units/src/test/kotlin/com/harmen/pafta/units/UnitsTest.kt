package com.harmen.pafta.units

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LengthUnitTest {
    @Test
    fun `conversions round trip through millimetres`() {
        for (u in LengthUnit.entries) {
            assertEquals(1234.5, u.fromMillimetres(u.toMillimetres(1234.5)), 1e-9, "unit $u")
        }
    }

    @Test
    fun `known conversion factors`() {
        assertEquals(5500.0, LengthUnit.METRE.toMillimetres(5.5), 1e-9)
        assertEquals(2.9, LengthUnit.METRE.fromMillimetres(2900.0), 1e-9)
        assertEquals(25.4, LengthUnit.INCH.toMillimetres(1.0), 1e-9)
        assertEquals(304.8, LengthUnit.FOOT.toMillimetres(1.0), 1e-9)
        assertEquals(120.0, LengthUnit.CENTIMETRE.toMillimetres(12.0), 1e-9)
    }

    @Test
    fun `symbols resolve case insensitively`() {
        assertEquals(LengthUnit.MILLIMETRE, LengthUnit.fromSymbol("MM"))
        assertEquals(LengthUnit.METRE, LengthUnit.fromSymbol(" m "))
        assertEquals(LengthUnit.INCH, LengthUnit.fromSymbol("inches"))
        assertEquals(LengthUnit.FOOT, LengthUnit.fromSymbol("'"))
        assertNull(LengthUnit.fromSymbol("furlong"))
    }
}

class AreaUnitTest {
    @Test
    fun `square metre conversion is quadratic`() {
        // 5500mm x 4500mm = 24.75 m2
        assertEquals(24.75, AreaUnit.SQUARE_METRE.fromSquareMillimetres(24_750_000.0), 1e-9)
        assertEquals(1_000_000.0, AreaUnit.SQUARE_METRE.toSquareMillimetres(1.0), 1e-6)
    }

    @Test
    fun `area unit matches its linear unit`() {
        assertEquals(AreaUnit.SQUARE_FOOT, AreaUnit.of(LengthUnit.FOOT))
        assertEquals(AreaUnit.SQUARE_METRE, AreaUnit.of(LengthUnit.METRE))
    }
}

class FormattingTest {
    @Test
    fun `millimetre labels match the reference drawing`() {
        assertEquals("5500mm", formatLength(5500.0))
        assertEquals("3100mm", formatLength(3100.0))
        assertEquals("120mm", formatLength(120.0))
        assertEquals("2900mm", formatLength(2900.0))
    }

    @Test
    fun `metres keep two decimals`() {
        assertEquals("5.5m", formatLength(5500.0, LengthFormat.METRES))
        assertEquals(
            "5.50m",
            formatLength(5500.0, LengthFormat(LengthUnit.METRE, 2, trimTrailingZeros = false)),
        )
    }

    @Test
    fun `optional space before the symbol`() {
        assertEquals("5500 mm", formatLength(5500.0, LengthFormat(spaceBeforeSymbol = true)))
    }

    @Test
    fun `values that round to zero are not rendered as minus zero`() {
        assertEquals("0mm", formatLength(-0.0000001))
    }

    @Test
    fun `area and angle labels`() {
        assertEquals("24.75m²", formatArea(24_750_000.0))
        assertEquals("90°", formatAngle(PI / 2))
        assertEquals("45°", formatAngle(PI / 4))
        assertEquals("1.57 rad", formatAngle(PI / 2, AngleUnit.RADIANS, decimals = 2))
    }

    @Test
    fun `feet and inches notation`() {
        assertEquals("1' 0\"", formatFeetInches(304.8))
        assertEquals("6\"", formatFeetInches(152.4))
        assertEquals("18' 0 1/2\"", formatFeetInches(18 * 304.8 + 12.7))
        assertEquals("-1' 0\"", formatFeetInches(-304.8))
        // 11 15/16" must not be promoted to 12" by rounding.
        assertEquals("11 15/16\"", formatFeetInches(11.9375 * 25.4))
        // 11.99" rounds to 12" and must carry into the feet column.
        assertEquals("1' 0\"", formatFeetInches(11.99 * 25.4))
    }
}

class ParsingTest {
    @Test
    fun `bare numbers use the default unit`() {
        assertEquals(5500.0, parseLengthToMillimetres("5500")!!, 1e-9)
        assertEquals(5500.0, parseLengthToMillimetres("5.5", LengthUnit.METRE)!!, 1e-9)
    }

    @Test
    fun `explicit suffixes win over the default unit`() {
        assertEquals(5500.0, parseLengthToMillimetres("5500mm", LengthUnit.METRE)!!, 1e-9)
        assertEquals(5500.0, parseLengthToMillimetres("5.5 m")!!, 1e-9)
        assertEquals(5500.0, parseLengthToMillimetres("550cm")!!, 1e-9)
        assertEquals(25.4, parseLengthToMillimetres("1in")!!, 1e-9)
    }

    @Test
    fun `decimal comma is accepted`() {
        assertEquals(5500.0, parseLengthToMillimetres("5,5 m")!!, 1e-9)
    }

    @Test
    fun `negative values`() {
        assertEquals(-1200.0, parseLengthToMillimetres("-1200")!!, 1e-9)
    }

    @Test
    fun `imperial forms`() {
        assertEquals(304.8, parseLengthToMillimetres("1'")!!, 1e-9)
        assertEquals(25.4, parseLengthToMillimetres("1\"")!!, 1e-9)
        assertEquals(304.8 + 152.4, parseLengthToMillimetres("1' 6\"")!!, 1e-9)
        assertEquals(304.8 + 152.4, parseLengthToMillimetres("1'6\"")!!, 1e-9)
        assertEquals(12.7, parseLengthToMillimetres("1/2\"")!!, 1e-9)
        assertEquals(304.8 + 6.5 * 25.4, parseLengthToMillimetres("1' 6 1/2\"")!!, 1e-9)
    }

    @Test
    fun `imperial round trips through the formatter`() {
        val mm = parseLengthToMillimetres("18' 0 1/2\"")!!
        assertEquals("18' 0 1/2\"", formatFeetInches(mm))
    }

    @Test
    fun `unparseable input returns null rather than guessing`() {
        assertNull(parseLengthToMillimetres(""))
        assertNull(parseLengthToMillimetres("   "))
        assertNull(parseLengthToMillimetres("abc"))
        assertNull(parseLengthToMillimetres("12 furlongs"))
        assertNull(parseLengthToMillimetres("1/0\""))
        assertNull(parseLengthToMillimetres("-"))
    }
}
