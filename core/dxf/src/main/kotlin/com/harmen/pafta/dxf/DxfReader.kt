package com.harmen.pafta.dxf

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import java.io.BufferedReader
import java.io.InputStream
import java.io.Reader

/** Raised when a DXF stream is malformed beyond what the reader can recover from. */
public class DxfParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** One DXF group: an integer code and its string value. */
private data class Group(val code: Int, val value: String) {
    fun asDouble(): Double = value.trim().toDoubleOrNull()
        ?: throw DxfParseException("group $code: expected a number, got '$value'")

    fun asInt(): Int = value.trim().toDouble().toInt()
}

/**
 * Reads ASCII DXF (R12 and later, to the extent later files use R12 entities).
 *
 * The reader is deliberately forgiving: an entity type it does not model is
 * skipped and recorded in [DxfDrawing.unsupportedEntityTypes] rather than
 * failing the whole import, because real-world drawings routinely carry
 * entities a 2D viewer has no use for.
 */
public object DxfReader {

    public fun read(text: String): DxfDrawing = read(text.reader().buffered())

    public fun read(stream: InputStream): DxfDrawing =
        read(stream.bufferedReader(Charsets.ISO_8859_1))

    public fun read(reader: Reader): DxfDrawing {
        val groups = tokenize(if (reader is BufferedReader) reader else BufferedReader(reader))
        return parse(groups)
    }

    private fun tokenize(reader: BufferedReader): List<Group> {
        val out = ArrayList<Group>(1024)
        while (true) {
            val codeLine = reader.readLine() ?: break
            val codeText = codeLine.trim()
            if (codeText.isEmpty()) continue
            val code = codeText.toIntOrNull()
                ?: throw DxfParseException("expected a group code, got '$codeText'")
            // Value lines are taken verbatim apart from the line terminator:
            // TEXT values may legitimately contain leading spaces.
            val valueLine = reader.readLine()
                ?: throw DxfParseException("group code $code has no value (truncated file)")
            out += Group(code, valueLine.trimEnd('\r', '\n'))
        }
        return out
    }

    private fun parse(groups: List<Group>): DxfDrawing {
        val layers = mutableListOf<DxfLayer>()
        val entities = mutableListOf<DxfEntity>()
        val unsupported = mutableSetOf<String>()
        var insUnits = DxfInsUnits.UNITLESS

        var i = 0
        while (i < groups.size) {
            val g = groups[i]
            if (g.code == 0 && g.value == "SECTION") {
                val nameGroup = groups.getOrNull(i + 1)
                    ?: throw DxfParseException("SECTION without a name")
                val end = findSectionEnd(groups, i + 2)
                val body = groups.subList(i + 2, end)
                when (nameGroup.value) {
                    "HEADER" -> insUnits = readHeaderUnits(body)
                    "TABLES" -> layers += readLayers(body)
                    "ENTITIES" -> readEntities(body, entities, unsupported)
                    else -> Unit // BLOCKS / CLASSES / OBJECTS are not needed for viewing.
                }
                i = end
            } else {
                i++
            }
        }

        return DxfDrawing(
            layers = layers,
            entities = entities,
            insUnits = insUnits,
            unsupportedEntityTypes = unsupported,
        )
    }

    private fun findSectionEnd(groups: List<Group>, from: Int): Int {
        var i = from
        while (i < groups.size) {
            val g = groups[i]
            if (g.code == 0 && g.value == "ENDSEC") return i
            i++
        }
        return groups.size
    }

    private fun readHeaderUnits(body: List<Group>): DxfInsUnits {
        for (i in body.indices) {
            if (body[i].code == 9 && body[i].value == "\$INSUNITS") {
                val v = body.getOrNull(i + 1) ?: return DxfInsUnits.UNITLESS
                return DxfInsUnits.fromCode(v.asInt())
            }
        }
        return DxfInsUnits.UNITLESS
    }

