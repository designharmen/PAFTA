package com.harmen.pafta.dwg

import com.harmen.pafta.dxf.BlockDefinition
import com.harmen.pafta.dxf.BlockExpansion
import com.harmen.pafta.dxf.COLOUR_BY_LAYER
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.DxfEntity
import com.harmen.pafta.dxf.DxfLayer
import com.harmen.pafta.dxf.DxfTextAlign
import com.harmen.pafta.dxf.unescapeMText
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3

/**
 * Reads AutoCAD's own drawing file.
 *
 * DWG is a closed format with no specification from its author; everything here
 * was worked out against real files written by AutoCAD, with the same drawings
 * exported to DXF as the answer sheet. That is also why the result is a
 * [DxfDrawing]: the geometry in the two files is the same geometry, so every
 * part of PAFTA that already draws, measures and snaps to a DXF drawing works
 * on a DWG without knowing one from the other.
 *
 * What this build reads is AutoCAD R13 up to AutoCAD 2000. From the 2004
 * release onwards the file is a set of compressed pages instead of a plain
 * table of contents, which is a separate reader; until it exists, such a file
 * is refused by name and by year rather than drawn half-right.
 */
public object DwgReader {

    public fun read(bytes: ByteArray): DwgOutcome {
        val version = DwgVersion.of(bytes) ?: return DwgOutcome.Failure(DwgFailure.NotADwg)
        if (!version.readable) return DwgOutcome.Failure(DwgFailure.UnsupportedVersion(version))
        return try {
            val sections = sections(bytes, version)
                ?: return DwgOutcome.Failure(DwgFailure.Damaged)
            DwgParse(sections, version).read()
        } catch (e: RuntimeException) {
            DwgOutcome.Failure(DwgFailure.Damaged)
        }
    }

    /**
     * The two parts of the drawing, however the file keeps them.
     *
     * Up to AutoCAD 2000 the objects are simply the file, and a table near the
     * end says where each one starts; from 2004 both are sections of a
     * compressed container. Past this point nothing cares which it was.
     */
    private fun sections(bytes: ByteArray, version: DwgVersion): DwgSections? {
        if (version.pagedContainer) return DwgContainer.read(bytes)
        val locators = legacyLocators(bytes) ?: return null
        val map = locators[OBJECT_MAP_RECORD] ?: return null
        val handles = slice(bytes, map) ?: return null
        return DwgSections(
            objects = bytes,
            handles = handles,
            classes = locators[CLASS_RECORD]?.let { slice(bytes, it) },
        )
    }

    private fun slice(bytes: ByteArray, at: IntArray): ByteArray? {
        val from = at[0]
        val to = at[0] + at[1]
        if (from < 0 || to > bytes.size || to <= from) return null
        return bytes.copyOfRange(from, to)
    }

    /**
     * The table of contents of a file up to AutoCAD 2000: a count, then one
     * nine-byte record per part saying what it is and where.
     */
    private fun legacyLocators(bytes: ByteArray): Map<Int, IntArray>? {
        if (bytes.size < LOCATOR_START + 9) return null
        fun int32(at: Int): Int = (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

        val count = int32(LOCATOR_COUNT_AT)
        if (count <= 0 || count > MAX_LOCATORS) return null
        val out = HashMap<Int, IntArray>()
        var at = LOCATOR_START
        repeat(count) {
            if (at + 9 > bytes.size) return out
            val id = bytes[at].toInt() and 0xFF
            val offset = int32(at + 1)
            val size = int32(at + 5)
            if (offset in 0 until bytes.size && size >= 0) out[id] = intArrayOf(offset, size)
            at += 9
        }
        return out
    }
}

/** An object's place in the file, before anything inside it is read. */
private class RawObject(
    val handle: Long,
    val offset: Int,
    val type: Int,
    /** Bit position just past the length field: what the bit size is counted from. */
    val afterSizeBit: Int,
    /** Bit position of the object's first field. */
    val bodyBit: Int,
    /** From the object's own header on AutoCAD 2000; -1 before that, where it comes later. */
    val headerBitSize: Int,
)

/**
 * An entity's common header: the part every entity has, whatever it draws.
 *
 * Several of these fields are flags whose only use is to say which handles
 * follow in the handle stream, which is why they are carried rather than read
 * and dropped.
 */
private class EntityHeader(
    val entityMode: Int,
    val reactors: Int,
    val noLinks: Boolean,
    val byLayerLineType: Boolean,
    val extensionDictionaryMissing: Boolean,
    val colour: Int,
    val colourIsHandle: Boolean,
    val lineTypeFlags: Int,
    val plotStyleFlags: Int,
    val materialFlags: Int,
    val shadowFlags: Int,
    val visualStyles: Int,
    val handleStreamBit: Int,
)

/** The objects an entity points at. */
private class EntityLinks(
    val owner: Long?,
    val layer: Long?,
    /** Whatever follows the layer, for the entities that point at more. */
    val extra: List<Long>,
)

/** A kind of object the file declares for itself, rather than one DWG fixes. */
private class DeclaredClass(val name: String, val isEntity: Boolean)

/** An entity as read, before block references are expanded. */
private class Placed(
    val entity: DxfEntity,
    val owner: Long?,
    val entityMode: Int,
)

private class DwgParse(sections: DwgSections, private val version: DwgVersion) {

    /** Every object in the drawing, back to back. */
    private val data: ByteArray = sections.objects

    /** The index into [data]: which handle starts where. */
    private val handleTable: ByteArray = sections.handles

    /** The object types the file declares for itself, if it says. */
    private val classTable: ByteArray? = sections.classes

    /** True for the AutoCAD 2000 layout, which moved several fields. */
    private val modern: Boolean = version.since2000

    private val typeCounts = LinkedHashMap<String, Int>()
    private val unsupported = LinkedHashSet<String>()
    private var declared: Map<Int, DeclaredClass> = emptyMap()

