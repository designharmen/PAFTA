package com.harmen.pafta.project

/**
 * Decides *when* an auto-save should happen; the caller does the saving.
 *
 * Saving on every edit would rewrite a multi-megabyte container on each drag of
 * an opacity track. Saving only on exit loses work when the process is killed.
 * So this coalesces: after an edit, wait [quietPeriodMs] for the edits to stop,
 * but never let more than [maxDelayMs] pass with unsaved work — and always save
 * immediately when the app is going away.
 *
 * Pure and clock-driven, so the policy is unit-tested rather than observed.
 */
public class AutoSavePolicy(
    private val quietPeriodMs: Long = 2_000,
    private val maxDelayMs: Long = 30_000,
) {
    init {
        require(quietPeriodMs > 0) { "quietPeriodMs must be > 0" }
        require(maxDelayMs >= quietPeriodMs) { "maxDelayMs must be >= quietPeriodMs" }
    }

    private var firstDirtyAt: Long? = null
    private var lastEditAt: Long = 0

    /** True when there are edits that have not been saved. */
    public val isDirty: Boolean get() = firstDirtyAt != null

    /** Records an edit at [nowMs]. */
    public fun onEdit(nowMs: Long) {
        if (firstDirtyAt == null) firstDirtyAt = nowMs
        lastEditAt = nowMs
    }

    /** Records a completed save, clearing the dirty state. */
    public fun onSaved() {
        firstDirtyAt = null
        lastEditAt = 0
    }

    /** Whether a save is due at [nowMs]. */
    public fun shouldSave(nowMs: Long): Boolean {
        val dirtySince = firstDirtyAt ?: return false
        return nowMs - lastEditAt >= quietPeriodMs || nowMs - dirtySince >= maxDelayMs
    }

    /**
     * Milliseconds until [shouldSave] will next be true, for scheduling a
     * single timer instead of polling. Zero when a save is already due, and
     * `null` when nothing is pending.
     */
    public fun delayUntilSave(nowMs: Long): Long? {
        val dirtySince = firstDirtyAt ?: return null
        if (shouldSave(nowMs)) return 0
        val untilQuiet = lastEditAt + quietPeriodMs - nowMs
        val untilDeadline = dirtySince + maxDelayMs - nowMs
        return minOf(untilQuiet, untilDeadline).coerceAtLeast(0)
    }

    /**
     * The app is being backgrounded or closed: save now if anything is pending,
     * regardless of the quiet period.
     */
    public fun shouldSaveOnExit(): Boolean = isDirty
}
