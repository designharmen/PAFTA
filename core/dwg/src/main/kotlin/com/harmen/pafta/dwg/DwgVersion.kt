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
public enum class DwgVersion(
    internal val tag: String,
    public val year: Int,
    /**
     * True for the versions this reader understands.
     *
     * The ones before R13 are a different format again — they predate the
     * layout everything since is a variation of. They are listed so that such
     * a file is refused by its year, with the one step that fixes it, instead
     * of being called damaged: an old drawing is not a broken drawing.
     *
     * AutoCAD 2007 is the other exception. Every release around it either uses
     * the plain layout of R13 to 2000 or the compressed-page container of
     * 2004; 2007 alone wrapped that container in an error-correcting code of
     * its own, which the next release dropped again.
     */
    public val readable: Boolean,
) {
    R9("AC1004", 1987, readable = false),
    R10("AC1006", 1988, readable = false),
    R11("AC1008", 1990, readable = false),
    R12("AC1009", 1992, readable = false),
    R13("AC1012", 1994, readable = true),
    R14("AC1014", 1997, readable = true),
    R2000("AC1015", 2000, readable = true),
    R2004("AC1018", 2004, readable = true),
    R2007("AC1021", 2007, readable = false),
    R2010("AC1024", 2010, readable = true),
    R2013("AC1027", 2013, readable = true),
    R2018("AC1032", 2018, readable = true),
    ;

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