    fun read(): DwgOutcome {
        // Only for the older files, and only because they need it: before
        // AutoCAD 2004 the lightweight polyline most plans are drawn with is a
        // declared type, so without this table it is an unknown number. From
        // 2004 it is a fixed type again, and the table would buy nothing but
        // nicer names for the types this reader skips anyway — which is not
        // worth reading a differently-laid-out table wrong.
        declared = if (version.since2004) {
            emptyMap()
        } else {
            classTable?.let { declaredClasses(it) } ?: emptyMap()
        }

        val objects = ArrayList<RawObject>()
        for ((handle, offset) in objectMap()) {
            val o = try {
                header(handle, offset)
            } catch (e: RuntimeException) {
                null
            }
            if (o != null) objects += o
        }
        if (objects.isEmpty()) return DwgOutcome.Failure(DwgFailure.Damaged)
        objects.sortBy { it.offset }

        val typeByHandle = HashMap<Long, Int>(objects.size * 2)
        for (o in objects) typeByHandle[o.handle] = o.type

        val layerNames = HashMap<Long, String>()
        val blockNames = HashMap<Long, String>()
        val blockBases = HashMap<Long, Vec2>()
        for (o in objects) {
            when (o.type) {
                DwgObjectType.LAYER -> readName(o)?.let { layerNames[o.handle] = it }
                DwgObjectType.BLOCK_HEADER -> {
                    val block = readBlockHeader(o) ?: continue
                    blockNames[o.handle] = block.first
                    blockBases[o.handle] = block.second
                }
            }
        }

        val modelSpace = blockNames.entries
            .firstOrNull { it.value.equals(MODEL_SPACE, ignoreCase = true) }
            ?.key

        // A classic polyline keeps its points in separate objects, and every one
        // of them says which polyline owns it. Up to AutoCAD 2000 they also sat
        // directly behind it in the file, which is how the DXF reader finds
        // them; from 2004 they do not, so going by what they say is the only
        // way that works for every version.
        val verticesByOwner = HashMap<Long, MutableList<RawObject>>()
        for (o in objects) {
            if (!isVertex(o.type)) continue
            val owner = try {
                ownerOf(o)
            } catch (e: RuntimeException) {
                null
            } ?: continue
            verticesByOwner.getOrPut(owner) { ArrayList() } += o
        }

        val placed = ArrayList<Placed>()
        for (o in objects) {
            if (!isEntity(o.type)) continue
            typeCounts[typeName(o.type)] = (typeCounts[typeName(o.type)] ?: 0) + 1
            // Counted above, drawn as part of the polyline that owns them.
            if (isVertex(o.type) || o.type == DwgObjectType.SEQEND) continue

            if (isPolyline(o.type)) {
                val vertices = verticesByOwner[o.handle] ?: adjacentVertices(objects, o)
                try {
                    placed += readPolyline(o, vertices, layerNames)
                } catch (e: RuntimeException) {
                    unsupported += typeName(o.type)
                }
                continue
            }
            try {
                val one = readEntity(o, layerNames, blockNames, typeByHandle)
                if (one != null) placed += one else unsupported += typeName(o.type)
            } catch (e: RuntimeException) {
                unsupported += typeName(o.type)
            }
        }

        // A block's geometry is the entities that say the block owns them; the
        // drawing itself is the entities in model space, which say so either by
        // naming the model space block or by the shorthand for it that AutoCAD
        // 2000 writes instead.
        val blocks = HashMap<String, MutableList<DxfEntity>>()
        val model = ArrayList<DxfEntity>()
        for (p in placed) {
            val inModel = p.entityMode == ENTITY_MODE_MODEL_SPACE ||
                (p.owner != null && p.owner == modelSpace)
            if (inModel) {
                model += p.entity
                continue
            }
            val name = p.owner?.let { blockNames[it] } ?: continue
            if (name.equals(MODEL_SPACE, ignoreCase = true)) {
                model += p.entity
            } else {
                blocks.getOrPut(name) { ArrayList() } += p.entity
            }
        }

        val definitions = blocks.mapValues { (name, entities) ->
            val base = blockNames.entries.firstOrNull { it.value == name }
                ?.let { blockBases[it.key] }
                ?: Vec2.ZERO
            BlockDefinition(base = base, entities = entities)
        }

        val expansion = BlockExpansion(definitions)
        expansion.place(
            entities = model,
            transform = null,
            layerOverride = null,
            colourOverride = null,
            depth = 0,
        )

        if (expansion.out.isEmpty()) {
            return DwgOutcome.Failure(DwgFailure.NothingInIt(typeCounts.toMap()))
        }

        val layers = layerNames.values.distinct().map { DxfLayer(name = it) }
        return DwgOutcome.Success(
            DxfDrawing(
                layers = layers,
                entities = expansion.out.toList(),
                unsupportedEntityTypes = unsupported.toSet(),
                entityTypeCounts = typeCounts.toMap(),
                blockEntityCounts = blocks.mapValues { it.value.size },
                expansionTruncated = expansion.truncated,
            ),
        )
    }

