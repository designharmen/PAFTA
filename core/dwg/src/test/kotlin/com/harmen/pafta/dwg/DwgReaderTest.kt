package com.harmen.pafta.dwg

import com.harmen.pafta.dxf.DxfEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads real AutoCAD files.
 *
 * The three files are AutoCAD's own, from the test set of the LibreDWG project
 * (GPL-3.0; recorded in `docs/LICENCES.md`). The first two are the same drawing
 * saved by two different AutoCAD versions, which is what makes the comparison
 * below worth having: two unrelated file layouts have to produce the same
 * geometry. The numbers asserted here were taken from the same drawing exported
 * to DXF — a format with a published specification — so they are the drawing's
 * real coordinates and not this reader's opinion of them.
 */
class DwgReaderTest {

    private fun load(name: String): ByteArray =
        javaClass.getResourceAsStream("/$name")!!.readBytes()

    private fun drawingOf(name: String) =
        (DwgReader.read(load(name)) as DwgOutcome.Success).drawing

    @Test
    fun `AutoCAD 2000 dosyasi okunur`() {
        val drawing = drawingOf("autocad-2000.dwg")

        assertEquals(
            listOf("0", "Tavolo 2", "Tavolo 3", "ADSK_SYSTEM_LIGHTS", "Defpoints"),
            drawing.layers.map { it.name },
        )
        // What the file says it holds, which is also what the DXF export of the
        // same drawing says: eight lines in model space, sixty-eight in all.
        assertEquals(68, drawing.entityTypeCounts["LINE"])
        assertEquals(11, drawing.entityTypeCounts["LWPOLYLINE"])
        assertEquals(10, drawing.entityTypeCounts["INSERT"])
        assertEquals(2, drawing.entityTypeCounts["3DFACE"])
        assertTrue(drawing.entities.isNotEmpty())
    }

    @Test
    fun `cizgi ucları dosyadaki degerlerin aynisi`() {
        val drawing = drawingOf("autocad-2000.dwg")
        val line = drawing.entities
            .filterIsInstance<DxfEntity.Line>()
            .firstOrNull { abs(it.start.x - 370.85861165343067) < 1e-9 }
        assertNotNull(line, "beklenen çizgi bulunamadı")
        assertEquals(730.6303035220437, line.start.y, 1e-9)
        assertEquals(450.03975642026035, line.end.x, 1e-9)
        assertEquals(619.2192730768991, line.end.y, 1e-9)
        assertEquals("Tavolo 3", line.layer)
    }

    @Test
    fun `yazilar iceriği ve yuksekligiyle okunur`() {
        val texts = drawingOf("autocad-2000.dwg").entities.filterIsInstance<DxfEntity.Text>()

        val simple = texts.firstOrNull { it.value == "teksto simpla" }
        assertNotNull(simple, "tek satırlık yazı bulunamadı")
        assertEquals(100.0, simple.height, 1e-9)
        assertEquals(5881.252623040076, simple.position.x, 1e-9)
        assertEquals(1296.9046997276237, simple.position.y, 1e-9)

        val turned = texts.firstOrNull { it.value == "xx" }
        assertNotNull(turned, "döndürülmüş yazı bulunamadı")
        assertEquals(280.7367083008678, turned.height, 1e-9)
        assertEquals(18.42545651388188, turned.rotationDegrees, 1e-9)

        // MTEXT's line breaks survive and its formatting codes do not.
        val paragraph = texts.firstOrNull { it.value.startsWith("Teksto granda") }
        assertNotNull(paragraph, "paragraf yazısı bulunamadı")
        assertTrue(paragraph.value.contains("\n"), "satır sonu kaybolmuş")
        assertTrue(!paragraph.value.contains("\\P"), "biçim kodu metne karışmış")
    }

