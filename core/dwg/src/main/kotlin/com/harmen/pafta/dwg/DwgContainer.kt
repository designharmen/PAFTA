package com.harmen.pafta.dwg

/**
 * Finds the drawing inside an AutoCAD 2004-or-later file.
 *
 * Up to AutoCAD 2000 a DWG was a plain file with a table of contents at a fixed
 * place. From 2004 it became a container: a scrambled directory at byte 128,
 * pointing at a map of pages, pointing at a map of sections, each section
 * spread over compressed pages that have to be unpacked and stitched back
 * together in order. Nothing about the drawing is reachable until all of that
 * is done — which is why this file exists and why it says nothing about
 * geometry.
 *
 * Two sections matter: `AcDb:AcDbObjects`, which is every object in the
 * drawing, and `AcDb:Handles`, the index into it. `AcDb:Classes` names the
 * object types the file declares for itself.
 */
internal class DwgSections(
    val objects: ByteArray,
    val handles: ByteArray,
    val classes: ByteArray?,
)

internal object DwgContainer {

    /** Null when the container's own structure does not hold together. */
    fun read(data: ByteArray): DwgSections? = try {
        val directory = directory(data) ?: return null
        val pages = pageOffsets(data, directory) ?: return null
        val sections = sectionMap(data, pages, directory) ?: return null
        val objects = assemble(data, pages, sections[OBJECTS_SECTION] ?: return null)
        val handles = assemble(data, pages, sections[HANDLES_SECTION] ?: return null)
        val classes = sections[CLASSES_SECTION]?.let { assemble(data, pages, it) }
        DwgSections(objects, handles, classes)
    } catch (e: RuntimeException) {
        null
    }

    // --- the scrambled directory at byte 128 ---------------------------------

    private class Directory(
        val pageMapAddress: Long,
        val pageMapId: Int,
        val sectionMapId: Int,
    )

