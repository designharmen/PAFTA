package com.harmen.pafta.dwg

import com.harmen.pafta.dxf.DxfDrawing

/** What came of trying to read a DWG file. */
public sealed interface DwgOutcome {
    public data class Success(val drawing: DxfDrawing) : DwgOutcome
    public data class Failure(val reason: DwgFailure) : DwgOutcome
}

/**
 * Why a DWG file could not be read.
 *
 * Each case carries data, never a sentence: the Turkish the user sees is
 * composed at the screen's edge, in one place, from these values. A reader that
 * returned a message would be a reader that decided the app's language.
 */
public sealed interface DwgFailure {

    /** The bytes do not start like a DWG file at all. */
    public data object NotADwg : DwgFailure

    /**
     * A DWG, but from a version this build cannot open.
     *
     * [version] is what the file says it is, so the screen can name the year
     * and tell the user which "Save as" option gets them a file PAFTA reads.
     */
    public data class TooNew(val version: DwgVersion) : DwgFailure

    /** A DWG of a readable version whose contents did not hold together. */
    public data object Damaged : DwgFailure

    /**
     * It read, and there was no geometry in it.
     *
     * [objectTypeCounts] is the file's own inventory, by object type name, so
     * the screen can say what *is* in there instead of showing an empty sheet.
     */
    public data class NothingInIt(val objectTypeCounts: Map<String, Int>) : DwgFailure
}
