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
 * The four files are AutoCAD's own, from the test set of the LibreDWG project
 * (GPL-3.0; recorded in `docs/LICENCES.md`), and they are all the same drawing
 * saved by four different releases: R14, 2000, 2004 and 2018. That is what
 * makes the comparison below worth having — four unrelated file layouts, from a
 * plain file with a table of contents to a set of compressed pages with the
 * text held separately as Unicode, all have to produce the same geometry.
 *
 * The numbers asserted here were taken from the same drawing exported to DXF —
 * a format with a published specification — so they are the drawing's real
 * coordinates and not this reader's opinion of them.
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
    fun `olcu cizgileri de ciziliyor`() {
        val drawing = drawingOf("autocad-2000.dwg")
        assertEquals(9, drawing.entityTypeCounts["DIMENSION"])

        // A dimension keeps its line, its arrows and its number in a block of
        // its own, so reading the dimension means placing that block. Each one
        // has its own, and every one of them is called `*D` — so they are kept
        // apart by which object they are, not by their name. Nine dimensions
        // that collapsed into one would draw the same pile nine times.
        val dimensionBlocks = drawing.blockEntityCounts.keys.filter { it.startsWith("*D") }
        assertTrue(
            dimensionBlocks.size >= 9,
            "ölçü blokları birbirine karışmış: $dimensionBlocks",
        )

        // The measurement itself is a piece of text inside that block, so a
        // drawn dimension is a drawn number.
        val numbers = drawing.entities.filterIsInstance<DxfEntity.Text>().map { it.value }
        assertTrue(
            numbers.any { it == "1504,68" },
            "ölçü rakamı çizime girmemiş: $numbers",
        )
    }

    @Test
    fun `bloga doldurulmus yazilar da ciziliyor`() {
        // A block's filled-in text — a room name, a door number, a title block
        // field. It belongs to the block reference rather than to a block, and
        // was being dropped for want of a block to put it in.
        val texts = drawingOf("autocad-2000.dwg").entities.filterIsInstance<DxfEntity.Text>()
        val filled = texts.firstOrNull { it.value == "valoro de la teksto en bloko" }
        assertNotNull(filled, "blok yazısı çizime girmemiş: ${texts.map { it.value }}")
        assertEquals(100.0, filled.height, 1e-9)
        // In the drawing's own coordinates, not the block's.
        assertEquals(3352.9, filled.position.x, 0.1)
        assertEquals(5848.0, filled.position.y, 0.1)
    }

    @Test
    fun `dort ayri DWG surumu ayni cizimi ayni okur`() {
        val files = listOf(
            "autocad-r14.dwg",
            "autocad-2000.dwg",
            "autocad-2004.dwg",
            "autocad-2018.dwg",
        )
        val reference = drawingOf(files.first())

        // Every line in one is a line in every other, to the micrometre. The
        // comparison is of geometry rather than layer names because the names
        // themselves differ in case between releases.
        val ends = { d: com.harmen.pafta.dxf.DxfDrawing ->
            d.entities.filterIsInstance<DxfEntity.Line>()
                .map { "%.6f %.6f %.6f %.6f".format(it.start.x, it.start.y, it.end.x, it.end.y) }
                .sorted()
        }
        val kinds = { d: com.harmen.pafta.dxf.DxfDrawing ->
            d.entities.groupingBy { it::class.simpleName!! }.eachCount()
        }

        for (file in files.drop(1)) {
            val other = drawingOf(file)
            assertEquals(reference.entities.size, other.entities.size, file)
            assertEquals(kinds(reference), kinds(other), file)
            assertEquals(ends(reference), ends(other), file)
            assertEquals(reference.entityTypeCounts["LINE"], other.entityTypeCounts["LINE"], file)
        }
    }

    @Test
    fun `2018 dosyasindaki yazilar da okunur`() {
        // From AutoCAD 2007 every string sits in a run at the end of its own
        // object, as Unicode, and nothing at all is written where it is used.
        // A reader that missed that would still get the geometry and lose
        // every word in the drawing.
        val texts = drawingOf("autocad-2018.dwg").entities.filterIsInstance<DxfEntity.Text>()
        assertTrue(texts.any { it.value == "teksto simpla" }, "yazı okunamadı: ${texts.map { it.value }}")
        assertEquals(
            100.0,
            texts.first { it.value == "teksto simpla" }.height,
            1e-9,
        )
        assertTrue(
            drawingOf("autocad-2018.dwg").layers.any { it.name == "Tavolo 3" },
            "katman adı okunamadı",
        )
    }

    @Test
    fun `AutoCAD 2007 bicimi yil bilgisiyle reddedilir`() {
        // The one release this reader does not open. The refusal has to carry
        // the year, because that is what the user picks from in their own
        // program's "Save as" list.
        val outcome = DwgReader.read("AC1021".toByteArray() + ByteArray(4096))
        val reason = (outcome as DwgOutcome.Failure).reason
        assertTrue(reason is DwgFailure.UnsupportedVersion, "beklenen ret sebebi değil: $reason")
        assertEquals(2007, (reason as DwgFailure.UnsupportedVersion).version.year)
    }

    @Test
    fun `her DWG surumu tanınır`() {
        assertEquals(DwgVersion.R14, DwgVersion.of("AC1014".toByteArray()))
        assertEquals(DwgVersion.R2000, DwgVersion.of("AC1015".toByteArray()))
        assertEquals(DwgVersion.R2004, DwgVersion.of("AC1018".toByteArray()))
        assertEquals(DwgVersion.R2018, DwgVersion.of("AC1032".toByteArray()))
        assertEquals(null, DwgVersion.of("AC9999".toByteArray()))
        // The old formats are recognised so they can be refused by year rather
        // than called damaged, and 2007 is the one modern version left out.
        assertEquals(DwgVersion.R12, DwgVersion.of("AC1009".toByteArray()))
        assertEquals(
            listOf(DwgVersion.R9, DwgVersion.R10, DwgVersion.R11, DwgVersion.R12, DwgVersion.R2007),
            DwgVersion.entries.filter { !it.readable },
        )
    }

    @Test
    fun `DWG olmayan baytlar reddedilir`() {
        val outcome = DwgReader.read("bu bir çizim dosyası değil".toByteArray())
        assertEquals(DwgFailure.NotADwg, (outcome as DwgOutcome.Failure).reason)
    }

    @Test
    fun `yarim kalmis dosya cokertmez`() {
        for (name in listOf("autocad-2000.dwg", "autocad-2018.dwg")) cutShort(name)
    }

    private fun cutShort(name: String) {
        val whole = load(name)
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
        for (name in listOf("autocad-2000.dwg", "autocad-2018.dwg")) scramble(name)
    }

    private fun scramble(name: String) {
        val whole = load(name)
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
