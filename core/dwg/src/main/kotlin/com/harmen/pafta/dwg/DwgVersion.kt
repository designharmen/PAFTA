package com.harmen.pafta.dwg

/**
 * Which DWG the file is.
 *
 * DWG is not one format. Every few AutoCAD releases the whole layout changed,
 * and a reader that handles one does not automatically handle the next. The
 * first six bytes of the file say which it is, and [year] is the AutoCAD
 * version that wrote it — a number, so it can be shown to the user in any
 * language.
 */
public enum class DwgVersion(internal val tag: String, public val year: Int) {
    R13("AC1012", 1994),
    R14("AC1014", 1997),
    R2000("AC1015", 2000),
    R2004("AC1018", 2004),
    R2007("AC1021", 2007),
    R2010("AC1024", 2010),
    R2013("AC1027", 2013),
    R2018("AC1032", 2018),
    ;

    /**
     * True for the versions this reader understands, which is all but one.
     *
     * AutoCAD 2007 is the exception. Every other release either uses the plain
     * layout of R13 to 2000 or the compressed-page container of 2004; 2007
     * alone wrapped that container in an error-correcting code of its own,
     * which later releases dropped again.
     */
    public val readable: Boolean get() = this != R2007

    /** From 2004 the drawing lives in compressed pages. */
    internal val pagedContainer: Boolean get() = ordinal >= R2004.ordinal

    /** 2000 moved several fields and shortened the coordinates. */
    internal val since2000: Boolean get() = ordinal >= R2000.ordinal

    /** 2004 replaced the colour field and dropped the neighbour links. */
    internal val since2004: Boolean get() = ordinal >= R2004.ordinal

    /** 2007 moved every piece of text to the end of its object, as Unicode. */
    internal val since2007: Boolean get() = ordinal >= R2007.ordinal

    /** 2010 changed how an object states its own size and type. */
    internal val since2010: Boolean get() = ordinal >= R2010.ordinal

    /** 2013 added one more flag to every entity. */
    internal val since2013: Boolean get() = ordinal >= R2013.ordinal

    public companion object {
        /** The version from the file's first six bytes, or null if those are not a DWG's. */
        public fun of(bytes: ByteArray): DwgVersion? {
            if (bytes.size < 6) return null
            val tag = String(bytes, 0, 6, Charsets.US_ASCII)
            return entries.firstOrNull { it.tag == tag }
        }
    }
}
