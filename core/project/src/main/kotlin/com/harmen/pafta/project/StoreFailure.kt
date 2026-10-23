package com.harmen.pafta.project

/**
 * Why a store operation failed.
 *
 * Deliberately carries *structured data* and no prose. PAFTA's interface is
 * Turkish, and the wording belongs in the Android string resources where it can
 * be reviewed and changed as language rather than as code. A failure type that
 * held an English sentence would be a user-visible English sentence waiting to
 * escape onto the screen.
 */
public sealed interface StoreFailure {

    /** The extension is not one PAFTA knows. Empty when the file has none. */
    public data class UnknownFormat(val extension: String) : StoreFailure

    /** The file has no content. */
    public data object EmptyFile : StoreFailure

    /** The file is larger than the import limit. */
    public data class TooLarge(val sizeBytes: Long, val limitBytes: Long) : StoreFailure

    /**
     * The payload could not be used as the format its extension claims.
     *
     * [found] is the file's own account of what it holds, and it is data, not
     * prose: the record names come out of the user's file, and the sentence
     * around them is composed in Turkish at the UI boundary. Without it, "there
     * is nothing to draw in this drawing" is a dead end for everyone — a plan
     * made entirely of hatching and a file whose drawing section is empty give
     * the same blank screen and need opposite answers.
     */
    public data class Unreadable(
        val format: FileFormat,
        val reason: UnreadableReason,
        val found: FileInventory? = null,
        /**
         * The release of the program that wrote the file, when the file says so
         * and that is the reason it cannot be read.
         *
         * A number, not a name: the screen turns it into Turkish, and a year is
         * also what the user sees in their own program's "Save as" list, so it
         * is the one thing that makes the advice actionable.
         */
        val formatYear: Int? = null,
    ) : StoreFailure

    /**
     * A filesystem or permission problem.
     *
     * [diagnostic] is the underlying platform message. It is for logs only and
     * must never reach the screen: it comes from the OS in whatever language the
     * OS chose, which would break the Turkish-only rule.
     */
    public data class Io(
        val cause: IoCause,
        val diagnostic: String? = null,
    ) : StoreFailure
}

/** Why a payload could not be read. */
public enum class UnreadableReason {
    /** The bytes do not parse as the claimed format. */
    MALFORMED,

    /** It parsed, but there is nothing to draw. */
    NO_DRAWABLE_CONTENT,

    /** PAFTA recognises the format but has no viewer for it in this build. */
    NO_VIEWER_YET,

    /**
     * The format is one PAFTA reads, but not the release that wrote this file.
     *
     * DWG is really a family of formats, and PAFTA reads all of them but one.
     * Saying which, by year, lets the user go back to their own program and
     * save the drawing again as something PAFTA opens — a thirty-second fix,
     * where "this file cannot be opened" is a dead end.
     */
    FORMAT_NOT_SUPPORTED,

    /**
     * It read, and it points at a second file that was not imported with it.
     *
     * A `.gltf` may keep its numbers in a `.bin` beside it, and a `.obj` its
     * materials in a `.mtl`. PAFTA copies the one file it was given into the
     * project so that the project keeps working afterwards — which means a
     * file that needs a neighbour has to say so, not show an empty view.
     */
    NEEDS_ANOTHER_FILE,
}

/** The specific filesystem problem, so the UI can explain it precisely. */
public enum class IoCause {
    /** The source file could not be read. */
    CANNOT_READ_FILE,

    /** The project could not be written: a full disk, or a read-only location. */
    CANNOT_WRITE_PROJECT,

    /** Android did not grant read access to the chosen file. */
    PERMISSION_DENIED,

    /** The file exists but is not a PAFTA project. */
    NOT_A_PROJECT,

    /** A project cannot be saved without a name. */
    NAME_REQUIRED,

    /** The name of the picked file could not be determined. */
    FILE_NAME_UNKNOWN,
}

/**
 * What a file turned out to contain.
 *
 * Three facts, because they separate three different problems that look
 * identical on screen: records the reader cannot draw yet ([recordTypes] names
 * them), a file that declares structure but draws nothing (no records, but
 * [layerCount] and [blockCount] are not zero — an export that wrote definitions
 * and no geometry), and a file that is empty end to end (all three zero).
 */
public data class FileInventory(
    /** Record type to how many of them, in the file's own vocabulary. */
    val recordTypes: Map<String, Int> = emptyMap(),
    val layerCount: Int = 0,
    val blockCount: Int = 0,
) {
    public val isEmpty: Boolean
        get() = recordTypes.isEmpty() && layerCount == 0 && blockCount == 0
}
