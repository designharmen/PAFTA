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

    /** The payload could not be used as the format its extension claims. */
    public data class Unreadable(
        val format: FileFormat,
        val reason: UnreadableReason,
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
