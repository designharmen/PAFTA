package com.harmen.pafta.project

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.mesh.Mesh
import com.harmen.pafta.mesh.MeshFailure
import com.harmen.pafta.mesh.MeshResult
import com.harmen.pafta.mesh.ObjReader
import com.harmen.pafta.mesh.StlReader

/**
 * A project opened as a 3D model: the faces, how big it is, and what was in it.
 *
 * The counterpart of [DrawingDocument]. A project holds either a drawing or a
 * model, decided by what was imported into it, and the screen shows whichever
 * one it turns out to be.
 */
public data class ModelDocument(
    val faces: List<Face3>,
    val bounds: Aabb,
    /** How many faces the file held, which may be more than are shown. */
    val facesInFile: Int,
    /** The names the file gave its parts, if it gave any. */
    val groups: List<String>,
) {
    /** True when the file was bigger than this viewer can draw at a usable speed. */
    public val isTruncated: Boolean get() = facesInFile > faces.size
}

/**
 * Opens a project's payload as a 3D model.
 *
 * Same contract as [openAsDrawing] and for the same reason: a failure that says
 * which format and what was wrong with it, never an empty view with no
 * explanation.
 */
public fun PaftaProject.openAsModel(): StoreResult<ModelDocument> {
    val format = manifest.format
        ?: return StoreResult.Failure(StoreFailure.UnknownFormat(manifest.source.format))

    val outcome = when (format) {
        FileFormat.STL -> StlReader.read(payload)
        FileFormat.OBJ -> ObjReader.read(payload)
        else -> return StoreResult.Failure(
            StoreFailure.Unreadable(format, UnreadableReason.NO_VIEWER_YET),
        )
    }

    return when (outcome) {
        is MeshResult.Failure -> StoreResult.Failure(
            StoreFailure.Unreadable(
                format,
                when (outcome.reason) {
                    MeshFailure.MALFORMED -> UnreadableReason.MALFORMED
                    MeshFailure.TRUNCATED_FILE -> UnreadableReason.MALFORMED
                    MeshFailure.NOTHING_IN_IT -> UnreadableReason.NO_DRAWABLE_CONTENT
                },
            ),
        )

        is MeshResult.Success -> StoreResult.Success(outcome.mesh.toDocument())
    }
}

/**
 * The read mesh, as the faces the viewer draws.
 *
 * Model files keep their parts' names; those become layers, so the palette can
 * hide the chassis and leave the body — which is the one thing anybody wants to
 * do with somebody else's model. A file with no names gets one layer, so there
 * is always a row to turn the whole thing off with.
 */
private fun Mesh.toDocument(): ModelDocument {
    val converted = faces.map {
        Face3(
            points = it.points,
            kind = FaceKind.MODEL,
            layer = it.group.ifEmpty { DrawnShape.LAYER_MODEL },
            id = DrawnShape.LAYER_MODEL,
        )
    }
    return ModelDocument(
        faces = converted,
        bounds = bounds(),
        facesInFile = facesInFile,
        groups = groups.ifEmpty { listOf(DrawnShape.LAYER_MODEL) },
    )
}

/** The palette rows a model's parts become, with the user's choices applied. */
public fun ModelDocument.layerStates(saved: List<LayerState>): List<LayerState> {
    val savedById = saved.associateBy { it.id }
    val names = faces.map { it.layer }.distinct()
    return names.map { name ->
        val override = savedById[name]
        LayerState(
            id = name,
            name = name,
            visible = override?.visible ?: true,
            opacity = override?.opacity ?: 1.0,
        )
    }
}
