package com.harmen.pafta.project

import com.harmen.pafta.geometry.Vec2
import com.harmen.pafta.geometry.Vec3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Geometry types live in a module without the serialization plugin, so they are
 * persisted through these surrogates. Keeping the wire form a bare `{x,y,z}`
 * object also keeps a `.pafta` file readable by hand.
 */
@Serializable
private data class Vec3Surrogate(val x: Double, val y: Double, val z: Double = 0.0)

@Serializable
private data class Vec2Surrogate(val x: Double, val y: Double)

public object Vec3Serializer : KSerializer<Vec3> {
    override val descriptor: SerialDescriptor = Vec3Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Vec3) {
        encoder.encodeSerializableValue(
            Vec3Surrogate.serializer(),
            Vec3Surrogate(value.x, value.y, value.z),
        )
    }

    override fun deserialize(decoder: Decoder): Vec3 {
        val s = decoder.decodeSerializableValue(Vec3Surrogate.serializer())
        return Vec3(s.x, s.y, s.z)
    }
}

public object Vec2Serializer : KSerializer<Vec2> {
    override val descriptor: SerialDescriptor = Vec2Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Vec2) {
        encoder.encodeSerializableValue(Vec2Surrogate.serializer(), Vec2Surrogate(value.x, value.y))
    }

    override fun deserialize(decoder: Decoder): Vec2 {
        val s = decoder.decodeSerializableValue(Vec2Surrogate.serializer())
        return Vec2(s.x, s.y)
    }
}