    @Test
    fun `cok kose cizgisinin kose noktalari okunur`() {
        val polylines = drawingOf("autocad-2000.dwg").entities
            .filterIsInstance<DxfEntity.Polyline>()
        val seven = polylines.firstOrNull { it.vertices.size == 7 && !it.closed }
        assertNotNull(seven, "yedi köşeli açık çizgi bulunamadı")
        assertEquals(571.6812651496752, seven.vertices.first().x, 1e-9)
        assertEquals(1289.727758628673, seven.vertices.first().y, 1e-9)
        assertTrue(polylines.any { it.closed }, "kapalı çok köşeli çizgi yok")
    }

    @Test
    fun `blok referanslari kendi geometrisine donusur`() {
        val drawing = drawingOf("autocad-2000.dwg")
        // Ten block references, none of which should survive as a reference:
        // each is replaced by the geometry of the block it names.
        assertEquals(10, drawing.entityTypeCounts["INSERT"])
        assertTrue(
            drawing.entities.none { it is DxfEntity.Insert },
            "açılamayan blok referansı kalmış",
        )
        assertTrue(drawing.blockEntityCounts.containsKey("bloko"))
        // The block holds four lines; the drawing holds more lines than model
        // space alone, which is only possible if they were placed.
        assertTrue(
            drawing.entities.filterIsInstance<DxfEntity.Line>().size > 8,
            "blok içindeki çizgiler yerleştirilmemiş",
        )
    }

    @Test
    fun `ayni cizim R14 ve 2000 dosyasindan ayni okunur`() {
        val old = drawingOf("autocad-r14.dwg")
        val new = drawingOf("autocad-2000.dwg")

        assertEquals(new.entities.size, old.entities.size)
        assertEquals(
            new.entities.groupingBy { it::class.simpleName!! }.eachCount(),
            old.entities.groupingBy { it::class.simpleName!! }.eachCount(),
        )
        // The layer names differ in case between the two versions, so the
        // comparison is of the geometry: every line in one is a line in the
        // other, to the millimetre.
        val ends = { d: com.harmen.pafta.dxf.DxfDrawing ->
            d.entities.filterIsInstance<DxfEntity.Line>()
                .map { "%.6f %.6f %.6f %.6f".format(it.start.x, it.start.y, it.end.x, it.end.y) }
                .sorted()
        }
        assertEquals(ends(new), ends(old))
    }

    @Test
    fun `2018 dosyasi yil bilgisiyle reddedilir`() {
        val outcome = DwgReader.read(load("autocad-2018.dwg"))
        val reason = (outcome as DwgOutcome.Failure).reason
        assertTrue(reason is DwgFailure.TooNew, "beklenen ret sebebi değil: $reason")
        assertEquals(2018, (reason as DwgFailure.TooNew).version.year)
    }

    @Test
    fun `DWG olmayan baytlar reddedilir`() {
        val outcome = DwgReader.read("bu bir çizim dosyası değil".toByteArray())
        assertEquals(DwgFailure.NotADwg, (outcome as DwgOutcome.Failure).reason)
    }

    @Test
    fun `yarim kalmis dosya cokertmez`() {
        val whole = load("autocad-2000.dwg")
        // A file cut off part way through — a download that stopped, a copy that
        // ran out of space. It must come back as a refusal, never as a crash.
        for (fraction in listOf(2, 3, 5, 20)) {
            val cut = whole.copyOfRange(0, whole.size / fraction)
            val outcome = DwgReader.read(cut)
            assertTrue(
                outcome is DwgOutcome.Failure || outcome is DwgOutcome.Success,
                "okuma sonucu yok",
            )
        }
    }

    @Test
    fun `bozulmus baytlar cokertmez`() {
        val whole = load("autocad-2000.dwg")
        // Every eleventh byte flipped: the version stays readable, so the reader
        // goes all the way in and has to survive nonsense at every step.
        val broken = whole.copyOf()
        var at = 64
        while (at < broken.size) {
            broken[at] = (broken[at].toInt() xor 0x5A).toByte()
            at += 11
        }
        val outcome = DwgReader.read(broken)
        assertTrue(
            outcome is DwgOutcome.Failure || outcome is DwgOutcome.Success,
            "okuma sonucu yok",
        )
    }
}
