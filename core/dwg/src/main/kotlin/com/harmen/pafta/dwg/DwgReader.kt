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
        if (!version.readable) return DwgOutcome.Failure(DwgFailure.TooNew(version))
        return try {
            DwgParse(bytes, version).read()
        } catch (e: RuntimeException) {
            DwgOutcome.Failure(DwgFailure.Damaged)
        }
    }
}

/** Where one part of the file begins and how long it is. */
private class Locator(val offset: Int, val size: Int)

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

/** An entity's common header: the part every entity has, whatever it draws. */
private class EntityHeader(
    val entityMode: Int,
    val reactors: Int,
    val noLinks: Boolean,
    val colour: Int,
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

private class DwgParse(private val data: ByteArray, private val version: DwgVersion) {

    /** True for the AutoCAD 2000 layout, which moved several fields. */
    private val modern: Boolean = version.ordinal >= DwgVersion.R2000.ordinal

    private val typeCounts = LinkedHashMap<String, Int>()
    private val unsupported = LinkedHashSet<String>()
    private var declared: Map<Int, DeclaredClass> = emptyMap()

    fun read(): DwgOutcome {
        val locators = locators() ?: return DwgOutcome.Failure(DwgFailure.Damaged)
        val map = locators[OBJECT_MAP_RECORD]
            ?: return DwgOutcome.Failure(DwgFailure.Damaged)
        declared = locators[CLASS_RECORD]?.let { declaredClasses(it) } ?: emptyMap()

        val objects = ArrayList<RawObject>()
        for ((handle, offset) in objectMap(map)) {
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

        val placed = ArrayList<Placed>()
        var index = 0
        while (index < objects.size) {
            val o = objects[index]
            if (!isEntity(o.type)) {
                index++
                continue
            }
            typeCounts[typeName(o.type)] = (typeCounts[typeName(o.type)] ?: 0) + 1

            if (isPolyline(o.type)) {
                val run = polylineRun(objects, index)
                try {
                    placed += readPolyline(o, run, layerNames, typeCounts)
                } catch (e: RuntimeException) {
                    unsupported += typeName(o.type)
                }
                index = run.endIndex
                continue
            }
            try {
                val one = readEntity(o, layerNames, blockNames, typeByHandle)
                if (one != null) placed += one else unsupported += typeName(o.type)
            } catch (e: RuntimeException) {
                unsupported += typeName(o.type)
            }
            index++
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

    // --- the file's table of contents ---------------------------------------

    private fun int32(at: Int): Int {
        if (at < 0 || at + 4 > data.size) throw DwgTruncated()
        return (data[at].toInt() and 0xFF) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            ((data[at + 2].toInt() and 0xFF) shl 16) or
            ((data[at + 3].toInt() and 0xFF) shl 24)
    }

    private fun locators(): Map<Int, Locator>? {
        if (data.size < LOCATOR_START + 9) return null
        val count = int32(LOCATOR_COUNT_AT)
        if (count <= 0 || count > MAX_LOCATORS) return null
        val out = HashMap<Int, Locator>()
        var at = LOCATOR_START
        repeat(count) {
            if (at + 9 > data.size) return null
            val id = data[at].toInt() and 0xFF
            val offset = int32(at + 1)
            val size = int32(at + 5)
            if (offset in 0 until data.size && size >= 0) out[id] = Locator(offset, size)
            at += 9
        }
        return out
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
    private fun declaredClasses(l: Locator): Map<Int, DeclaredClass> {
        val out = HashMap<Int, DeclaredClass>()
        try {
            val start = l.offset + CLASS_SENTINEL_BYTES
            if (start + 4 > data.size) return out
            val r = DwgBitReader(data, start * 8)
            val size = r.rawLong()
            if (size <= 0 || start + 4 + size > data.size) return out
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
                        name = name.uppercase(),
                        isEntity = itemClass == DECLARED_ENTITY,
                    )
                }
            }
        } catch (e: RuntimeException) {
            return out
        }
        return out
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
    private fun objectMap(l: Locator): LinkedHashMap<Long, Int> {
        val out = LinkedHashMap<Long, Int>()
        val limit = minOf(l.offset + l.size, data.size)
        var at = l.offset
        while (at + 2 <= limit) {
            val runSize = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
            if (runSize <= 2) break
            val runEnd = at + runSize
            if (runEnd > limit) break
            var handle = 0L
            var location = 0L
            var p = at + 2
            while (p < runEnd) {
                val handleStep = unsignedModular(p)
                val locationStep = signedModular(handleStep.second)
                handle += handleStep.first
                location += locationStep.first
                p = locationStep.second
                if (location > 0 && location < data.size) out[handle] = location.toInt()
            }
            at = runEnd + 2
        }
        return out
    }

    private fun unsignedModular(from: Int): Pair<Long, Int> {
        var at = from
        var value = 0L
        var shift = 0
        while (true) {
            if (at >= data.size || shift > 56) throw DwgTruncated()
            val b = data[at].toInt() and 0xFF
            at++
            value = value or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if (b and 0x80 == 0) return value to at
        }
    }

    private fun signedModular(from: Int): Pair<Long, Int> {
        var at = from
        var value = 0L
        var shift = 0
        while (true) {
            if (at >= data.size || shift > 56) throw DwgTruncated()
            var b = data[at].toInt() and 0xFF
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
        val afterSize = r.position
        if (size <= 0 || afterSize / 8 + size > data.size) return null
        val type = r.bitShort()
        val bitSize = if (modern) r.rawLong() else -1
        if (modern && (bitSize <= 0 || bitSize > size * 8 + 16)) return null
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
        if (!modern) r.rawLong()
        r.bitLong()
        return r
    }

    private fun readName(o: RawObject): String? = try {
        tableBody(o).text().takeIf { it.isNotEmpty() }
    } catch (e: RuntimeException) {
        null
    }

    private fun readBlockHeader(o: RawObject): Pair<String, Vec2>? = try {
        val r = tableBody(o)
        val name = r.text()
        if (name.isEmpty()) throw DwgMalformedValue()
        r.boolean()
        r.bitShort()
        r.boolean()
        r.boolean()
        r.boolean()
        r.boolean()
        r.boolean()
        if (modern) r.boolean()
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

    private fun entityHeader(o: RawObject, r: DwgBitReader): EntityHeader {
        skipExtendedData(r)
        if (r.boolean()) {
            val size = r.rawLong()
            if (size < 0 || size > data.size || !r.has(size * 8)) throw DwgTruncated()
            r.skip(size * 8)
        }
        val bitSize = if (modern) o.headerBitSize else r.rawLong()
        if (bitSize <= 0 || o.afterSizeBit + bitSize > r.bitLength) throw DwgTruncated()
        val entityMode = r.twoBits()
        val reactors = r.bitLong()
        if (!modern) r.boolean()
        val noLinks = r.boolean()
        val colour = r.bitShort()
        r.bitDouble()
        if (modern) {
            r.twoBits()
            r.twoBits()
        }
        r.bitShort()
        if (modern) r.rawChar()
        return EntityHeader(
            entityMode = entityMode,
            reactors = reactors,
            noLinks = noLinks,
            colour = colour,
            handleStreamBit = o.afterSizeBit + bitSize,
        )
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
        val owner = if (h.entityMode == 0) resolve(r.handle(), o.handle) else null
        repeat(h.reactors.coerceIn(0, MAX_REACTORS)) { r.handle() }
        r.handle()
        val layer: Long?
        if (modern) {
            if (!h.noLinks) {
                r.handle()
                r.handle()
            }
            layer = resolve(r.handle(), o.handle)
        } else {
            layer = resolve(r.handle(), o.handle)
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
        val h = entityHeader(o, r)
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
            DwgObjectType.TEXT -> readText(r, layer, colour)
            DwgObjectType.MTEXT -> readMText(r, layer, colour)
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
        if (!modern) return point3(r) to point3(r)
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
        if (modern) {
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
        val widths = if (flags and LW_WIDTHS != 0) r.bitLong() else 0
        if (count < 0 || count > MAX_VERTICES) throw DwgMalformedValue()
        // Before AutoCAD 2000 every vertex is written out in full; from it,
        // each one after the first is the difference from the one before.
        val points = ArrayList<Vec2>(count)
        for (i in 0 until count) {
            points += if (i == 0 || !modern) {
                Vec2(r.rawDouble(), r.rawDouble())
            } else {
                val last = points[i - 1]
                Vec2(r.bitDoubleWithDefault(last.x), r.bitDoubleWithDefault(last.y))
            }
        }
        repeat(bulges.coerceIn(0, MAX_VERTICES)) { r.bitDouble() }
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
    private fun readText(r: DwgBitReader, layer: String, colour: Int): DxfEntity {
        val position: Vec3
        val height: Double
        val rotation: Double
        val value: String
        val alignCode: Int
        if (modern) {
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
            value = r.text()
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
            value = r.text()
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
    private fun readMText(r: DwgBitReader, layer: String, colour: Int): DxfEntity {
        val position = point3(r)
        point3(r)
        val axis = point3(r)
        r.bitDouble()
        val height = r.bitDouble()
        r.bitShort()
        r.bitShort()
        r.bitDouble()
        r.bitDouble()
        val value = r.text()
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
        if (modern) {
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

    private class PolylineRun(val vertices: List<RawObject>, val endIndex: Int)

    private fun isPolyline(type: Int): Boolean = type == DwgObjectType.POLYLINE_2D ||
        type == DwgObjectType.POLYLINE_3D ||
        type == DwgObjectType.POLYLINE_PFACE ||
        type == DwgObjectType.POLYLINE_MESH

    private fun isVertex(type: Int): Boolean = type in DwgObjectType.VERTEX_2D..DwgObjectType.VERTEX_PFACE_FACE

    /**
     * The vertices belonging to the polyline at [from].
     *
     * The classic polyline keeps its points in separate objects that follow it
     * in the file and stop at an end marker — exactly as the same entity does in
     * a DXF file.
     */
    private fun polylineRun(objects: List<RawObject>, from: Int): PolylineRun {
        val vertices = ArrayList<RawObject>()
        var i = from + 1
        while (i < objects.size) {
            val type = objects[i].type
            if (isVertex(type)) {
                vertices += objects[i]
                i++
                continue
            }
            if (type == DwgObjectType.SEQEND) {
                i++
            }
            break
        }
        return PolylineRun(vertices, i)
    }

    private fun readPolyline(
        o: RawObject,
        run: PolylineRun,
        layerNames: Map<Long, String>,
        counts: MutableMap<String, Int>,
    ): List<Placed> {
        val r = DwgBitReader(data, o.bodyBit)
        val h = entityHeader(o, r)
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
        for (v in run.vertices) {
            counts["VERTEX"] = (counts["VERTEX"] ?: 0) + 1
            val vr = DwgBitReader(data, v.bodyBit)
            try {
                entityHeader(v, vr)
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
private const val CLASS_SENTINEL_BYTES = 16
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
