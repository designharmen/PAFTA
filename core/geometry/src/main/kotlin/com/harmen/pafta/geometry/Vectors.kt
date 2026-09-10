package com.harmen.pafta.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Tolerance used when comparing model-space coordinates (model units). */
public const val EPS: Double = 1e-9

/** A point or direction on the 2D drawing plane. */
public data class Vec2(val x: Double, val y: Double) {

    public operator fun plus(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
    public operator fun minus(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
    public operator fun times(s: Double): Vec2 = Vec2(x * s, y * s)
    public operator fun div(s: Double): Vec2 = Vec2(x / s, y / s)
    public operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    public infix fun dot(o: Vec2): Double = x * o.x + y * o.y

    /** 2D cross product (z component of the 3D cross product). */
    public infix fun cross(o: Vec2): Double = x * o.y - y * o.x

    public val length: Double get() = hypot(x, y)
    public val lengthSquared: Double get() = x * x + y * y

    public fun normalized(): Vec2 {
        val l = length
        return if (l < EPS) ZERO else Vec2(x / l, y / l)
    }

    /** Rotated 90° counter-clockwise. */
    public fun perpendicular(): Vec2 = Vec2(-y, x)

    public fun rotated(radians: Double): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    public fun distanceTo(o: Vec2): Double = hypot(o.x - x, o.y - y)

    /** Angle from the +X axis, in radians, within (-PI, PI]. */
    public val angle: Double get() = atan2(y, x)

    public fun isCloseTo(o: Vec2, tolerance: Double = 1e-6): Boolean =
        abs(x - o.x) <= tolerance && abs(y - o.y) <= tolerance

    public fun toVec3(z: Double = 0.0): Vec3 = Vec3(x, y, z)

    public companion object {
        public val ZERO: Vec2 = Vec2(0.0, 0.0)
        public val X: Vec2 = Vec2(1.0, 0.0)
        public val Y: Vec2 = Vec2(0.0, 1.0)
    }
}

/** A point or direction in model space. */
public data class Vec3(val x: Double, val y: Double, val z: Double) {

    public operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
    public operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
    public operator fun times(s: Double): Vec3 = Vec3(x * s, y * s, z * s)
    public operator fun div(s: Double): Vec3 = Vec3(x / s, y / s, z / s)
    public operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    public infix fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    public infix fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    public val length: Double get() = sqrt(x * x + y * y + z * z)
    public val lengthSquared: Double get() = x * x + y * y + z * z

    public fun normalized(): Vec3 {
        val l = length
        return if (l < EPS) ZERO else Vec3(x / l, y / l, z / l)
    }

    public fun distanceTo(o: Vec3): Double = (o - this).length

    public fun isCloseTo(o: Vec3, tolerance: Double = 1e-6): Boolean =
        abs(x - o.x) <= tolerance && abs(y - o.y) <= tolerance && abs(z - o.z) <= tolerance

    public fun toVec2(): Vec2 = Vec2(x, y)

    public companion object {
        public val ZERO: Vec3 = Vec3(0.0, 0.0, 0.0)
        public val X: Vec3 = Vec3(1.0, 0.0, 0.0)
        public val Y: Vec3 = Vec3(0.0, 1.0, 0.0)
        public val Z: Vec3 = Vec3(0.0, 0.0, 1.0)
    }
}

/**
 * Unsigned angle at [vertex] between the rays to [a] and [b], in radians,
 * within [0, PI]. Returns 0 when either ray is degenerate.
 */
public fun angleBetween(a: Vec3, vertex: Vec3, b: Vec3): Double {
    val u = (a - vertex)
    val v = (b - vertex)
    val lu = u.length
    val lv = v.length
    if (lu < EPS || lv < EPS) return 0.0
    val c = ((u dot v) / (lu * lv)).coerceIn(-1.0, 1.0)
    return acos(c)
}
