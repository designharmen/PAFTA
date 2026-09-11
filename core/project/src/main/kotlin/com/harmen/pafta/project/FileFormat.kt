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
    public val displayName: String,
) {
    DXF("dxf", ViewerKind.DRAWING, readable = true, displayName = "DXF drawing"),
    DWG("dwg", ViewerKind.DRAWING, readable = false, displayName = "DWG drawing"),

    GLB("glb", ViewerKind.MODEL, readable = false, displayName = "glTF binary"),
    GLTF("gltf", ViewerKind.MODEL, readable = false, displayName = "glTF"),
    OBJ("obj", ViewerKind.MODEL, readable = false, displayName = "OBJ mesh"),
    STL("stl", ViewerKind.MODEL, readable = false, displayName = "STL mesh"),
    PLY("ply", ViewerKind.MODEL, readable = false, displayName = "PLY mesh"),
    DAE("dae", ViewerKind.MODEL, readable = false, displayName = "COLLADA"),
    THREE_DS("3ds", ViewerKind.MODEL, readable = false, displayName = "3DS mesh"),
    IFC("ifc", ViewerKind.MODEL, readable = false, displayName = "IFC model"),
    SKP("skp", ViewerKind.MODEL, readable = false, displayName = "SketchUp"),

    PDF("pdf", ViewerKind.DOCUMENT, readable = false, displayName = "PDF"),

    PNG("png", ViewerKind.IMAGE, readable = false, displayName = "PNG image"),
    JPG("jpg", ViewerKind.IMAGE, readable = false, displayName = "JPEG image"),
    JPEG("jpeg", ViewerKind.IMAGE, readable = false, displayName = "JPEG image"),
    WEBP("webp", ViewerKind.IMAGE, readable = false, displayName = "WebP image"),

    /** A PAFTA project — opened directly rather than imported. */
    PAFTA(PAFTA_EXTENSION, ViewerKind.UNSUPPORTED, readable = true, displayName = "PAFTA project"),
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
