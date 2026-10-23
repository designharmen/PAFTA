package com.harmen.pafta.dwg

/**
 * What kind of thing a DWG object is.
 *
 * DWG does not write the word `LINE` in the file; it writes the number 19. The
 * names here are the ones the same thing has in a DXF file, deliberately, so
 * that the inventory PAFTA shows when a file holds nothing drawable reads the
 * same whichever of the two files it came from.
 */
internal object DwgObjectType {

    const val TEXT: Int = 1
    const val ATTRIB: Int = 2
    const val ATTDEF: Int = 3
    const val SEQEND: Int = 6
    const val INSERT: Int = 7
    const val MINSERT: Int = 8
    const val VERTEX_2D: Int = 10
    const val VERTEX_3D: Int = 11
    const val VERTEX_MESH: Int = 12
    const val VERTEX_PFACE: Int = 13
    const val VERTEX_PFACE_FACE: Int = 14
    const val POLYLINE_2D: Int = 15
    const val POLYLINE_3D: Int = 16
    const val ARC: Int = 17
    const val CIRCLE: Int = 18
    const val LINE: Int = 19
    const val POINT: Int = 27
    const val FACE_3D: Int = 28
    const val POLYLINE_PFACE: Int = 29
    const val POLYLINE_MESH: Int = 30
    const val SOLID: Int = 31
    const val TRACE: Int = 32
    const val ELLIPSE: Int = 35
    const val MTEXT: Int = 44
    const val BLOCK_HEADER: Int = 49
    const val LAYER: Int = 51
    const val LWPOLYLINE: Int = 77

    private val NAMES: Map<Int, String> = mapOf(
        1 to "TEXT", 2 to "ATTRIB", 3 to "ATTDEF", 4 to "BLOCK", 5 to "ENDBLK",
        6 to "SEQEND", 7 to "INSERT", 8 to "MINSERT",
        10 to "VERTEX", 11 to "VERTEX", 12 to "VERTEX", 13 to "VERTEX", 14 to "VERTEX",
        15 to "POLYLINE", 16 to "POLYLINE",
        17 to "ARC", 18 to "CIRCLE", 19 to "LINE",
        20 to "DIMENSION", 21 to "DIMENSION", 22 to "DIMENSION", 23 to "DIMENSION",
        24 to "DIMENSION", 25 to "DIMENSION", 26 to "DIMENSION",
        27 to "POINT", 28 to "3DFACE", 29 to "POLYLINE", 30 to "POLYLINE",
        31 to "SOLID", 32 to "TRACE", 33 to "SHAPE", 34 to "VIEWPORT",
        35 to "ELLIPSE", 36 to "SPLINE", 37 to "REGION", 38 to "3DSOLID", 39 to "BODY",
        40 to "RAY", 41 to "XLINE", 42 to "DICTIONARY", 43 to "OLEFRAME",
        44 to "MTEXT", 45 to "LEADER", 46 to "TOLERANCE", 47 to "MLINE",
        48 to "BLOCK_RECORD_TABLE", 49 to "BLOCK_RECORD",
        50 to "LAYER_TABLE", 51 to "LAYER",
        52 to "STYLE_TABLE", 53 to "STYLE",
        56 to "LTYPE_TABLE", 57 to "LTYPE",
        60 to "VIEW_TABLE", 61 to "VIEW", 62 to "UCS_TABLE", 63 to "UCS",
        64 to "VPORT_TABLE", 65 to "VPORT", 66 to "APPID_TABLE", 67 to "APPID",
        68 to "DIMSTYLE_TABLE", 69 to "DIMSTYLE",
        70 to "VPORT_ENTITY_TABLE", 71 to "VPORT_ENTITY",
        72 to "GROUP", 73 to "MLINESTYLE", 74 to "OLE2FRAME", 75 to "DUMMY",
        76 to "LONG_TRANSACTION", 77 to "LWPOLYLINE", 78 to "HATCH",
        79 to "XRECORD", 80 to "ACDBPLACEHOLDER", 81 to "VBA_PROJECT", 82 to "LAYOUT",
    )

    /**
     * The name for an object type code.
     *
     * Codes from 500 upwards belong to classes the file declares for itself —
     * a table, a point cloud, something an add-on wrote — and their names live
     * in a part of the file this reader does not read. They come back as the
     * number, which is still the file's own account of what is in it.
     */
    fun name(code: Int): String = NAMES[code] ?: code.toString()

    /** True for the object types that hold geometry. */
    fun isEntity(code: Int): Boolean = code in 1..82 && code !in NON_ENTITY

    private val NON_ENTITY = setOf(42, 48, 49, 50, 51, 52, 53, 56, 57, 60, 61, 62, 63,
        64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 75, 76, 79, 80, 81, 82)
}