    /**
     * The kinds of object the file declares for itself.
     *
     * DWG numbers the entity types it has always had — a line is 19 — but
     * everything added since R13 is declared in a table at the front of the
     * file, and its number is whatever that table gave it. The same polyline is
     * 535 in one file and 538 in another, which is why reading this table is
     * not optional: without it, the lightweight polyline that most plans are
     * drawn with is an unknown number in half the files in the world.
     */
    private fun declaredClasses(table: ByteArray): Map<Int, DeclaredClass> {
        val out = HashMap<Int, DeclaredClass>()
        try {
            val sentinel = indexOfSentinel(table)
            if (sentinel < 0) return out
            val start = sentinel + CLASS_SENTINEL.size
            if (start + 4 > table.size) return out
            val r = DwgBitReader(table, start * 8)
            val size = r.rawLong()
            if (size <= 0 || start + 4 + size > table.size) return out
            val end = (start + 4 + size) * 8
            while (r.position < end - 8 && out.size < MAX_DECLARED_CLASSES) {
                val number = r.bitShort()
                r.bitShort()
                r.text()
                r.text()
                val name = r.text()
                r.boolean()
                val itemClass = r.bitShort()
                if (number >= FIRST_DECLARED_CLASS && name.isNotEmpty()) {
                    out[number] = DeclaredClass(
                        name = name.uppercase(java.util.Locale.ROOT),
                        isEntity = itemClass == DECLARED_ENTITY,
                    )
                }
            }
        } catch (e: RuntimeException) {
            return out
        }
        return out
    }

    /**
     * Where the list of declared types starts.
     *
     * The section is introduced by sixteen fixed bytes rather than by its
     * length, so it is found by looking for them.
     */
    private fun indexOfSentinel(table: ByteArray): Int {
        outer@ for (at in 0..table.size - CLASS_SENTINEL.size) {
            for (i in CLASS_SENTINEL.indices) {
                if (table[at + i] != CLASS_SENTINEL[i]) continue@outer
            }
            return at
        }
        return -1
    }

    /** What the file calls this type of object. */
    private fun typeName(code: Int): String =
        if (code < FIRST_DECLARED_CLASS) DwgObjectType.name(code)
        else declared[code]?.name ?: code.toString()

    private fun isEntity(code: Int): Boolean =
        if (code < FIRST_DECLARED_CLASS) DwgObjectType.isEntity(code)
        else declared[code]?.isEntity == true

    /**
     * The fixed type code to read a declared class as.
     *
     * A declared class is read only when PAFTA knows its geometry by name. The
     * rest are counted in the inventory and left alone, which is the honest
     * answer for a table or a point cloud.
     */
    private fun effectiveType(code: Int): Int = when {
        code < FIRST_DECLARED_CLASS -> code
        declared[code]?.name == "LWPOLYLINE" -> DwgObjectType.LWPOLYLINE
        else -> UNKNOWN_TYPE
    }

    /**
     * Every object in the file, by handle, with where it starts.
     *
     * The list is kept in runs, and each run's first entry is counted from zero
     * while the rest are counted from the entry before — so a run that is read
     * as though it continued the previous one lands on nothing. Each run also
     * ends with two bytes of checksum that are not part of it.
     */
    private fun objectMap(): LinkedHashMap<Long, Int> {
        val table = handleTable
        val out = LinkedHashMap<Long, Int>()
        var at = 0
        while (at + 2 <= table.size) {
            val runSize = ((table[at].toInt() and 0xFF) shl 8) or (table[at + 1].toInt() and 0xFF)
            if (runSize <= 2) break
            val runEnd = at + runSize
            if (runEnd > table.size) break
            var handle = 0L
            var location = 0L
            var p = at + 2
            while (p < runEnd) {
                val handleStep = unsignedModular(table, p)
                val locationStep = signedModular(table, handleStep.second)
                handle += handleStep.first
                location += locationStep.first
                p = locationStep.second
                if (location > 0 && location < data.size) out[handle] = location.toInt()
            }
            at = runEnd + 2
        }
        return out
    }

    private fun unsignedModular(from: ByteArray, start: Int): Pair<Long, Int> {
        var at = start
        var value = 0L
        var shift = 0
        while (true) {
            if (at >= from.size || shift > 56) throw DwgTruncated()
            val b = from[at].toInt() and 0xFF
            at++
            value = value or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if (b and 0x80 == 0) return value to at
        }
    }

    private fun signedModular(from: ByteArray, start: Int): Pair<Long, Int> {
        var at = start
        var value = 0L
        var shift = 0
        while (true) {
            if (at >= from.size || shift > 56) throw DwgTruncated()
            var b = from[at].toInt() and 0xFF
            at++
            if (b and 0x80 != 0) {
                value = value or ((b and 0x7F).toLong() shl shift)
                shift += 7
            } else {
                var negative = false
                if (b and 0x40 != 0) {
                    negative = true
                    b = b and 0xBF
                }
                value = value or ((b and 0x7F).toLong() shl shift)
                return (if (negative) -value else value) to at
            }
        }
    }

    /**
     * An object's header, or null when the object is not where the table said.
     *
     * Every object repeats its own handle a few fields in. Checking it against
     * the handle the table gave is what makes a misread table harmless: an
     * entry pointing into the middle of something else is dropped instead of
     * being drawn as nonsense.
     */
    private fun header(handle: Long, offset: Int): RawObject? {
        val r = DwgBitReader(data, offset * 8)
        val size = r.modularShort()
        // From AutoCAD 2010 the object states how many of its bits are handles
        // instead of how many are data, and this field is not counted in its
        // own size. Everything after it is measured from here.
        val handleBits = if (version.since2010) r.unsignedModularChar() else 0L
        val afterSize = r.position
        if (size <= 0 || afterSize / 8 + size > data.size) return null
        val type = if (version.since2010) r.objectType() else r.bitShort()
        val bitSize = when {
            version.since2010 -> (size.toLong() * 8 - handleBits).toInt()
            version.since2000 -> r.rawLong()
            else -> -1
        }
        if (version.since2000 && (bitSize <= 0 || bitSize > size * 8 + 16)) return null
        val own = r.handle()
        if (own.code != 0 || own.value != handle) return null
        return RawObject(handle, offset, type, afterSize, r.position, bitSize)
    }

    // --- the parts every object starts with ---------------------------------

