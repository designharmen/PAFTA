package com.harmen.pafta.measure

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec3
import com.harmen.pafta.geometry.angleBetween
import com.harmen.pafta.geometry.area
import com.harmen.pafta.geometry.centroid
import com.harmen.pafta.geometry.perimeter
import com.harmen.pafta.geometry.polylineLength
import com.harmen.pafta.units.AngleUnit
import com.harmen.pafta.units.AreaUnit
import com.harmen.pafta.units.LengthFormat
import com.harmen.pafta.units.formatAngle
import com.harmen.pafta.units.formatArea
import com.harmen.pafta.units.formatLength

/** What a measurement measures. */
public enum class MeasurementKind { DISTANCE, POLYLINE, ANGLE, AREA }

/**
 * A measurement taken on a model or drawing.
 *
 * All coordinates are model-space millimetres. The raw value is kept unit-less
 * ([rawValue]) so that changing the display unit never loses precision; the UI
 * asks for [label] with whatever format is current.
 */
public sealed interface Measurement {
    public val id: String
    public val kind: MeasurementKind
    public val points: List<Vec3>

    /** Millimetres, square millimetres, or radians depending on [kind]. */
    public val rawValue: Double

    /** The bounding box of the measurement's own geometry. */
    public val bounds: Aabb get() = Aabb.of(points)

    /** Where a leader or label should be anchored. */
    public val anchor: Vec3

    /** Straight-line distance between two picked points. */
    public data class Distance(
        override val id: String,
        val from: Vec3,
        val to: Vec3,
    ) : Measurement {
        override val kind: MeasurementKind get() = MeasurementKind.DISTANCE
        override val points: List<Vec3> get() = listOf(from, to)
        override val rawValue: Double get() = from.distanceTo(to)
        override val anchor: Vec3 get() = (from + to) * 0.5

        /** Axis deltas, for the `dx / dy / dz` read-out in the properties panel. */
        public val delta: Vec3 get() = to - from
    }

    /** Running length along a chain of picked points. */
    public data class Polyline(
        override val id: String,
        override val points: List<Vec3>,
    ) : Measurement {
        init {
            require(points.size >= 2) { "a polyline measurement needs >= 2 points" }
        }

        override val kind: MeasurementKind get() = MeasurementKind.POLYLINE
        override val rawValue: Double get() = polylineLength(points.map { it.toVec2() })
        override val anchor: Vec3 get() = points[points.size / 2]

        /** Per-leg lengths, so the UI can label each segment. */
        public val segmentLengths: List<Double>
            get() = points.zipWithNext { a, b -> a.distanceTo(b) }
    }

    /** Angle at [vertex] between the rays to [from] and [to]. */
    public data class Angle(
        override val id: String,
        val from: Vec3,
        val vertex: Vec3,
        val to: Vec3,
    ) : Measurement {
        override val kind: MeasurementKind get() = MeasurementKind.ANGLE
        override val points: List<Vec3> get() = listOf(from, vertex, to)

        /** Radians, in `[0, PI]`. */
        override val rawValue: Double get() = angleBetween(from, vertex, to)
        override val anchor: Vec3 get() = vertex
    }

    /** Planar area of a closed ring of picked points (projected onto XY). */
    public data class Area(
        override val id: String,
        override val points: List<Vec3>,
    ) : Measurement {
        init {
            require(points.size >= 3) { "an area measurement needs >= 3 points" }
        }

        override val kind: MeasurementKind get() = MeasurementKind.AREA

        /** Square millimetres. */
        override val rawValue: Double get() = area(points.map { it.toVec2() })
        override val anchor: Vec3
            get() {
                val c = centroid(points.map { it.toVec2() })
                return Vec3(c.x, c.y, points.map { it.z }.average())
            }

        /** Closed-loop perimeter, shown alongside the area. */
        public val perimeter: Double get() = perimeter(points.map { it.toVec2() })
    }
}

/** Display preferences applied to every measurement label. */
public data class MeasurementDisplay(
    val lengthFormat: LengthFormat = LengthFormat.MILLIMETRES,
    val areaUnit: AreaUnit = AreaUnit.SQUARE_METRE,
    val areaDecimals: Int = 2,
    val angleUnit: AngleUnit = AngleUnit.DEGREES,
    val angleDecimals: Int = 1,
)

/** The text PAFTA draws next to a measurement, e.g. `5500mm`, `90°`, `24.4m²`. */
public fun Measurement.label(display: MeasurementDisplay = MeasurementDisplay()): String =
    when (this) {
        is Measurement.Distance, is Measurement.Polyline ->
            formatLength(rawValue, display.lengthFormat)

        is Measurement.Angle ->
            formatAngle(rawValue, display.angleUnit, display.angleDecimals)

        is Measurement.Area ->
            formatArea(rawValue, display.areaUnit, display.areaDecimals, trimTrailingZeros = true)
    }
