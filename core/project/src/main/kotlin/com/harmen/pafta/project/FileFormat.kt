package com.harmen.pafta.project

import java.util.Locale

/** Which viewer a file opens in. */
public enum class ViewerKind {
    /** 2D drawing: DXF today, DWG once LibreDWG lands. */
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
    DWG("dwg", ViewerKind.DRAWING, readable = false),

    GLB("glb", ViewerKind.MODEL, readable = false),
    GLTF("gltf", ViewerKind.MODEL, readable = false),
    OBJ("obj", ViewerKind.MODEL, readable = false),
    STL("stl", ViewerKind.MODEL, readable = false),
    PLY("ply", ViewerKind.MODEL, readable = false),
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

        /** Every extension the import picker should offer. */
        public val importableExtensions: List<String> =
            entries.filter { it != PAFTA }.map { it.extension }
    }
}

/** The format of this project's payload, or `null` if unrecognised. */
public val PaftaManifest.format: FileFormat?
    get() = FileFormat.of(source.format.ifEmpty { source.fileName })