    /**
     * Skips the extended data an application may have attached.
     *
     * It is a list of blocks, each with its length in front, ending at a length
     * of zero. PAFTA has no use for it, but it sits between the handle and the
     * geometry, so it has to be walked past exactly.
     */
    private fun skipExtendedData(r: DwgBitReader) {
        var guard = 0
        while (true) {
            val size = r.bitShort()
            if (size <= 0) return
            r.handle()
            if (!r.has(size * 8)) throw DwgTruncated()
            r.skip(size * 8)
            if (++guard > MAX_EXTENDED_DATA_BLOCKS) throw DwgMalformedValue()
        }
    }

    /** Positions [r] at the body of a table object, past the parts it shares. */
    private fun tableBody(o: RawObject): DwgBitReader {
        val r = DwgBitReader(data, o.bodyBit)
        skipExtendedData(r)
        if (!version.since2000) r.rawLong()
        r.bitLong()
        if (version.since2004) r.boolean()
        if (version.since2013) r.boolean()
        return r
    }

    private fun readName(o: RawObject): String? = try {
        val r = tableBody(o)
        text(r, stringStream(o)).takeIf { it.isNotEmpty() }
    } catch (e: RuntimeException) {
        null
    }

    private fun readBlockHeader(o: RawObject): Pair<String, Vec2>? = try {
        val r = tableBody(o)
        val name = text(r, stringStream(o))
        if (name.isEmpty()) throw DwgMalformedValue()
        // Every table entry says whether it came from a referenced drawing.
        // AutoCAD 2007 dropped two of the three fields that said it, and the
        // two bits they cost are enough to read the base point below out of
        // the middle of something else.
        if (!version.since2007) r.boolean()
        r.bitShort()
        if (!version.since2007) r.boolean()
        r.boolean()
        r.boolean()
        val isReference = r.boolean()
        val isOverlaid = r.boolean()
        if (version.since2000) r.boolean()
        if (version.since2004 && !isReference && !isOverlaid) r.bitLong()
        val x = r.bitDouble()
        val y = r.bitDouble()
        r.bitDouble()
        // A base point read from the wrong bit would move every copy of the
        // block somewhere absurd, and a block drawn around the origin — which
        // is nearly all of them — is indistinguishable from no base point at
        // all. So an implausible one is treated as none.
        val base =
            if (x.isFinite() && y.isFinite() && kotlin.math.abs(x) < MAX_COORDINATE &&
                kotlin.math.abs(y) < MAX_COORDINATE
            ) {
                Vec2(x, y)
            } else {
                Vec2.ZERO
            }
        name to base
    } catch (e: RuntimeException) {
        null
    }

    private fun entityHeader(
        o: RawObject,
        r: DwgBitReader,
        strings: DwgBitReader?,
    ): EntityHeader {
        skipExtendedData(r)
        if (r.boolean()) {
            // A thumbnail of the entity, which PAFTA draws itself and does not
            // need. From AutoCAD 2010 its length is written differently.
            val size = if (version.since2010) r.bitLongLong() else r.rawLong().toLong()
            if (size < 0 || size > data.size) throw DwgTruncated()
            val bits = (size * 8).toInt()
            if (!r.has(bits)) throw DwgTruncated()
            r.skip(bits)
        }
        val bitSize = if (version.since2000) o.headerBitSize else r.rawLong()
        if (bitSize <= 0 || o.afterSizeBit + bitSize > r.bitLength) throw DwgTruncated()

        val entityMode = r.twoBits()
        val reactors = r.bitLong()
        // One bit, three meanings, depending on the release. Up to R14 it says
        // the line type is the layer's; from 2004 it says the entity has no
        // extension dictionary; in between, and up to 2002, it says the entity
        // is not linked to its neighbours. Getting this wrong puts every later
        // field one bit out.
        var byLayerLineType = false
        var extensionDictionaryMissing = false
        var noLinks = true
        if (!version.since2000) byLayerLineType = r.boolean()
        if (version.since2004) extensionDictionaryMissing = r.boolean()
        if (!version.since2004) noLinks = r.boolean()
        if (version.since2013) r.boolean()

        var colour = COLOUR_BY_LAYER
        var colourIsHandle = false
        if (version.since2004) {
            // 2004 replaced the colour index with an index plus flags saying
            // what else was written: true colour, transparency, a named colour
            // from a book, or a reference to a colour object.
            val raw = r.bitShort()
            val flags = (raw ushr 8) and 0xFF
            colour = raw and 0x1FF
            if (flags and 0x20 != 0) r.bitLong()
            if (flags and 0x40 != 0) {
                colourIsHandle = true
            } else if (flags and 0x80 != 0) {
                r.bitLong()
            }
            if (flags and 0x41 == 0x41) text(r, strings)
            if (flags and 0x42 == 0x42) text(r, strings)
        } else {
            colour = r.bitShort()
        }

        r.bitDouble()
        var lineTypeFlags = 0
        var plotStyleFlags = 0
        var materialFlags = 0
        var shadowFlags = 0
        if (version.since2000) {
            lineTypeFlags = r.twoBits()
            if (version.since2007) {
                materialFlags = r.twoBits()
                plotStyleFlags = r.twoBits()
                shadowFlags = r.rawChar()
            } else {
                plotStyleFlags = r.twoBits()
            }
        }
        var visualStyles = 0
        if (version.since2010) {
            if (r.boolean()) visualStyles++
            if (r.boolean()) visualStyles++
            if (r.boolean()) visualStyles++
        }
        r.bitShort()
        if (version.since2000) r.rawChar()

        return EntityHeader(
            entityMode = entityMode,
            reactors = reactors,
            noLinks = noLinks,
            byLayerLineType = byLayerLineType,
            extensionDictionaryMissing = extensionDictionaryMissing,
            colour = colour,
            colourIsHandle = colourIsHandle,
            lineTypeFlags = lineTypeFlags,
            plotStyleFlags = plotStyleFlags,
            materialFlags = materialFlags,
            shadowFlags = shadowFlags,
            visualStyles = visualStyles,
            handleStreamBit = o.afterSizeBit + bitSize,
        )
    }

