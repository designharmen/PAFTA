package com.harmen.pafta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.harmen.pafta.R
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.FileFormat
import com.harmen.pafta.project.IoCause
import com.harmen.pafta.project.StoreFailure
import com.harmen.pafta.project.UnreadableReason
import com.harmen.pafta.ui.state.UiError

/**
 * Turns the domain's structured values into Turkish text.
 *
 * This is the single boundary where data becomes language. `core:project` knows
 * only facts — which extension, which reason, how many bytes — and everything the
 * user reads is composed here from `strings.xml`.
 */

/** The Turkish name of a file type, e.g. `DXF çizimi`. */
@Composable
@ReadOnlyComposable
public fun FileFormat?.adi(): String = stringResource(
    when (this) {
        FileFormat.DXF -> R.string.format_dxf
        FileFormat.DWG -> R.string.format_dwg
        FileFormat.GLB -> R.string.format_glb
        FileFormat.GLTF -> R.string.format_gltf
        FileFormat.OBJ -> R.string.format_obj
        FileFormat.STL -> R.string.format_stl
        FileFormat.PLY -> R.string.format_ply
        FileFormat.DAE -> R.string.format_dae
        FileFormat.THREE_DS -> R.string.format_3ds
        FileFormat.IFC -> R.string.format_ifc
        FileFormat.SKP -> R.string.format_skp
        FileFormat.PDF -> R.string.format_pdf
        FileFormat.PNG -> R.string.format_png
        FileFormat.JPG, FileFormat.JPEG -> R.string.format_jpg
        FileFormat.WEBP -> R.string.format_webp
        FileFormat.PAFTA -> R.string.format_pafta
        null -> R.string.format_unknown
    },
)

/** The Turkish label of an annotation tool. */
@Composable
@ReadOnlyComposable
public fun AnnotationKind.adi(): String = stringResource(
    when (this) {
        AnnotationKind.TEXT -> R.string.annotation_text
        AnnotationKind.ARROW -> R.string.annotation_arrow
        AnnotationKind.PIN -> R.string.annotation_pin
        AnnotationKind.DIMENSION -> R.string.annotation_dimension
        AnnotationKind.CALLOUT -> R.string.annotation_callout
        AnnotationKind.STAMP -> R.string.annotation_stamp
        AnnotationKind.COMMENT -> R.string.annotation_comment
    },
)

/**
 * The Turkish sentence for a failure.
 *
 * Size limits are shown in megabytes because that is the unit a person reading a
 * warning can act on; the byte counts stay in the data for logs.
 */
@Composable
@ReadOnlyComposable
public fun StoreFailure.mesaj(): String = when (this) {
    is StoreFailure.UnknownFormat ->
        if (extension.isEmpty()) {
            stringResource(R.string.error_no_extension)
        } else {
            stringResource(R.string.error_unknown_format, extension)
        }

    StoreFailure.EmptyFile -> stringResource(R.string.error_empty_file)

    is StoreFailure.TooLarge -> stringResource(
        R.string.error_too_large,
        (sizeBytes / 1_048_576).toInt().coerceAtLeast(1),
        (limitBytes / 1_048_576).toInt(),
    )

    is StoreFailure.Unreadable -> {
        val tur = format.adi()
        when (reason) {
            UnreadableReason.MALFORMED -> stringResource(R.string.error_malformed, tur)
            UnreadableReason.NO_DRAWABLE_CONTENT -> stringResource(R.string.error_no_drawable, tur)
            UnreadableReason.NO_VIEWER_YET -> stringResource(R.string.error_no_viewer, tur)
        }
    }

    is StoreFailure.Io -> stringResource(
        when (cause) {
            IoCause.CANNOT_READ_FILE -> R.string.error_cannot_read
            IoCause.CANNOT_WRITE_PROJECT -> R.string.error_cannot_write
            IoCause.PERMISSION_DENIED -> R.string.error_permission
            IoCause.NOT_A_PROJECT -> R.string.error_not_a_project
            IoCause.NAME_REQUIRED -> R.string.error_name_required
            IoCause.FILE_NAME_UNKNOWN -> R.string.error_file_name_unknown
        },
    )
}

/** The Turkish sentence for anything the screen has to report. */
@Composable
@ReadOnlyComposable
public fun UiError.mesaj(): String = when (this) {
    is UiError.Store -> failure.mesaj()
    is UiError.SaveFailed -> stringResource(R.string.error_save_failed, failure.mesaj())
}
