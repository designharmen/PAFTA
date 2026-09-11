package com.harmen.pafta.project

/**
 * A bounded undo/redo history of immutable snapshots.
 *
 * Snapshot-based rather than command-based: the states PAFTA undoes are small
 * (layer flags, annotations, measurements — never the model payload), and a
 * snapshot stack cannot drift out of sync with the document the way a stack of
 * inverse commands can.
 *
 * @param limit how many undo steps to keep; the oldest is dropped beyond it
 */
public class UndoStack<T>(public val limit: Int = 64) {

    init {
        require(limit >= 1) { "limit must be >= 1, was $limit" }
    }

    private val undos = ArrayDeque<T>()
    private val redos = ArrayDeque<T>()

    public val canUndo: Boolean get() = undos.isNotEmpty()
    public val canRedo: Boolean get() = redos.isNotEmpty()

    /** Undo steps currently available. */
    public val undoDepth: Int get() = undos.size

    /** Redo steps currently available. */
    public val redoDepth: Int get() = redos.size

    /**
     * Records [previous] as an undoable step, which is what happens when the
     * document moves off it. Recording always clears the redo branch: once the
     * user edits after undoing, the abandoned future is gone.
     */
    public fun record(previous: T) {
        undos.addLast(previous)
        while (undos.size > limit) undos.removeFirst()
        redos.clear()
    }

    /**
     * Steps back. [current] is pushed onto the redo side so it can be returned
     * to. Returns `null`, changing nothing, when there is no history.
     */
    public fun undo(current: T): T? {
        val previous = undos.removeLastOrNull() ?: return null
        redos.addLast(current)
        return previous
    }

    /** Steps forward again, or `null` when there is nothing to redo. */
    public fun redo(current: T): T? {
        val next = redos.removeLastOrNull() ?: return null
        undos.addLast(current)
        return next
    }

    public fun clear() {
        undos.clear()
        redos.clear()
    }
}