    /**
     * Where an object keeps its text, from AutoCAD 2007 on.
     *
     * That release moved every string out of the middle of the object and into
     * a run at its end, as Unicode. The run's length is written backwards from
     * the object's last bit, which is also where a single bit says whether
     * there is one at all.
     */
    private fun stringStream(o: RawObject): DwgBitReader? {
        if (!version.since2007 || o.headerBitSize <= 0) return null
        val last = o.afterSizeBit + o.headerBitSize - 1
        if (last <= o.bodyBit || last >= data.size * 8) return null
        if (DwgBitReader(data, last).bit() == 0) return null
        if (last - 32 < o.bodyBit) return null
        var size = DwgBitReader(data, last - 16).rawShort()
        val begin: Int
        if (size and 0x8000 != 0) {
            val high = DwgBitReader(data, last - 32).rawShort()
            size = (size and 0x7FFF) or (high shl 15)
            begin = last - 32 - size
        } else {
            begin = last - 16 - size
        }
        if (begin < o.bodyBit || begin >= last) return null
        return DwgBitReader(data, begin)
    }

    /**
     * One string, from wherever this release keeps them.
     *
     * Before 2007 a string sits where it is used; from 2007 nothing at all is
     * written in its place, so an object with no text run has no text.
     */
    private fun text(r: DwgBitReader, strings: DwgBitReader?): String = when {
        version.since2007 -> strings?.unicodeText() ?: ""
        else -> r.text()
    }

    /**
     * The handles an entity carries, which sit past the end of its geometry.
     *
     * Their order changed with AutoCAD 2000: before it the layer came first and
     * the neighbouring entities after, and from it the other way round.
     * Anything past the layer is returned as a list rather than named, because
     * which handles follow depends on flags read earlier — and the one entity
     * that needs one of them, a block reference, can say which it wants by what
     * kind of object it points at.
     */
    private fun links(o: RawObject, h: EntityHeader): EntityLinks {
        val r = DwgBitReader(data, h.handleStreamBit)
        // A colour that refers to a colour object is the one handle read before
        // the owner, because the colour is part of the data the handles follow.
        if (h.colourIsHandle) r.handle()
        val owner = if (h.entityMode == 0) resolve(r.handle(), o.handle) else null
        repeat(h.reactors.coerceIn(0, MAX_REACTORS)) { r.handle() }
        if (!h.extensionDictionaryMissing) r.handle()
        val layer: Long?
        if (version.since2000) {
            if (!h.noLinks) {
                r.handle()
                r.handle()
            }
            layer = resolve(r.handle(), o.handle)
            if (h.lineTypeFlags == 3) r.handle()
            if (version.since2007) {
                if (h.materialFlags == 3) r.handle()
                if (h.shadowFlags == 3) r.handle()
            }
            if (h.plotStyleFlags == 3) r.handle()
            repeat(h.visualStyles) { r.handle() }
        } else {
            layer = resolve(r.handle(), o.handle)
            if (!h.byLayerLineType) r.handle()
            if (!h.noLinks) {
                r.handle()
                r.handle()
            }
        }
        val extra = ArrayList<Long>()
        for (i in 0 until MAX_TRAILING_HANDLES) {
            val ref = try {
                r.handle()
            } catch (e: RuntimeException) {
                break
            }
            resolve(ref, o.handle)?.let { extra += it }
        }
        return EntityLinks(owner, layer, extra)
    }

    /**
     * A handle reference as an actual handle.
     *
     * Most references are written out in full, but a reference to a neighbour is
     * written as the step from the referring object's own handle — which is how
     * a chain of a thousand lines each pointing at the next costs one byte
     * apiece.
     */
    private fun resolve(ref: DwgHandleRef, own: Long): Long? = when (ref.code) {
        2, 3, 4, 5 -> ref.value.takeIf { it > 0 }
        6 -> own + 1
        8 -> own - 1
        0xA -> own + ref.value
        0xC -> own - ref.value
        else -> null
    }

    // --- the entities themselves --------------------------------------------

    private fun readEntity(
        o: RawObject,
        layerNames: Map<Long, String>,
        blockNames: Map<Long, String>,
        typeByHandle: Map<Long, Int>,
    ): Placed? {
        val r = DwgBitReader(data, o.bodyBit)
        val strings = stringStream(o)
        val h = entityHeader(o, r, strings)
        val l = links(o, h)
        val layer = layerNames[l.layer] ?: DEFAULT_LAYER
        val colour = if (h.colour == 0 || h.colour > 255) COLOUR_BY_LAYER else h.colour

        val entity: DxfEntity = when (effectiveType(o.type)) {
            DwgObjectType.LINE -> {
                val (start, end) = readLine(r)
                DxfEntity.Line(layer, start, end, colour)
            }
            DwgObjectType.POINT -> DxfEntity.Point(layer, point3(r), colour)
            DwgObjectType.CIRCLE -> {
                val centre = point3(r)
                DxfEntity.Circle(layer, centre, r.bitDouble(), colour)
            }
            DwgObjectType.ARC -> {
                val centre = point3(r)
                val radius = r.bitDouble()
                r.bitThickness()
                r.bitExtrusion()
                val from = Math.toDegrees(r.bitDouble())
                val to = Math.toDegrees(r.bitDouble())
                DxfEntity.Arc(layer, centre, radius, from, to, colour)
            }
            DwgObjectType.ELLIPSE -> readEllipse(r, layer, colour)
            DwgObjectType.FACE_3D -> readFace3d(r, layer, colour) ?: return null
            DwgObjectType.SOLID, DwgObjectType.TRACE ->
                readSolid(r, layer, colour) ?: return null
            DwgObjectType.LWPOLYLINE -> readLwPolyline(r, layer, colour)
            DwgObjectType.TEXT -> readText(r, strings, layer, colour)
            DwgObjectType.MTEXT -> readMText(r, strings, layer, colour)
            DwgObjectType.INSERT -> {
                val block = l.extra.firstOrNull {
                    typeByHandle[it] == DwgObjectType.BLOCK_HEADER
                }
                val name = block?.let { blockNames[it] } ?: return null
                readInsert(r, layer, colour, name)
            }
            else -> return null
        }
        return Placed(entity, l.owner, h.entityMode)
    }

