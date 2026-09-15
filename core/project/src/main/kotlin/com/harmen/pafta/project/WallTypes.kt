package com.harmen.pafta.project

import kotlinx.serialization.Serializable

/**
 * The twenty wall types, as a catalogue rather than as twenty classes.
 *
 * The specification listed twenty "wall types". Read carefully they are four
 * different questions wearing one hat — what it is made of, what it does, how
 * thick it is, how it is built up — and twenty separate classes would have been
 * twenty places to fix the same bug. `DrawnShape.Wall` already carries a
 * material and a thickness, so a "type" here is not a new kind of object: it is
 * a **preset**, one tap that sets the numbers an architect would otherwise set
 * three at a time.
 *
 * What a preset adds beyond those numbers is [function] — whether the wall
 * holds the building up, divides it, or retains earth. That is a real property
 * of a real wall and it is what the schedules of phase G are made of.
 */

/** What a wall is for. Not what it is made of, which is [WallMaterial]. */
@Serializable
public enum class WallFunction {
    /** Divides space and holds nothing up. */
    PARTITION,

    /** Carries the floors above it. */
    LOAD_BEARING,

    /** Carries lateral load: a shear wall, a core. */
    SHEAR,

    /** Holds earth or water back. */
    RETAINING,

    /** Outside the building, standing on its own. */
    PARAPET,
}

/**
 * One entry in the wall catalogue: a name, and the three numbers it sets.
 *
 * The code is ASCII because it can become part of a layer name, and layer
 * names travel into exported DXF files whose encoding cannot carry `Ç` or `Ş`.
 */
@Serializable
public enum class WallType(
    public val code: String,
    public val material: WallMaterial,
    public val thicknessMm: Double,
    public val function: WallFunction,
) {
    // --- tuğla ---
    BRICK_100("TUGLA-100", WallMaterial.BRICK, 100.0, WallFunction.PARTITION),
    BRICK_200("TUGLA-200", WallMaterial.BRICK, 200.0, WallFunction.PARTITION),
    BRICK_300("TUGLA-300", WallMaterial.BRICK, 300.0, WallFunction.LOAD_BEARING),
    BRICK_CAVITY("TUGLA-BOSLUKLU", WallMaterial.BRICK, 350.0, WallFunction.LOAD_BEARING),
    BRICK_FACING("TUGLA-CEPHE", WallMaterial.BRICK, 250.0, WallFunction.PARTITION),

    // --- beton ---
    CONCRETE_150("BETON-150", WallMaterial.CONCRETE, 150.0, WallFunction.LOAD_BEARING),
    CONCRETE_200("BETON-200", WallMaterial.CONCRETE, 200.0, WallFunction.LOAD_BEARING),
    CONCRETE_250("BETON-250", WallMaterial.CONCRETE, 250.0, WallFunction.LOAD_BEARING),
    CONCRETE_SHEAR("BETON-PERDE", WallMaterial.CONCRETE, 300.0, WallFunction.SHEAR),
    CONCRETE_RETAINING("BETON-ISTINAT", WallMaterial.CONCRETE, 400.0, WallFunction.RETAINING),
    CONCRETE_PRECAST("BETON-PREFABRIK", WallMaterial.CONCRETE, 200.0, WallFunction.LOAD_BEARING),
    CONCRETE_PARAPET("BETON-PARAPET", WallMaterial.CONCRETE, 200.0, WallFunction.PARAPET),

    // --- gazbeton ---
    AERATED_100("GAZBETON-100", WallMaterial.AERATED, 100.0, WallFunction.PARTITION),
    AERATED_150("GAZBETON-150", WallMaterial.AERATED, 150.0, WallFunction.PARTITION),
    AERATED_200("GAZBETON-200", WallMaterial.AERATED, 200.0, WallFunction.PARTITION),
    AERATED_250("GAZBETON-250", WallMaterial.AERATED, 250.0, WallFunction.LOAD_BEARING),

    // --- ahşap ---
    TIMBER_STUD("AHSAP-DIKME", WallMaterial.TIMBER, 120.0, WallFunction.PARTITION),
    TIMBER_FRAME("AHSAP-KARKAS", WallMaterial.TIMBER, 200.0, WallFunction.LOAD_BEARING),
    TIMBER_CLAD("AHSAP-KAPLAMA", WallMaterial.TIMBER, 160.0, WallFunction.PARTITION),
    TIMBER_PARAPET("AHSAP-PARAPET", WallMaterial.TIMBER, 100.0, WallFunction.PARAPET),
    ;

    /** The layer a wall of this type belongs on. */
    public fun layer(): String = DrawnShape.wallLayer(thicknessMm, material)
}

/** The wall this type builds, between two points. */
public fun WallType.wallBetween(
    id: String,
    a: com.harmen.pafta.geometry.Vec3,
    b: com.harmen.pafta.geometry.Vec3,
    heightMm: Double = DrawnShape.DEFAULT_WALL_HEIGHT_MM,
): DrawnShape.Wall = DrawnShape.Wall(
    id = id,
    a = a,
    b = b,
    thicknessMm = thicknessMm,
    heightMm = heightMm,
    material = material,
    layer = layer(),
)
