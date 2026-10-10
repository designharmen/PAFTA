package com.harmen.pafta.mesh

import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.geometry.Vec3

/**
 * A 3D model as it comes out of somebody else's file.
 *
 * Flat faces and nothing else — no materials, no textures, no scene tree. That
 * is not a shortcut, it is what PAFTA can honestly show: the viewer sorts flat
 * faces back to front and fills them, so anything richer would be read, stored
 * and then quietly ignored.
 *
 * No units. STL and OBJ do not record any, so a model exported in metres and
 * one exported in millimetres look identical in the file. Nothing here guesses:
 * the view frames whatever it is given, so a model is the right size on screen
 * whichever unit it was written in.
 */
public data class Mesh(
    val faces: List<MeshFace>,
    /** The names the file gave its parts, in the order they were met. */
    val groups: List<String> = emptyList(),
    /**
     * How many faces the file actually held, which may be more than were read.
     *
     * A model with a million triangles is a model this renderer cannot draw at
     * a usable speed. Saying so is better than reading it all and crawling, and
     * far better than silently showing part of it.
     */
    val facesInFile: Int = faces.size,
) {
    public val isTruncated: Boolean get() = facesInFile > faces.size

    public fun bounds(): Aabb =
        faces.asSequence().flatMap { it.points.asSequence() }
            .fold(Aabb.EMPTY) { box, p -> box.encompass(p) }
}

/** One flat face: three or more points, in order round it. */
public data class MeshFace(
    val points: List<Vec3>,
    /** Which part of the model it belongs to, or empty. */
    val group: String = "",
)

/**
 * Why a model file could not be read. Data, not a sentence — the same rule the
 * rest of PAFTA keeps, so the Turkish is composed in one place at the surface.
 */
public enum class MeshFailure {
    /** The bytes are not the format they claim to be. */
    MALFORMED,

    /** It read, but there was not a single face in it. */
    NOTHING_IN_IT,

    /** It stopped in the middle: the file is cut short. */
    TRUNCATED_FILE,
}

/** The outcome of reading a model file. */
public sealed interface MeshResult {
    public data class Success(val mesh: Mesh) : MeshResult
    public data class Failure(val reason: MeshFailure) : MeshResult
}

/**
 * How many faces are worth reading.
 *
 * The viewer sorts every face back to front and fills it, once per frame. That
 * is exactly right for a building and it has a ceiling: somewhere in the tens
 * of thousands it stops being interactive. Reading past the ceiling only buys
 * a slower model, so the reader stops and says how many there were.
 */
public const val FACE_BUDGET: Int = 40_000
