package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.measure.Measurement
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.units.AngleUnit
import com.harmen.pafta.units.AreaUnit
import com.harmen.pafta.units.LengthUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bumped whenever the on-disk layout changes incompatibly. */
public const val PAFTA_FORMAT_VERSION: Int = 1

/** The file extension PAFTA projects use. */
public const val PAFTA_EXTENSION: String = "pafta"

/**
 * What a project was built from. The original bytes are always copied into the
 * container, so a project stays openable after the user moves or deletes the
 * file it was imported from.
 */
@Serializable
public data class SourceRef(
    /** File name as imported, e.g. `villa-ground-floor.dxf`. */
    val fileName: String,
    /** Lower-case extension, used to pick the viewer. */
    val format: String,
    /** Size of the payload in bytes, for the file-manager listing. */
    val sizeBytes: Long = 0,
    /** Where it came from, for display only; never used to re-read the file. */
    val importedFrom: String? = null,
)

/** A visibility/opacity override for one layer or model node. */
@Serializable
public data class LayerState(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    /** 0.0..1.0; the layer palette shows this as a percentage. */
    val opacity: Double = 1.0,
    /** `#RRGGBB`, or null to keep the colour the source file specifies. */
    val colour: String? = null,
    val locked: Boolean = false,
) {
    init {
        require(opacity in 0.0..1.0) { "opacity must be in 0.0..1.0, was $opacity" }
    }

    /** The `78%` shown next to the layer name. */
    public val opacityPercent: Int get() = Math.round(opacity * 100).toInt()
}

/** A material assignment the user made on top of what the source file defines. */
@Serializable
public data class MaterialOverride(
    /** Mesh, node, or layer the override applies to. */
    val targetId: String,
    val materialId: String,
    val displayName: String,
    /** `#RRGGBB` base colour. */
    val baseColour: String,
    val metallic: Double = 0.0,
    val roughness: Double = 0.6,
    val opacity: Double = 1.0,
)

/** A stored camera the user can jump back to. */
@Serializable
public data class CameraPreset(
    val id: String,
    val name: String,
    @Serializable(with = Vec3Serializer::class) val eye: Vec3,
    @Serializable(with = Vec3Serializer::class) val target: Vec3,
    @Serializable(with = Vec3Serializer::class) val up: Vec3 = Vec3(0.0, 0.0, 1.0),
    /** Vertical field of view in degrees; null for an orthographic preset. */
    val fovDegrees: Double? = 45.0,
    /** Half-height of the orthographic frustum in model units. */
    val orthoHeight: Double? = null,
) {
    public val isOrthographic: Boolean get() = fovDegrees == null
}

/** Annotation kinds, matching the tools in the right-hand panel. */
public enum class AnnotationKind { TEXT, ARROW, PIN, DIMENSION, CALLOUT, STAMP, COMMENT }

/**
 * A user-authored markup on top of the model.
 *
 * Annotations are stored separately from the source payload so that the
 * original file is never rewritten; this is what makes a `.pafta` safe to open
 * against read-only reference drawings.
 */
@Serializable
public sealed interface Annotation {
    public val id: String
    public val layerId: String?
    public val createdAtEpochMs: Long
    public val author: String?

    public val kind: AnnotationKind

    @Serializable
    @SerialName("text")
    public data class Text(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val position: Vec3,
        val value: String,
        val heightMm: Double = 150.0,
        val rotationDegrees: Double = 0.0,
        val colour: String? = null,
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.TEXT
    }

    @Serializable
    @SerialName("arrow")
    public data class Arrow(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val from: Vec3,
        @Serializable(with = Vec3Serializer::class) val to: Vec3,
        val colour: String? = null,
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.ARROW
    }

    @Serializable
    @SerialName("pin")
    public data class Pin(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val position: Vec3,
        val label: String = "",
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.PIN
    }

    /** A dimension the user placed, as opposed to a measurement they took. */
    @Serializable
    @SerialName("dimension")
    public data class Dimension(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val from: Vec3,
        @Serializable(with = Vec3Serializer::class) val to: Vec3,
        /** Offset of the dimension line from the measured edge, in model units. */
        val offsetMm: Double = 300.0,
        /** Overrides the computed text when the user types their own. */
        val textOverride: String? = null,
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.DIMENSION
    }

    @Serializable
    @SerialName("callout")
    public data class Callout(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val anchor: Vec3,
        @Serializable(with = Vec3Serializer::class) val labelPosition: Vec3,
        val value: String,
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.CALLOUT
    }

    @Serializable
    @SerialName("stamp")
    public data class Stamp(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val position: Vec3,
        /** `APPROVED`, `REVISED`, `AS BUILT`, ... */
        val stampId: String,
        val rotationDegrees: Double = 0.0,
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.STAMP
    }