    private fun readLayers(body: List<Group>): List<DxfLayer> {
        val layers = mutableListOf<DxfLayer>()
        var i = 0
        while (i < body.size) {
            if (body[i].code == 0 && body[i].value == "LAYER") {
                var name: String? = null
                var colour = 7
                var flags = 0
                var lineType = "CONTINUOUS"
                i++
                while (i < body.size && body[i].code != 0) {
                    val g = body[i]
                    when (g.code) {
                        2 -> name = g.value
                        62 -> colour = g.asInt()
                        70 -> flags = g.asInt()
                        6 -> lineType = g.value
                    }
                    i++
                }
                if (name != null) {
                    layers += DxfLayer(
                        name = name,
                        colour = colour,
                        // A negative colour index is AutoCAD's "layer off"; bit 1
                        // of the flags is "frozen". Both hide the layer.
                        frozen = (flags and 1) != 0,
                        lineType = lineType,
                    )
                }
            } else {
                i++
            }
        }
        return layers
    }

    private fun readEntities(
        body: List<Group>,
        out: MutableList<DxfEntity>,
        unsupported: MutableSet<String>,
    ) {
        var i = 0
        while (i < body.size) {
            val g = body[i]
            if (g.code != 0) {
                i++
                continue
            }
            val type = g.value
            val start = i + 1
            var end = start
            while (end < body.size && body[end].code != 0) end++
            val fields = body.subList(start, end)

            when (type) {
                "LINE" -> out += readLine(fields)
                "POINT" -> out += readPoint(fields)
                "LWPOLYLINE" -> out += readLwPolyline(fields)
                "CIRCLE" -> out += readCircle(fields)
                "ARC" -> out += readArc(fields)
                "TEXT", "MTEXT" -> out += readText(fields, type == "MTEXT")
                "INSERT" -> out += readInsert(fields)
                "POLYLINE" -> {
                    // The classic POLYLINE keeps its points in following VERTEX
                    // entities, terminated by SEQEND.
                    val (entity, consumed) = readClassicPolyline(body, i)
                    out += entity
                    i = consumed
                    continue
                }
                "SEQEND", "VERTEX" -> Unit // consumed by readClassicPolyline
                "ENDSEC", "EOF" -> Unit
                else -> unsupported += type
            }
            i = end
        }
    }

    private fun layerOf(fields: List<Group>): String =
        fields.firstOrNull { it.code == 8 }?.value ?: "0"

    private fun colourOf(fields: List<Group>): Int =
        fields.firstOrNull { it.code == 62 }?.asInt() ?: COLOUR_BY_LAYER

    private fun scalar(fields: List<Group>, code: Int, default: Double = 0.0): Double =
        fields.firstOrNull { it.code == code }?.asDouble() ?: default

    private fun readLine(f: List<Group>) = DxfEntity.Line(
        layer = layerOf(f),
        start = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
        end = Vec3(scalar(f, 11), scalar(f, 21), scalar(f, 31)),
        colour = colourOf(f),
    )

    private fun readPoint(f: List<Group>) = DxfEntity.Point(
        layer = layerOf(f),
        position = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
        colour = colourOf(f),
    )

    private fun readCircle(f: List<Group>) = DxfEntity.Circle(
        layer = layerOf(f),
        centre = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
        radius = scalar(f, 40),
        colour = colourOf(f),
    )

    private fun readArc(f: List<Group>) = DxfEntity.Arc(
        layer = layerOf(f),
        centre = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
        radius = scalar(f, 40),
        startAngleDegrees = scalar(f, 50),
        endAngleDegrees = scalar(f, 51),
        colour = colourOf(f),
    )

