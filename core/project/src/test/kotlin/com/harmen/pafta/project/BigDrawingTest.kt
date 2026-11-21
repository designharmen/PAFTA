package com.harmen.pafta.project

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A drawing too big for the device it is on.
 *
 * Opening one needs room for several copies at once — the file's bytes, the
 * unpacked contents, the geometry built from them — so a file that does not fit
 * does not fail, it takes the app down with it. That has to end as a sentence
 * on the screen instead, with the file's size, the device's limit and what to
 * do about it.
 */
class BigDrawingTest {

    private val dir = createTempDirectory("pafta-big").toFile()
    private val store = ProjectStore(dir)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sample(): ByteArray =
        javaClass.getResourceAsStream("/ornek-cizim-autocad-2018.dwg")!!.readBytes()

    @Test
    fun `cihaza sigmayan cizim boyutuyla ve sinirla reddedilir`() {
        // Nine megabytes of DWG on a device whose limit is the floor of eight.
        // It is turned away before anything is unpacked, which is the point:
        // the refusal must cost nothing, or it is the crash it was meant to
        // prevent.
        val big = "AC1032".toByteArray() + ByteArray(9 * 1024 * 1024)
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("buyuk.dwg", big.inputStream()),
        ).value
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value

        val failure = assertIs<StoreResult.Failure>(
            project.openAsDrawing(memoryBudgetBytes = 1024 * 1024),
        ).failure
        val tooLarge = assertIs<StoreFailure.TooLarge>(failure)
        assertEquals(big.size.toLong(), tooLarge.sizeBytes)
        assertTrue(
            tooLarge.limitBytes < tooLarge.sizeBytes,
            "sınır dosyadan büyük, ret anlamsız: ${tooLarge.limitBytes}",
        )
    }

    @Test
    fun `kucuk cizimler en dar cihazda da acilir`() {
        // The floor exists so that a device reporting an implausibly small heap
        // still opens ordinary drawings rather than refusing everything.
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("kucuk.dwg", sample().inputStream()),
        ).value
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        assertIs<StoreResult.Success<DrawingDocument>>(
            project.openAsDrawing(memoryBudgetBytes = 1024),
        )
    }

    @Test
    fun `yeterli bellekle ayni cizim acilir`() {
        val entry = assertIs<StoreResult.Success<ProjectEntry>>(
            store.import("buyuk.dwg", sample().inputStream()),
        ).value
        val project = assertIs<StoreResult.Success<PaftaProject>>(store.open(entry.file)).value
        val opened = assertIs<StoreResult.Success<DrawingDocument>>(
            project.openAsDrawing(memoryBudgetBytes = 512L * 1024 * 1024),
        ).value
        assertTrue(opened.entityCount > 100)
    }

    @Test
    fun `sinir cihazin belleginden hesaplaniyor`() {
        // Tablets differ by a factor of three or more, so the limit is theirs
        // rather than a number chosen here.
        val small = openableDrawingBytes(96L * 1024 * 1024)
        val large = openableDrawingBytes(768L * 1024 * 1024)
        assertTrue(small < large, "sınır cihaza göre değişmiyor")

        // With a floor, so a device reporting an implausibly small heap still
        // opens ordinary drawings...
        assertTrue(openableDrawingBytes(1024) >= 8L * 1024 * 1024)
        // ...and a ceiling, since nothing bigger can be imported anyway.
        assertEquals(256L * 1024 * 1024, openableDrawingBytes(Long.MAX_VALUE / 4))
    }
}
