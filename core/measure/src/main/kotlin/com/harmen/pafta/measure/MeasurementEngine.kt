package com.harmen.pafta.measure

import com.harmen.pafta.geometry.Segment2
import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3

/** Snapping targets the engine can lock a pick onto. */
public enum class SnapKind { NONE, ENDPOINT, MIDPOINT, INTERSECTION, PERPENDICULAR, CENTRE, GRID }

/** A candidate snap: where the pick lands and why. */
public data class SnapResult(val point: Vec3, val kind: SnapKind, val distance: Double)

/**
 * Builds measurements from the points the user picks.
 *
 * The engine is a pure state machine: the UI feeds it picks and it reports
 * whether the current measurement is complete. Keeping it free of Android
 * types is what lets it be unit-tested on the JVM.
 */
public class MeasurementEngine(private val idFactory: () -> String = { defaultId() }) {

    private val picks = mutableListOf<Vec3>()

    /** The measurement kind currently being built. */
    public var mode: MeasurementKind = MeasurementKind.DISTANCE
        set(value) {
            if (field != value) {
                field = value
                picks.clear()
            }
        }

    /** Points picked so far for the in-progress measurement. */
    public val pendingPoints: List<Vec3> get() = picks.toList()

    /** How many more picks the current [mode] needs before it can complete. */
    public val remainingPicks: Int
        get() = when (mode) {
            MeasurementKind.DISTANCE -> (2 - picks.size).coerceAtLeast(0)
            MeasurementKind.ANGLE -> (3 - picks.size).coerceAtLeast(0)
            MeasurementKind.AREA -> (3 - picks.size).coerceAtLeast(0)
            MeasurementKind.POLYLINE -> (2 - picks.size).coerceAtLeast(0)
        }

    /**
     * Adds a pick. Returns the finished [Measurement] for fixed-arity modes
     * (distance, angle) as soon as enough points exist, otherwise `null`.
     * Open-ended modes (polyline, area) are closed with [finish].
     */
    public fun addPick(point: Vec3): Measurement? {
        picks += point
        return when (mode) {
            MeasurementKind.DISTANCE -> if (picks.size == 2) take() else null
            MeasurementKind.ANGLE -> if (picks.size == 3) take() else null
            MeasurementKind.POLYLINE, MeasurementKind.AREA -> null
        }
    }

    /** Removes the most recent pick. Returns true when something was undone. */
    public fun undoPick(): Boolean = picks.removeLastOrNull() != null

    /** Discards the in-progress measurement. */
    public fun cancel() {
        picks.clear()
    }

    /**
     * Closes an open-ended measurement. Returns `null` when there are too few
     * picks — a two-point area, for instance, is not an area.
     */
    public fun finish(): Measurement? {
        val enough = when (mode) {
            MeasurementKind.POLYLINE -> picks.size >= 2
            MeasurementKind.AREA -> picks.size >= 3
            MeasurementKind.DISTANCE -> picks.size >= 2
            MeasurementKind.ANGLE -> picks.size >= 3
        }
        if (!enough) return null
        return take()
    }

    private fun take(): Measurement {
        val id = idFactory()
        val m = when (mode) {
            MeasurementKind.DISTANCE -> Measurement.Distance(id, picks[0], picks[1])
            MeasurementKind.ANGLE -> Measurement.Angle(id, picks[0], picks[1], picks[2])
            MeasurementKind.POLYLINE -> Measurement.Polyline(id, picks.toList())
            MeasurementKind.AREA -> Measurement.Area(id, picks.toList())
        }
        picks.clear()
        return m
    }

    public companion object {
        private var counter = 0L

        private fun defaultId(): String = "m${++counter}"
    }
}

/**
 * Finds the best snap for a raw [pick] among candidate [segments].
 *
 * [tolerance] is in model units (millimetres); picks further than that from
 * every candidate return a [SnapKind.NONE] result at the original position, so
 * the caller can always use the returned point.
 */
public fun snap(
    pick: Vec2,
    segments: List<Segment2>,
    tolerance: Double,
    gridSpacing: Double? = null,
): SnapResult {
    var best: SnapResult? = null

    fun offer(p: Vec2, kind: SnapKind) {
        val d = p.distanceTo(pick)
        if (d > tolerance) return
        // Lower ordinal == higher priority; ties break on distance.
        val current = best
        if (current == null || kind.ordinal < current.kind.ordinal ||
            (kind == current.kind && d < current.distance)
        ) {
            best = SnapResult(Vec3(p.x, p.y, 0.0), kind, d)
        }
    }

    for (s in segments) {
        offer(s.a, SnapKind.ENDPOINT)
        offer(s.b, SnapKind.ENDPOINT)
        offer(s.midpoint, SnapKind.MIDPOINT)
        offer(s.closestPointTo(pick), SnapKind.PERPENDICULAR)
    }

    if (gridSpacing != null && gridSpacing > 0.0) {
        val g = Vec2(
            Math.round(pick.x / gridSpacing) * gridSpacing,
            Math.round(pick.y / gridSpacing) * gridSpacing,
        )
        offer(g, SnapKind.GRID)
    }

    return best ?: SnapResult(Vec3(pick.x, pick.y, 0.0), SnapKind.NONE, 0.0)
}
