package com.harmen.pafta.dxf

import java.io.Writer
import java.util.Locale

/**
 * Writes ASCII DXF in the R12 dialect.
 *
 * R12 is the deliberate target: it is the most widely readable DXF flavour, it
 * needs no handles or object ownership bookkeeping, and everything PAFTA's 2D
 * side produces (lines, polylines, arcs, circles, text) expresses cleanly in
 * it.
 */
public object DxfWriter {

    public fun writeToString(drawing: DxfDrawing): String =
        StringBuilder().also { sb -> write(drawing) { sb.append(it) } }.toString()

    public fun write(drawing: DxfDrawing, writer: Writer) {
        write(drawing) { writer.write(it) }
        writer.flush()
    }

    private fun write(drawing: DxfDrawing, emit: (String) -> Unit) {
        fun pair(code: Int, value: String) = emit("$code\n$value\n")
        fun num(code: Int, value: Double) = pair(code, fmt(value))

        // --- HEADER ---------------------------------------------------------
        pair(0, "SECTION")
        pair(2, "HEADER")
        pair(9, "\$ACADVER")
        pair(1, "AC1009") // R12
        pair(9, "\$INSUNITS")
        pair(70, drawing.insUnits.code.toString())
        val b = drawing.bounds
        if (!b.isEmpty) {
            pair(9, "\$EXTMIN")
            num(10, b.min.x); num(20, b.min.y)
            pair(9, "\$EXTMAX")
            num(10, b.max.x); num(20, b.max.y)
        }
        pair(0, "ENDSEC")

        // --- TABLES (layers) ------------------------------------------------
        pair(0, "SECTION")
        pair(2, "TABLES")
        pair(0, "TABLE")
        pair(2, "LAYER")
        // Layer "0" always exists in a valid DXF, so synthesise it when absent.
        val layers = if (drawing.layers.any { it.name == "0" }) {
            drawing.layers
        } else {
            listOf(DxfLayer("0")) + drawing.layers
        }
        pair(70, layers.size.toString())
        for (layer in layers) {
            pair(0, "LAYER")
            pair(2, layer.name)
            pair(70, if (layer.frozen) "1" else "0")
            pair(62, layer.colour.toString())
            pair(6, layer.lineType)
        }
        pair(0, "ENDTAB")
        pair(0, "ENDSEC")

        // --- ENTITIES -------------------------------------------------------
        pair(0, "SECTION")
        pair(2, "ENTITIES")
        for (e in drawing.entities) {
            when (e) {
                is DxfEntity.Line -> {
                    pair(0, "LINE"); pair(8, e.layer); colour(e.colour, ::pair)
                    num(10, e.start.x); num(20, e.start.y); num(30, e.start.z)
                    num(11, e.end.x); num(21, e.end.y); num(31, e.end.z)
                }
                is DxfEntity.Point -> {
                    pair(0, "POINT"); pair(8, e.layer); colour(e.colour, ::pair)
                    num(10, e.position.x); num(20, e.position.y); num(30, e.position.z)
                }
                is DxfEntity.Circle -> {
                    pair(0, "CIRCLE"); pair(8, e.layer); colour(e.colour, ::pair)
                    num(10, e.centre.x); num(20, e.centre.y); num(30, e.centre.z)
                    num(40, e.radius)
                }
                is DxfEntity.Arc -> {
                    pair(0, "ARC"); pair(8, e.layer); colour(e.colour, ::pair)
                    num(10, e.centre.x); num(20, e.centre.y); num(30, e.centre.z)
                    num(40, e.radius)
                    num(50, e.startAngleDegrees); num(51, e.endAngleDegrees)
                }
                is DxfEntity.Polyline -> {
                    // R12 has no LWPOLYLINE, so emit POLYLINE + VERTEX + SEQEND.
                    pair(0, "POLYLINE"); pair(8, e.layer); colour(e.colour, ::pair)
                    pair(66, "1") // vertices follow
                    pair(70, if (e.closed) "1" else "0")
                    num(10, 0.0); num(20, 0.0); num(30, 0.0)
                    for (v in e.vertices) {
                        pair(0, "VERTEX"); pair(8, e.layer)
                        num(10, v.x); num(20, v.y); num(30, 0.0)
                    }
                    pair(0, "SEQEND"); pair(8, e.layer)
                }
                is DxfEntity.Text -> {
                    pair(0, "TEXT"); pair(8, e.layer); colour(e.colour, ::pair)
                    num(10, e.position.x); num(20, e.position.y); num(30, e.position.z)
                    num(40, e.height)
                    pair(1, e.value.replace("\n", "\\P"))
                    if (e.rotationDegrees != 0.0) num(50, e.rotationDegrees)
                    pair(
                        72,
                        when (e.align) {
                            DxfTextAlign.LEFT -> "0"
                            DxfTextAlign.CENTRE -> "1"
                            DxfTextAlign.RIGHT -> "2"
                        },
                    )
                }
                is DxfEntity.Insert -> {
                    pair(0, "INSERT"); pair(8, e.layer); colour(e.colour, ::pair)
                    pair(2, e.blockName)
                    num(10, e.position.x); num(20, e.position.y); num(30, e.position.z)
                    num(41, e.scale.x); num(42, e.scale.y); num(43, e.scale.z)
                    if (e.rotationDegrees != 0.0) num(50, e.rotationDegrees)
                }
            }
        }
        pair(0, "ENDSEC")
        pair(0, "EOF")
    }

    private fun colour(colour: Int, pair: (Int, String) -> Unit) {
        // 256 is "by layer", which is the default; omitting it keeps files small.
        if (colour != COLOUR_BY_LAYER) pair(62, colour.toString())
    }

    /**
     * DXF reals use a plain decimal form with a dot separator. Trailing zeros
     * are trimmed so that coordinates stay readable, and scientific notation is
     * avoided because some older readers reject it.
     */
    private fun fmt(value: Double): String {
        if (!value.isFinite()) return "0.0"
        var s = String.format(Locale.ROOT, "%.9f", value)
        if (s.contains('.')) {
            s = s.trimEnd('0')
            if (s.endsWith('.')) s += "0"
        }
        return if (s == "-0.0") "0.0" else s
    }
}