    private fun point3(r: DwgBitReader): Vec3 = Vec3(r.bitDouble(), r.bitDouble(), r.bitDouble())

    /**
     * A line's two ends.
     *
     * AutoCAD 2000 writes them interleaved — both x values, then both y values —
     * so that the second end can be stored as the difference from the first,
     * which for a line in a wall is a couple of bytes instead of twenty-four.
     */
    private fun readLine(r: DwgBitReader): Pair<Vec3, Vec3> {
        if (!version.since2000) return point3(r) to point3(r)
        val flat = r.boolean()
        val x1 = r.rawDouble()
        val x2 = r.bitDoubleWithDefault(x1)
        val y1 = r.rawDouble()
        val y2 = r.bitDoubleWithDefault(y1)
        var z1 = 0.0
        var z2 = 0.0
        if (!flat) {
            z1 = r.rawDouble()
            z2 = r.bitDoubleWithDefault(z1)
        }
        return Vec3(x1, y1, z1) to Vec3(x2, y2, z2)
    }

    /**
     * An ellipse, drawn as the ring of points it passes through.
     *
     * PAFTA's drawing model has no ellipse, and an ellipse that a reader skipped
     * would be a hole in the plan. Its own description — a centre, the vector to
     * one end of the long axis, and how squat it is — turns into points exactly.
     */
    private fun readEllipse(r: DwgBitReader, layer: String, colour: Int): DxfEntity {
        val centre = point3(r)
        val major = point3(r)
        point3(r)
        val ratio = r.bitDouble()
        val from = r.bitDouble()
        val to = r.bitDouble()
        val majorLength = kotlin.math.hypot(major.x, major.y)
        val minorLength = majorLength * ratio
        val tilt = kotlin.math.atan2(major.y, major.x)
        val sweep = if (to > from) to - from else to - from + 2.0 * Math.PI
        val closed = sweep >= 2.0 * Math.PI - 1e-9
        val steps = ELLIPSE_SEGMENTS
        val points = ArrayList<Vec2>(steps + 1)
        for (i in 0..steps) {
            val at = from + sweep * i / steps
            val x = kotlin.math.cos(at) * majorLength
            val y = kotlin.math.sin(at) * minorLength
            points += Vec2(
                centre.x + x * kotlin.math.cos(tilt) - y * kotlin.math.sin(tilt),
                centre.y + x * kotlin.math.sin(tilt) + y * kotlin.math.cos(tilt),
            )
        }
        return DxfEntity.Polyline(layer, points, closed = closed, colour = colour)
    }

    /**
     * A flat face: four corners anywhere in space.
     *
     * This is the entity a 3D model is made of, and AutoCAD 2000 stores the
     * corners after the first as differences from the one before, because the
     * corners of one face are close together by definition.
     */
    private fun readFace3d(r: DwgBitReader, layer: String, colour: Int): DxfEntity? {
        val corners: List<Vec3>
        if (version.since2000) {
            val noFlags = r.boolean()
            val flat = r.boolean()
            val x = r.rawDouble()
            val y = r.rawDouble()
            val z = if (flat) 0.0 else r.rawDouble()
            val first = Vec3(x, y, z)
            val second = next3(r, first)
            val third = next3(r, second)
            val fourth = next3(r, third)
            if (!noFlags) r.bitShort()
            corners = listOf(first, second, third, fourth)
        } else {
            corners = listOf(point3(r), point3(r), point3(r), point3(r))
            r.bitShort()
        }
        return face(corners, layer, colour)
    }

    private fun next3(r: DwgBitReader, from: Vec3): Vec3 = Vec3(
        r.bitDoubleWithDefault(from.x),
        r.bitDoubleWithDefault(from.y),
        r.bitDoubleWithDefault(from.z),
    )

    /**
     * A filled quadrilateral.
     *
     * Its corners are numbered across, not around, so the last two are swapped
     * into an outline that does not cross itself — the same correction the DXF
     * reader makes for the same entity.
     */
    private fun readSolid(r: DwgBitReader, layer: String, colour: Int): DxfEntity? {
        r.bitThickness()
        val z = r.bitDouble()
        val read = ArrayList<Vec3>(4)
        repeat(4) { read += Vec3(r.rawDouble(), r.rawDouble(), z) }
        return face(listOf(read[0], read[1], read[3], read[2]), layer, colour)
    }

    private fun face(corners: List<Vec3>, layer: String, colour: Int): DxfEntity? {
        val trimmed = if (corners.size == 4 && corners[3].nearly(corners[2])) {
            corners.subList(0, 3)
        } else {
            corners
        }
        if (trimmed.size < 3) return null
        if (trimmed.any { !it.finite() }) return null
        return DxfEntity.Face(layer, trimmed.toList(), colour)
    }

