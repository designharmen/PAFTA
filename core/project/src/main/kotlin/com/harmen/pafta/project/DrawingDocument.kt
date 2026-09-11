package com.harmen.pafta.project

import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.dxf.DxfReader
import com.harmen.pafta.geometry.Aabb

/**
 * A project opened as a 2D drawing: the parsed geometry, the layer palette, and
 * whatever the reader could not handle.
 */
public data class DrawingDocument(
    val drawing: DxfDrawing,
    /** Palette rows, in file order, with any saved overrides applied. */
    val layers: List<LayerState>,
    val bounds: Aabb,
    /** Entity types present in the file that the reader does not model. */
    val unsupportedEntityTypes: Set<String>,
) {
    public val entityCount: Int get() = drawing.entities.size

    /** True when there is nothing to draw, so the UI can say so plainly. */
    public val isEmpty: Boolean get() = drawing.entities.isEmpty()
}

/**
 * Opens a project's payload as a drawing.
 *
 * Returns a failure rather than an empty document when the payload is not a
 * drawing format or will not parse, so the editor never shows a blank canvas
 * with no explanation.
 */
public fun PaftaProject.openAsDrawing(): StoreResult<DrawingDocument> {
    val format = manifest.format
    if (format != FileFormat.DXF) {
        return StoreResult.Failure(
            if (format == null) {
                StoreFailure.UnknownFormat(manifest.source.format)
            } else {
                StoreFailure.Unreadable(format, "PAFTA cannot open this as a 2D drawing yet")
            },
        )
    }

    val drawing = try {
        DxfReader.read(payload.inputStream())
    } catch (e: Exception) {
        return StoreResult.Failure(
            StoreFailure.Unreadable(FileFormat.DXF, e.message ?: "the file is malformed"),
        )
    }

    return StoreResult.Success(
        DrawingDocument(
            drawing = drawing,
            layers = mergeLayers(drawing, layers),
            bounds = drawing.bounds,
            unsupportedEntityTypes = drawing.unsupportedEntityTypes,
        ),
    )
}

/**
 * Reconciles the layers in the file with the visibility and opacity the user
 * saved.
 *
 * The file decides which layers exist; the project decides how they look. A
 * layer the user hid stays hidden when the drawing is reopened, a layer added to
 * a revised drawing appears visible, and saved state for a layer that is no
 * longer in the file is dropped rather than shown as a row that controls
 * nothing.
 */
internal fun mergeLayers(drawing: DxfDrawing, saved: List<LayerState>): List<LayerState> {
    val savedById = saved.associateBy { it.id }

    // Layers the entities actually reference, even when the TABLES section omits
    // them — DXF files from other tools routinely draw on undeclared layers.
    val declared = drawing.layers.map { it.name }
    val referenced = drawing.entities.map { it.layer }.distinct()
    val names = (declared + referenced).distinct()

    return names.map { name ->
        val fromFile = drawing.layer(name)
        val override = savedById[name]
        LayerState(
            id = name,
            name = name,
            // A frozen layer starts hidden; the user's saved choice wins over it.
            visible = override?.visible ?: fromFile.visible,
            opacity = override?.opacity ?: 1.0,
            colour = override?.colour,
            locked = override?.locked ?: false,
        )
    }
}