    @Serializable
    @SerialName("comment")
    public data class Comment(
        override val id: String,
        @Serializable(with = Vec3Serializer::class) val position: Vec3,
        val body: String,
        val resolved: Boolean = false,
        /** Ids of replies, kept flat so a thread can be rendered in order. */
        val replyIds: List<String> = emptyList(),
        override val layerId: String? = null,
        override val createdAtEpochMs: Long = 0,
        override val author: String? = null,
    ) : Annotation {
        override val kind: AnnotationKind get() = AnnotationKind.COMMENT
    }
}

/**
 * A persisted measurement.
 *
 * Measurements are stored as picked points plus a kind, never as a cached
 * number, so that re-opening a project in different display units shows the
 * same geometry rather than a stale label.
 */
@Serializable
public data class StoredMeasurement(
    val id: String,
    val kind: String,
    val points: List<@Serializable(with = Vec3Serializer::class) Vec3>,
    val note: String? = null,
) {
    /** Rebuilds the domain measurement, or null when the record is malformed. */
    public fun toMeasurement(): Measurement? {
        val k = runCatching { MeasurementKind.valueOf(kind) }.getOrNull() ?: return null
        return when (k) {
            MeasurementKind.DISTANCE ->
                if (points.size >= 2) Measurement.Distance(id, points[0], points[1]) else null

            MeasurementKind.ANGLE ->
                if (points.size >= 3) Measurement.Angle(id, points[0], points[1], points[2]) else null

            MeasurementKind.POLYLINE ->
                if (points.size >= 2) Measurement.Polyline(id, points) else null

            MeasurementKind.AREA ->
                if (points.size >= 3) Measurement.Area(id, points) else null
        }
    }

    public companion object {
        public fun from(m: Measurement, note: String? = null): StoredMeasurement =
            StoredMeasurement(m.id, m.kind.name, m.points, note)
    }
}

/** Display units for the whole project, stored by enum name for readability. */
@Serializable
public data class UnitPreferences(
    val lengthUnit: String = LengthUnit.MILLIMETRE.name,
    val lengthDecimals: Int = 0,
    val areaUnit: String = AreaUnit.SQUARE_METRE.name,
    val angleUnit: String = AngleUnit.DEGREES.name,
) {
    public fun length(): LengthUnit =
        runCatching { LengthUnit.valueOf(lengthUnit) }.getOrDefault(LengthUnit.MILLIMETRE)

    public fun area(): AreaUnit =
        runCatching { AreaUnit.valueOf(areaUnit) }.getOrDefault(AreaUnit.SQUARE_METRE)

    public fun angle(): AngleUnit =
        runCatching { AngleUnit.valueOf(angleUnit) }.getOrDefault(AngleUnit.DEGREES)
}

/** The container's `manifest.json`. */
@Serializable
public data class PaftaManifest(
    val formatVersion: Int = PAFTA_FORMAT_VERSION,
    val projectName: String,
    val source: SourceRef,
    val units: UnitPreferences = UnitPreferences(),
    val createdAtEpochMs: Long = 0,
    val modifiedAtEpochMs: Long = 0,
    /** Written by the app so an older build can warn about a newer file. */
    val producer: String = "PAFTA",
)

/**
 * A whole PAFTA project, as held in memory.
 *
 * [payload] is the untouched source file. Everything else is PAFTA's own
 * overlay, which is why it round-trips through JSON while the payload is copied
 * byte for byte.
 */
public data class PaftaProject(
    val manifest: PaftaManifest,
    val payload: ByteArray,
    val annotations: List<Annotation> = emptyList(),
    val measurements: List<StoredMeasurement> = emptyList(),
    val layers: List<LayerState> = emptyList(),
    val materials: List<MaterialOverride> = emptyList(),
    val cameras: List<CameraPreset> = emptyList(),
    /** PNG bytes, or null when no preview has been captured yet. */
    val thumbnail: ByteArray? = null,
) {
    // data class equals on ByteArray compares references, which would make
    // round-trip assertions pass or fail for the wrong reason; compare contents.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaftaProject) return false
        return manifest == other.manifest &&
            payload.contentEquals(other.payload) &&
            annotations == other.annotations &&
            measurements == other.measurements &&
            layers == other.layers &&
            materials == other.materials &&
            cameras == other.cameras &&
            (thumbnail?.contentEquals(other.thumbnail ?: ByteArray(0)) ?: (other.thumbnail == null))
    }

    override fun hashCode(): Int {
        var result = manifest.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + annotations.hashCode()
        result = 31 * result + measurements.hashCode()
        result = 31 * result + layers.hashCode()
        result = 31 * result + materials.hashCode()
        result = 31 * result + cameras.hashCode()
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        return result
    }
}
