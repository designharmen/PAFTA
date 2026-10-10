package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where you are standing, looking at the building.
 *
 * An orbit camera: it always looks at one point, and you go round that point
 * rather than flying freely. That is the right camera for a building — you walk
 * round a model on a table, you do not fly through it — and it is the one
 * nobody gets lost in, which matters more here than freedom does.
 *
 * Model space is the one the plan is drawn in: X east, Y north, **Z up**, in
 * millimetres. Screen space is Y-down, like [Viewport2D].
 *
 * @param target the point the camera looks at, in model millimetres
 * @param distanceMm how far the eye is from it
 * @param yawDegrees which way round the building you are, anticlockwise from east
 * @param pitchDegrees how high up you are: 0 is standing on the ground, 90 is overhead
 * @param fovDegrees the vertical angle of view
 */
public data class Camera3D(
    val target: Vec3 = Vec3.ZERO,
    val distanceMm: Double = 20_000.0,
    val yawDegrees: Double = -60.0,
    val pitchDegrees: Double = 25.0,
    val fovDegrees: Double = 45.0,
) {
    init {
        require(distanceMm > 0.0 && distanceMm.isFinite()) {
            "distance must be finite and > 0, was $distanceMm"
        }
        require(fovDegrees > 0.0 && fovDegrees < 180.0) {
            "field of view must be between 0 and 180, was $fovDegrees"
        }
    }

    /**
     * Where the eye actually is.
     *
     * Worked out from the three numbers above rather than stored, so there is
     * one description of where the camera is and it cannot disagree with
     * itself.
     */
    public val eye: Vec3
        get() {
            val yaw = Math.toRadians(yawDegrees)
            val pitch = Math.toRadians(pitchDegrees)
            val flat = cos(pitch) * distanceMm
            return Vec3(
                target.x - flat * cos(yaw),
                target.y - flat * sin(yaw),
                target.z + sin(pitch) * distanceMm,
            )
        }

    /** The way the camera is pointing. */
    public val forward: Vec3 get() = (target - eye).normalized()

    /** Screen right, as a direction in the model. */
    public val right: Vec3
        get() {
            val side = forward cross UP
            // Looking straight down there is no "side" to take from up; fall
            // back on the yaw, which still says which way round we are.
            return if (side.length < EPS) {
                val yaw = Math.toRadians(yawDegrees)
                Vec3(-sin(yaw), cos(yaw), 0.0)
            } else {
                side.normalized()
            }
        }

    /** Screen up, as a direction in the model. */
    public val up: Vec3 get() = (right cross forward).normalized()

    /** The camera turned round the building, and tipped, within sane limits. */
    public fun orbitedBy(yawDelta: Double, pitchDelta: Double): Camera3D = copy(
        yawDegrees = (yawDegrees + yawDelta) % 360.0,
        // Not past the poles: at 90 the view flips over and nobody who has ever
        // used it has meant to do that.
        pitchDegrees = (pitchDegrees + pitchDelta).coerceIn(-85.0, 85.0),
    )

    /** Closer to or further from what it is looking at. */
    public fun zoomedBy(factor: Double): Camera3D =
        if (!factor.isFinite() || factor <= 0.0) {
            this
        } else {
            copy(distanceMm = (distanceMm / factor).coerceIn(100.0, 10_000_000.0))
        }

    /**
     * The point looked at, slid sideways and up — the whole view goes with it.
     *
     * Moved in the plane of the screen rather than in the model, because a
     * finger dragging across glass means "take this with me", and the model's
     * own axes have nothing to do with which way the finger went.
     */
    public fun pannedBy(rightMm: Double, upMm: Double): Camera3D =
        copy(target = target + right * rightMm + up * upMm)

    /**
     * A model point on the screen, with how far away it is.
     *
     * Null when it is behind the camera, where there is no answer: a point
     * behind you does not appear on the glass, and projecting it anyway puts it
     * on the screen mirrored, which is the classic way a 3D view ends up with
     * walls stretched across the sky.
     */
    public fun project(
        point: Vec3,
        viewWidth: Double,
        viewHeight: Double,
    ): Projected? {
        if (viewWidth <= 0.0 || viewHeight <= 0.0) return null
        val eye = this.eye
        val toPoint = point - eye
        val depth = toPoint dot forward
        if (depth <= NEAR_MM) return null

        val focal = (viewHeight / 2.0) / tan(Math.toRadians(fovDegrees) / 2.0)
        val x = (toPoint dot right) * focal / depth
        val y = (toPoint dot up) * focal / depth
        return Projected(Vec2(viewWidth / 2.0 + x, viewHeight / 2.0 - y), depth)
    }

    public companion object {
        /** Z is up: this is a building, not a video game. */
        public val UP: Vec3 = Vec3(0.0, 0.0, 1.0)

        /** Nearer than this is behind the glass; 1mm, in model units. */
        private const val NEAR_MM = 1.0

        private const val EPS = 1e-9

        /**
         * A camera that frames [bounds] from the usual three-quarter view.
         *
         * The standpoint every architectural model is photographed from: above
         * the eaves, round at an angle so two faces of the building are visible
         * at once, far enough back that all of it fits.
         */
        public fun framing(bounds: Aabb, fovDegrees: Double = 45.0): Camera3D {
            if (bounds.isEmpty) return Camera3D(fovDegrees = fovDegrees)

            val size = bounds.size
            val across = max(max(abs(size.x), abs(size.y)), abs(size.z))
            // Far enough that the whole of it is inside the angle of view, with
            // a little air round it.
            val distance = max(
                1000.0,
                (across / 2.0) / tan(Math.toRadians(fovDegrees) / 2.0) * 1.9,
            )
            return Camera3D(
                target = bounds.center,
                distanceMm = distance,
                fovDegrees = fovDegrees,
            )
        }
    }
}

/** A point put on the screen, and how far in front of the camera it was. */
public data class Projected(val screen: Vec2, val depthMm: Double)