    private fun readLwPolyline(r: DwgBitReader, layer: String, colour: Int): DxfEntity {
        val flags = r.bitShort()
        if (flags and LW_CONST_WIDTH != 0) r.bitDouble()
        if (flags and LW_ELEVATION != 0) r.bitDouble()
        if (flags and LW_THICKNESS != 0) r.bitDouble()
        if (flags and LW_EXTRUSION != 0) {
            r.bitDouble()
            r.bitDouble()
            r.bitDouble()
        }
        val count = r.bitLong()
        val bulges = if (flags and LW_BULGES != 0) r.bitLong() else 0
        val vertexIds =
            if (version.since2010 && flags and LW_VERTEX_IDS != 0) r.bitLong() else 0
        val widths = if (flags and LW_WIDTHS != 0) r.bitLong() else 0
        if (count < 0 || count > MAX_VERTICES) throw DwgMalformedValue()
        // Before AutoCAD 2000 every vertex is written out in full; from it,
        // each one after the first is the difference from the one before.
        val points = ArrayList<Vec2>(count)
        for (i in 0 until count) {
            points += if (i == 0 || !version.since2000) {
                Vec2(r.rawDouble(), r.rawDouble())
            } else {
                val last = points[i - 1]
                Vec2(r.bitDoubleWithDefault(last.x), r.bitDoubleWithDefault(last.y))
            }
        }
        repeat(bulges.coerceIn(0, MAX_VERTICES)) { r.bitDouble() }
        repeat(vertexIds.coerceIn(0, MAX_VERTICES)) { r.bitLong() }
        repeat(widths.coerceIn(0, MAX_VERTICES)) {
            r.bitDouble()
            r.bitDouble()
        }
        return DxfEntity.Polyline(
            layer = layer,
            vertices = points.filter { it.x.isFinite() && it.y.isFinite() },
            closed = flags and LW_CLOSED != 0,
            colour = colour,
        )
    }

    /**
     * A single line of text.
     *
     * The one byte of flags in front says which of its properties are at their
     * usual value and therefore absent — a text at no angle, at normal width,
     * aligned the normal way costs nothing for any of them. AutoCAD 2000 also
     * stores the sizes and angles as plain numbers here rather than shortened
     * ones, which earlier versions do not.
     */
    private fun readText(
        r: DwgBitReader,
        strings: DwgBitReader?,
        layer: String,
        colour: Int,
    ): DxfEntity {
        val position: Vec3
        val height: Double
        val rotation: Double
        val value: String
        val alignCode: Int
        if (version.since2000) {
            val flags = r.rawChar()
            val elevation = if (flags and 0x01 != 0) 0.0 else r.rawDouble()
            val x = r.rawDouble()
            val y = r.rawDouble()
            if (flags and 0x02 == 0) {
                r.bitDoubleWithDefault(x)
                r.bitDoubleWithDefault(y)
            }
            r.bitExtrusion()
            r.bitThickness()
            if (flags and 0x04 == 0) r.rawDouble()
            rotation = if (flags and 0x08 != 0) 0.0 else r.rawDouble()
            height = r.rawDouble()
            if (flags and 0x10 == 0) r.rawDouble()
            value = text(r, strings)
            if (flags and 0x20 == 0) r.bitShort()
            alignCode = if (flags and 0x40 != 0) 0 else r.bitShort()
            position = Vec3(x, y, elevation)
        } else {
            val elevation = r.bitDouble()
            val x = r.rawDouble()
            val y = r.rawDouble()
            r.rawDouble()
            r.rawDouble()
            point3(r)
            r.bitDouble()
            r.bitDouble()
            rotation = r.bitDouble()
            height = r.bitDouble()
            r.bitDouble()
            value = text(r, strings)
            r.bitShort()
            alignCode = r.bitShort()
            position = Vec3(x, y, elevation)
        }
        return DxfEntity.Text(
            layer = layer,
            position = position,
            height = if (height.isFinite() && height > 0.0) height else DEFAULT_TEXT_HEIGHT,
            value = unescapeMText(value, isMText = false),
            rotationDegrees = Math.toDegrees(rotation),
            align = when (alignCode) {
                1, 4 -> DxfTextAlign.CENTRE
                2 -> DxfTextAlign.RIGHT
                else -> DxfTextAlign.LEFT
            },
            colour = colour,
        )
    }

    /** A paragraph of text. Its rotation is a direction vector, not an angle. */
    private fun readMText(
        r: DwgBitReader,
        strings: DwgBitReader?,
        layer: String,
        colour: Int,
    ): DxfEntity {
        val position = point3(r)
        point3(r)
        val axis = point3(r)
        r.bitDouble()
        if (version.since2007) r.bitDouble()
        val height = r.bitDouble()
        r.bitShort()
        r.bitShort()
        r.bitDouble()
        r.bitDouble()
        val value = text(r, strings)
        return DxfEntity.Text(
            layer = layer,
            position = position,
            height = if (height.isFinite() && height > 0.0) height else DEFAULT_TEXT_HEIGHT,
            value = unescapeMText(value, isMText = true),
            rotationDegrees = Math.toDegrees(kotlin.math.atan2(axis.y, axis.x)),
            align = DxfTextAlign.LEFT,
            colour = colour,
        )
    }

    private fun readInsert(
        r: DwgBitReader,
        layer: String,
        colour: Int,
        blockName: String,
    ): DxfEntity {
        val position = point3(r)
        val scale: Vec3
        if (version.since2000) {
            scale = when (r.twoBits()) {
                0 -> {
                    val x = r.rawDouble()
                    Vec3(x, r.bitDoubleWithDefault(x), r.bitDoubleWithDefault(x))
                }
                1 -> Vec3(1.0, r.bitDoubleWithDefault(1.0), r.bitDoubleWithDefault(1.0))
                2 -> r.rawDouble().let { Vec3(it, it, it) }
                else -> Vec3(1.0, 1.0, 1.0)
            }
        } else {
            scale = point3(r)
        }
        val rotation = r.bitDouble()
        return DxfEntity.Insert(
            layer = layer,
            blockName = blockName,
            position = position,
            scale = Vec3(
                scale.x.orOne(),
                scale.y.orOne(),
                scale.z.orOne(),
            ),
            rotationDegrees = Math.toDegrees(rotation),
            colour = colour,
        )
    }

    // --- the polyline family ------------------------------------------------