    /**
     * Unscrambles the directory.
     *
     * It is obfuscated rather than encrypted: each byte is combined with a run
     * of numbers from a fixed arithmetic sequence, with no key. The point was to
     * stop people reading the file, not to keep a secret, and the twelve bytes
     * of magic at the front are how we know it came out right.
     */
    private fun directory(data: ByteArray): Directory? {
        if (data.size < DIRECTORY_AT + DIRECTORY_BYTES) return null
        val plain = ByteArray(DIRECTORY_BYTES)
        var seed = 1L
        for (i in 0 until DIRECTORY_BYTES) {
            seed = (seed * 0x343FDL + 0x269EC3L) and 0xFFFFFFFFL
            val key = ((seed shr 16) and 0xFF).toInt()
            plain[i] = (data[DIRECTORY_AT + i].toInt() xor key).toByte()
        }
        if (String(plain, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return null
        return Directory(
            pageMapAddress = int64(plain, 84),
            pageMapId = int32(plain, 80),
            sectionMapId = int32(plain, 92),
        )
    }

    // --- where each page is -------------------------------------------------

    private fun pageOffsets(data: ByteArray, directory: Directory): Map<Int, Int>? {
        val at = (PAGE_BASE + directory.pageMapAddress).toInt()
        val page = systemPage(data, at, PAGE_MAP_TYPE) ?: return null
        val out = HashMap<Int, Int>()
        var offset = PAGE_BASE.toInt()
        var i = 0
        while ((i + 1) * 8 <= page.size) {
            val number = int32(page, i * 8)
            val size = int32(page, i * 8 + 4)
            if (size < 0) return out
            if (number > 0) out[number] = offset
            offset += size
            if (offset > data.size + size) return out
            i++
        }
        return out
    }

    /** A system page: a fixed header, then one compressed lump. */
    private fun systemPage(data: ByteArray, at: Int, expectedType: Int): ByteArray? {
        if (at < 0 || at + SYSTEM_PAGE_HEADER > data.size) return null
        if (int32(data, at) != expectedType) return null
        val decompressed = int32(data, at + 4)
        val compressed = int32(data, at + 8)
        if (decompressed <= 0 || decompressed > MAX_SYSTEM_PAGE) return null
        if (compressed <= 0 || at + SYSTEM_PAGE_HEADER + compressed > data.size) return null
        return DwgLz77.inflate(data, at + SYSTEM_PAGE_HEADER, compressed, decompressed)
    }

    // --- what the sections are ----------------------------------------------

    private class SectionPage(val number: Int, val startOffset: Int)

    private class SectionInfo(
        val pageCount: Int,
        val maxPageSize: Int,
        val compressed: Boolean,
        val pages: List<SectionPage>,
    )

    private fun sectionMap(
        data: ByteArray,
        pages: Map<Int, Int>,
        directory: Directory,
    ): Map<String, SectionInfo>? {
        val at = pages[directory.sectionMapId] ?: return null
        val map = systemPage(data, at, SECTION_MAP_TYPE) ?: return null
        val count = int32(map, 0)
        if (count <= 0 || count > MAX_SECTIONS) return null
        val out = HashMap<String, SectionInfo>()
        var p = SECTION_MAP_HEADER
        repeat(count) {
            if (p + SECTION_ENTRY_BYTES > map.size) return out
            val pageCount = int32(map, p + 8)
            val maxPageSize = int32(map, p + 12)
            val compressed = int32(map, p + 20) == COMPRESSED
            p += 8 + 24
            val name = textAt(map, p, SECTION_NAME_BYTES)
            p += SECTION_NAME_BYTES
            if (pageCount < 0 || pageCount > MAX_SECTION_PAGES) return out
            val list = ArrayList<SectionPage>(pageCount)
            repeat(pageCount) {
                if (p + 16 > map.size) return out
                list += SectionPage(number = int32(map, p), startOffset = int32(map, p + 8))
                p += 16
            }
            if (name.isNotEmpty() && maxPageSize in 1..MAX_SECTION_PAGE_SIZE) {
                out[name] = SectionInfo(pageCount, maxPageSize, compressed, list)
            }
        }
        return out
    }

    // --- putting a section back together ------------------------------------

    /**
     * Unpacks a section's pages into one run of bytes.
     *
     * Each page says where in the finished section it belongs, so a page that
     * will not unpack leaves a hole rather than shifting everything after it.
     * Objects name their position in this run, so a shift would be worse than a
     * hole: a hole costs the objects in it, a shift costs all of them.
     */
    private fun assemble(
        data: ByteArray,
        pages: Map<Int, Int>,
        info: SectionInfo,
    ): ByteArray {
        val total = info.pageCount.toLong() * info.maxPageSize.toLong()
        if (total <= 0 || total > MAX_SECTION_BYTES) return ByteArray(0)
        val out = ByteArray(total.toInt())
        for (page in info.pages) {
            val at = pages[page.number] ?: continue
            if (at < 0 || at + DATA_PAGE_HEADER > data.size) continue
            // The page's own header is scrambled too, with a key made from
            // where the page sits — so a page moved somewhere else stops
            // reading, which is presumably the point.
            val mask = (DATA_PAGE_KEY xor at)
            val type = int32(data, at) xor mask
            if (type != DATA_PAGE_TYPE) continue
            val compressedSize = int32(data, at + 8) xor mask
            val pageSize = int32(data, at + 12) xor mask
            val start = int32(data, at + 16) xor mask
            if (start < 0 || start >= out.size) continue
            val body = at + DATA_PAGE_HEADER
            if (info.compressed) {
                if (compressedSize <= 0 || body + compressedSize > data.size) continue
                val room = minOf(info.maxPageSize, out.size - start)
                val chunk = DwgLz77.inflate(data, body, compressedSize, room)
                chunk.copyInto(out, start)
            } else {
                if (pageSize <= 0 || body + pageSize > data.size) continue
                val room = minOf(pageSize, out.size - start)
                data.copyInto(out, start, body, body + room)
            }
        }
        return out
    }

    // --- reading the fixed-width fields -------------------------------------

    private fun int32(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 4 > b.size) throw DwgTruncated()
        return (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
    }

    private fun int64(b: ByteArray, at: Int): Long {
        if (at < 0 || at + 8 > b.size) throw DwgTruncated()
        var value = 0L
        for (i in 7 downTo 0) value = (value shl 8) or (b[at + i].toLong() and 0xFF)
        return value
    }

    private fun textAt(b: ByteArray, at: Int, length: Int): String {
        if (at < 0 || at + length > b.size) return ""
        var end = at
        while (end < at + length && b[end] != 0.toByte()) end++
        return String(b, at, end - at, Charsets.US_ASCII)
    }

    private const val MAGIC = "AcFssFcAJMB"
    private const val DIRECTORY_AT = 0x80
    private const val DIRECTORY_BYTES = 0x6C
    private const val PAGE_BASE = 0x100L
    private const val PAGE_MAP_TYPE = 0x41630E3B
    private const val SECTION_MAP_TYPE = 0x4163003B
    private const val DATA_PAGE_TYPE = 0x4163043B
    private const val DATA_PAGE_KEY = 0x4164536B
    private const val SYSTEM_PAGE_HEADER = 20
    private const val DATA_PAGE_HEADER = 32
    private const val SECTION_MAP_HEADER = 20
    private const val SECTION_ENTRY_BYTES = 8 + 24 + 64
    private const val SECTION_NAME_BYTES = 64
    private const val COMPRESSED = 2
    private const val MAX_SYSTEM_PAGE = 1 shl 22
    private const val MAX_SECTIONS = 256
    private const val MAX_SECTION_PAGES = 4096
    private const val MAX_SECTION_PAGE_SIZE = 1 shl 24
    private const val MAX_SECTION_BYTES = 1L shl 31
    private const val OBJECTS_SECTION = "AcDb:AcDbObjects"
    private const val HANDLES_SECTION = "AcDb:Handles"
    private const val CLASSES_SECTION = "AcDb:Classes"
}
