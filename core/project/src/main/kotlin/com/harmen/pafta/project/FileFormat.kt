package com.harmen.pafta.project

import java.util.Locale

/** Which viewer a file opens in. */
public enum class ViewerKind {
    /** 2D drawing: DXF or DWG. */
    DRAWING,

    /** 3D model: glTF today, the Assimp formats later. */
    MODEL,

    /** Page-based document. */
    DOCUMENT,

    /** Raster image. */
    IMAGE,

    /** Recognised, but PAFTA cannot open it yet. */
    UNSUPPORTED,
}

/**
 * The file formats PAFTA recognises.
 *
 * [readable] is the honest part: a format can be listed and imported — so the
 * user's file is safely inside a project container — while its viewer is still
 * a later phase. Import never silently drops a file it cannot yet draw.
 */
public enum class FileFormat(
    public val extension: String,
    public val viewer: ViewerKind,
    public val readable: Boolean,
) {
    DXF("dxf", ViewerKind.DRAWING, readable = true),
    // AutoCAD's own file. Read here, in Kotlin, for the versions up to AutoCAD
    // 2000; a file from 2004 or later is recognised and refused by its year.
    DWG("dwg", ViewerKind.DRAWING, readable = true),

    // What everything exports today: Blender, SketchUp, Revit, Rhino and every
    // web tool. JSON describing where the numbers are, plus the numbers.
    GLB("glb", ViewerKind.MODEL, readable = true),
    GLTF("gltf", ViewerKind.MODEL, readable = true),
    // Both are plain lists of triangles with no units and no scene, which is
    // exactly what this viewer can show — so both are read here, in Kotlin,
    // with no native library and no licence question.
    OBJ("obj", ViewerKind.MODEL, readable = true),
    STL("stl", ViewerKind.MODEL, readable = true),
    // What every 3D scanner writes: a plain-text header that says exactly what
    // is in the file, then the file.
    PLY("ply", ViewerKind.MODEL, readable = true),
    DAE("dae", ViewerKind.MODEL, readable = false),
    THREE_DS("3ds", ViewerKind.MODEL, readable = false),
    IFC("ifc", ViewerKind.MODEL, readable = false),
    SKP("skp", ViewerKind.MODEL, readable = false),

    // Revit's own format. No free library reads it, and none is planned: the
    // route in is Revit's own IFC or DXF export. It is still recognised so the
    // file is taken into a project and the user gets that advice, rather than
    // being told PAFTA has never heard of the extension.
    RVT("rvt", ViewerKind.MODEL, readable = false),

    PDF("pdf", ViewerKind.DOCUMENT, readable = false),

    PNG("png", ViewerKind.IMAGE, readable = false),
    JPG("jpg", ViewerKind.IMAGE, readable = false),
    JPEG("jpeg", ViewerKind.IMAGE, readable = false),
    WEBP("webp", ViewerKind.IMAGE, readable = false),

    /** A PAFTA project — opened directly rather than imported. */
    PAFTA(PAFTA_EXTENSION, ViewerKind.UNSUPPORTED, readable = true),
    ;

    public companion object {
        /**
         * Resolves a format from a file name or bare extension. Returns `null`
         * for anything unrecognised so the caller can refuse the import with a
         * message naming the extension, rather than creating an unopenable
         * project.
         */
        public fun of(fileNameOrExtension: String): FileFormat? {
            val token = fileNameOrExtension
                .trim()
                .substringAfterLast('.', fileNameOrExtension.trim())
                .removePrefix(".")
                .lowercase(Locale.ROOT)
            if (token.isEmpty()) return null
            return entries.firstOrNull { it.extension == token }
        }

        /**
         * What the bytes say they are, whatever the file is called.
         *
         * Android hands over a display name from whichever app the file came
         * from, and that name is not always what the file is: a download may
         * arrive as `model(1)`, a cloud drive may hand over no extension at
         * all, and a file renamed by hand is renamed by hand. Refusing a
         * perfectly good model because of its name is the kind of failure
         * nobody can diagnose from the outside.
         *
         * Only the formats PAFTA actually reads are sniffed. A DWG is still a
         * DWG however it is recognised, and recognising it faster would not
         * make it openable.
         */
        public fun sniff(bytes: ByteArray): FileFormat? {
            if (bytes.isEmpty()) return null

            // glTF's binary wrapper says its own name in the first four bytes.
            if (bytes.size >= 12 && bytes.startsWith("glTF")) return GLB

            // A DWG opens with the AutoCAD version that wrote it: `AC` and four
            // digits. Which version it is decides whether PAFTA can draw it, and
            // the reader answers that; here it is enough that the file is a DWG.
            if (bytes.size >= 6 && bytes.startsWith("AC10") && bytes[4].isAsciiDigit() &&
                bytes[5].isAsciiDigit()
            ) {
                return DWG
            }

            // A binary STL is exactly 84 bytes plus 50 a triangle, and that is
            // checked before the text sniffing below — a binary file is allowed
            // to begin with the word `solid` too.
            if (bytes.size >= 84) {
                val count = (0 until 4).fold(0L) { total, i ->
                    total or ((bytes[80 + i].toLong() and 0xFF) shl (8 * i))
                }
                if (bytes.size.toLong() == 84L + count * 50L && count > 0) return STL
            }

            val head = bytes.copyOfRange(0, minOf(bytes.size, SNIFF_BYTES))
                .toString(Charsets.ISO_8859_1)
            val trimmed = head.trimStart()

            return when {
                trimmed.startsWith("ply", ignoreCase = true) -> PLY

                trimmed.startsWith("solid", ignoreCase = true) &&
                    head.contains("facet", ignoreCase = true) -> STL

                // glTF's text spelling is JSON with the one key every file has.
                trimmed.startsWith("{") && head.contains("\"asset\"") -> GLTF

                // DXF is a stream of group codes; every one of them opens the
                // same way, whichever program wrote it.
                head.contains("SECTION") &&
                    (head.contains("HEADER") || head.contains("ENTITIES")) -> DXF

                // OBJ has no header at all, so it is recognised by having both
                // of the two things it is made of.
                OBJ_VERTEX.containsMatchIn(head) && OBJ_FACE.containsMatchIn(head) -> OBJ

                else -> null
            }
        }

        private fun Byte.isAsciiDigit(): Boolean = this >= '0'.code.toByte() && this <= '9'.code.toByte()

        private fun ByteArray.startsWith(text: String): Boolean {
            val wanted = text.toByteArray(Charsets.US_ASCII)
            if (size < wanted.size) return false
            for (i in wanted.indices) if (this[i] != wanted[i]) return false
            return true
        }

        /** Enough of the front of a file to recognise it by, and no more. */
        private const val SNIFF_BYTES = 8192

        private val OBJ_VERTEX = Regex("(?m)^\\s*v\\s+[-0-9.]")
        private val OBJ_FACE = Regex("(?m)^\\s*f\\s+[-0-9]")

        /** Every extension the import picker should offer. */
        public val importableExtensions: List<String> =
            entries.filter { it != PAFTA }.map { it.extension }
    }
}

/** The format of this project's payload, or `null` if unrecognised. */
public val PaftaManifest.format: FileFormat?
    get() = FileFormat.of(source.format.ifEmpty { source.fileName })