    private fun readText(f: List<Group>, isMText: Boolean): DxfEntity.Text {
        // MTEXT splits long strings across one code 1 and repeated code 3
        // fragments, in file order.
        val value = if (isMText) {
            buildString {
                for (g in f) if (g.code == 3 || g.code == 1) append(g.value)
            }
        } else {
            f.firstOrNull { it.code == 1 }?.value ?: ""
        }
        val alignCode = f.firstOrNull { it.code == 72 }?.asInt() ?: 0
        return DxfEntity.Text(
            layer = layerOf(f),
            position = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
            height = scalar(f, 40, 2.5),
            value = unescapeMText(value, isMText),
            rotationDegrees = scalar(f, 50),
            align = when (alignCode) {
                1, 4 -> DxfTextAlign.CENTRE
                2 -> DxfTextAlign.RIGHT
                else -> DxfTextAlign.LEFT
            },
            colour = colourOf(f),
        )
    }

    private fun readInsert(f: List<Group>) = DxfEntity.Insert(
        layer = layerOf(f),
        blockName = f.firstOrNull { it.code == 2 }?.value ?: "",
        position = Vec3(scalar(f, 10), scalar(f, 20), scalar(f, 30)),
        scale = Vec3(scalar(f, 41, 1.0), scalar(f, 42, 1.0), scalar(f, 43, 1.0)),
        rotationDegrees = scalar(f, 50),
        colour = colourOf(f),
    )

    private fun readLwPolyline(f: List<Group>): DxfEntity.Polyline {
        val vertices = mutableListOf<Vec2>()
        var pendingX: Double? = null
        for (g in f) {
            when (g.code) {
                10 -> {
                    // A 10 with no 20 after it would be malformed; hold it until
                    // the matching 20 arrives.
                    pendingX = g.asDouble()
                }
                20 -> {
                    val x = pendingX
                    if (x != null) {
                        vertices += Vec2(x, g.asDouble())
                        pendingX = null
                    }
                }
            }
        }
        val flags = f.firstOrNull { it.code == 70 }?.asInt() ?: 0
        return DxfEntity.Polyline(
            layer = layerOf(f),
            vertices = vertices,
            closed = (flags and 1) != 0,
            colour = colourOf(f),
        )
    }

    /** Returns the polyline plus the index just past its SEQEND. */
    private fun readClassicPolyline(body: List<Group>, polylineIndex: Int): Pair<DxfEntity.Polyline, Int> {
        var i = polylineIndex + 1
        val header = mutableListOf<Group>()
        while (i < body.size && body[i].code != 0) {
            header += body[i]
            i++
        }
        val vertices = mutableListOf<Vec2>()
        while (i < body.size) {
            val g = body[i]
            if (g.code != 0) {
                i++
                continue
            }
            when (g.value) {
                "VERTEX" -> {
                    i++
                    var x = 0.0
                    var y = 0.0
                    while (i < body.size && body[i].code != 0) {
                        when (body[i].code) {
                            10 -> x = body[i].asDouble()
                            20 -> y = body[i].asDouble()
                        }
                        i++
                    }
                    vertices += Vec2(x, y)
                }
                "SEQEND" -> {
                    i++
                    while (i < body.size && body[i].code != 0) i++
                    break
                }
                else -> break // malformed: a new entity started without SEQEND
            }
        }
        val flags = header.firstOrNull { it.code == 70 }?.asInt() ?: 0
        val entity = DxfEntity.Polyline(
            layer = header.firstOrNull { it.code == 8 }?.value ?: "0",
            vertices = vertices,
            closed = (flags and 1) != 0,
            colour = header.firstOrNull { it.code == 62 }?.asInt() ?: COLOUR_BY_LAYER,
        )
        return entity to i
    }

    /** Strips the MTEXT formatting codes a 2D viewer cannot honour. */
    private fun unescapeMText(value: String, isMText: Boolean): String {
        if (!isMText) return value
        return value
            .replace("\\P", "\n")
            .replace(Regex("""\\[A-Za-z][^;\\]*;"""), "")
            .replace("{", "")
            .replace("}", "")
    }
}
