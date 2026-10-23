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
     * True for the versions this reader understands.
     *
     * R13 through R2000 share one file layout: a table of where things are,
     * then the objects. From 2004 onwards the file is split into compressed
     * pages, which is a different reader. Everything a program can save as
     * "AutoCAD 2000 DWG" lands in the first group.
     */
    public val readable: Boolean get() = ordinal <= R2000.ordinal

    public companion object {
        /** The version from the file's first six bytes, or null if those are not a DWG's. */
        public fun of(bytes: ByteArray): DwgVersion? {
            if (bytes.size < 6) return null
            val tag = String(bytes, 0, 6, Charsets.US_ASCII)
            return entries.firstOrNull { it.tag == tag }
        }
    }
}