    /** Which object says it owns this one, if it says. */
    private fun ownerOf(o: RawObject): Long? {
        val r = DwgBitReader(data, o.bodyBit)
        val h = entityHeader(o, r, stringStream(o))
        if (h.entityMode != 0) return null
        val stream = DwgBitReader(data, h.handleStreamBit)
        if (h.colourIsHandle) stream.handle()
        return resolve(stream.handle(), o.handle)
    }

    /**
     * The vertices directly behind [polyline] in the file.
     *
     * The fallback for a file whose vertices do not name their owner: up to
     * AutoCAD 2000 they are written in a run behind the polyline, ending at a
     * marker.
     */
    private fun adjacentVertices(objects: List<RawObject>, polyline: RawObject): List<RawObject> {
        val from = objects.indexOf(polyline)
        if (from < 0) return emptyList()
        val out = ArrayList<RawObject>()
        var i = from + 1
        while (i < objects.size && isVertex(objects[i].type)) {
            out += objects[i]
            i++
        }
        return out
    }

    private fun isPolyline(type: Int): Boolean = type == DwgObjectType.POLYLINE_2D ||
        type == DwgObjectType.POLYLINE_3D ||
        type == DwgObjectType.POLYLINE_PFACE ||
        type == DwgObjectType.POLYLINE_MESH

    private fun isVertex(type: Int): Boolean = type in DwgObjectType.VERTEX_2D..DwgObjectType.VERTEX_PFACE_FACE

    private fun readPolyline(
        o: RawObject,
        vertices: List<RawObject>,
        layerNames: Map<Long, String>,
    ): List<Placed> {
        val r = DwgBitReader(data, o.bodyBit)
        val h = entityHeader(o, r, stringStream(o))
        val l = links(o, h)
        val layer = layerNames[l.layer] ?: DEFAULT_LAYER
        val colour = if (h.colour == 0 || h.colour > 255) COLOUR_BY_LAYER else h.colour

        var closed = false
        if (o.type == DwgObjectType.POLYLINE_2D) {
            closed = r.bitShort() and 1 != 0
        } else if (o.type == DwgObjectType.POLYLINE_3D) {
            r.rawChar()
            closed = r.rawChar() and 1 != 0
        }

        val points = ArrayList<Vec3>()
        val faces = ArrayList<List<Int>>()
        for (v in vertices.sortedBy { it.offset }) {
            val vr = DwgBitReader(data, v.bodyBit)
            try {
                entityHeader(v, vr, stringStream(v))
                if (v.type == DwgObjectType.VERTEX_PFACE_FACE) {
                    faces += listOf(vr.bitShort(), vr.bitShort(), vr.bitShort(), vr.bitShort())
                } else {
                    vr.rawChar()
                    points += point3(vr)
                }
            } catch (e: RuntimeException) {
                unsupported += typeName(v.type)
            }
        }

        if (faces.isNotEmpty()) {
            // A mesh: the vertices are a shared pool and each face names four
            // of them by number, counting from one, with a repeat standing for
            // a triangle.
            val out = ArrayList<Placed>(faces.size)
            for (f in faces) {
                val corners = f
                    .map { kotlin.math.abs(it) }
                    .filter { it in 1..points.size }
                    .map { points[it - 1] }
                val entity = face(corners, layer, colour) ?: continue
                out += Placed(entity, l.owner, h.entityMode)
            }
            return out
        }

        val flat = points.filter { it.finite() }.map { it.toVec2() }
        if (flat.size < 2) return emptyList()
        return listOf(
            Placed(
                DxfEntity.Polyline(layer, flat, closed = closed, colour = colour),
                l.owner,
                h.entityMode,
            ),
        )
    }
}

private fun Vec3.finite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Vec3.nearly(other: Vec3): Boolean =
    kotlin.math.abs(x - other.x) < 1e-9 &&
        kotlin.math.abs(y - other.y) < 1e-9 &&
        kotlin.math.abs(z - other.z) < 1e-9

private fun Double.orOne(): Double = if (isFinite() && this != 0.0) this else 1.0

/** The file's own table of contents puts the list of objects in this slot. */
private const val OBJECT_MAP_RECORD = 2
private const val CLASS_RECORD = 1
private val CLASS_SENTINEL: ByteArray = byteArrayOf(
    0x8D.toByte(), 0xA1.toByte(), 0xC4.toByte(), 0xB8.toByte(),
    0xC4.toByte(), 0xA9.toByte(), 0xF8.toByte(), 0xC5.toByte(),
    0xC0.toByte(), 0xDC.toByte(), 0xF4.toByte(), 0x5F.toByte(),
    0xE7.toByte(), 0xCF.toByte(), 0xB6.toByte(), 0x8A.toByte(),
)
private const val FIRST_DECLARED_CLASS = 500
private const val DECLARED_ENTITY = 0x1F2
private const val MAX_DECLARED_CLASSES = 2048
private const val UNKNOWN_TYPE = -1
private const val LOCATOR_COUNT_AT = 0x15
private const val LOCATOR_START = 0x19
private const val MAX_LOCATORS = 64
private const val MAX_EXTENDED_DATA_BLOCKS = 1024
private const val MAX_REACTORS = 1024
private const val MAX_TRAILING_HANDLES = 8
private const val MAX_VERTICES = 200_000
private const val MAX_COORDINATE = 1e12
private const val DEFAULT_TEXT_HEIGHT = 2.5
private const val DEFAULT_LAYER = "0"
private const val MODEL_SPACE = "*Model_Space"
private const val ENTITY_MODE_MODEL_SPACE = 2
private const val ELLIPSE_SEGMENTS = 64

private const val LW_EXTRUSION = 0x1
private const val LW_THICKNESS = 0x2
private const val LW_CONST_WIDTH = 0x4
private const val LW_ELEVATION = 0x8
private const val LW_BULGES = 0x10
private const val LW_WIDTHS = 0x20
private const val LW_CLOSED = 0x200
private const val LW_VERTEX_IDS = 0x400
