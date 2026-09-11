package com.harmen.pafta.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoStackTest {

    @Test
    fun `a fresh stack can neither undo nor redo`() {
        val s = UndoStack<String>()
        assertFalse(s.canUndo)
        assertFalse(s.canRedo)
        assertNull(s.undo("current"))
        assertNull(s.redo("current"))
    }

    @Test
    fun `undo walks back through recorded states`() {
        val s = UndoStack<String>()
        s.record("a")
        s.record("b")
        assertEquals(2, s.undoDepth)

        assertEquals("b", s.undo("c"))
        assertEquals("a", s.undo("b"))
        assertNull(s.undo("a"))
        assertFalse(s.canUndo)
    }

    @Test
    fun `redo returns along the path undo took`() {
        val s = UndoStack<String>()
        s.record("a")
        s.record("b")

        assertEquals("b", s.undo("c"))
        assertEquals("a", s.undo("b"))
        assertTrue(s.canRedo)
        assertEquals("b", s.redo("a"))
        assertEquals("c", s.redo("b"))
        assertFalse(s.canRedo)
    }

    @Test
    fun `editing after an undo discards the abandoned future`() {
        val s = UndoStack<String>()
        s.record("a")
        s.record("b")
        assertEquals("b", s.undo("c"))
        assertTrue(s.canRedo)

        // The user edits instead of redoing: "c" is no longer reachable.
        s.record("b")
        assertFalse(s.canRedo)
        assertEquals(0, s.redoDepth)
    }

    @Test
    fun `the oldest step is dropped once the limit is reached`() {
        val s = UndoStack<Int>(limit = 3)
        repeat(5) { s.record(it) }
        assertEquals(3, s.undoDepth)

        // 0 and 1 fell off the bottom; 4, 3, 2 remain.
        assertEquals(4, s.undo(5))
        assertEquals(3, s.undo(4))
        assertEquals(2, s.undo(3))
        assertNull(s.undo(2))
    }

    @Test
    fun `a limit below one is rejected`() {
        assertFailsWith<IllegalArgumentException> { UndoStack<Int>(limit = 0) }
        assertFailsWith<IllegalArgumentException> { UndoStack<Int>(limit = -5) }
    }

    @Test
    fun `clear drops both directions`() {
        val s = UndoStack<String>()
        s.record("a")
        s.undo("b")
        s.clear()
        assertFalse(s.canUndo)
        assertFalse(s.canRedo)
    }

    @Test
    fun `a full undo then redo cycle returns the original state`() {
        val s = UndoStack<String>()
        val states = listOf("s0", "s1", "s2", "s3")
        for (i in 0 until states.size - 1) s.record(states[i])

        var current = states.last()
        val walkedBack = mutableListOf<String>()
        while (s.canUndo) {
            current = s.undo(current)!!
            walkedBack += current
        }
        assertEquals(listOf("s2", "s1", "s0"), walkedBack)

        while (s.canRedo) current = s.redo(current)!!
        assertEquals("s3", current)
    }
}

class AutoSavePolicyTest {

    @Test
    fun `a clean document never needs saving`() {
        val p = AutoSavePolicy()
        assertFalse(p.isDirty)
        assertFalse(p.shouldSave(1_000_000))
        assertFalse(p.shouldSaveOnExit())
        assertNull(p.delayUntilSave(1_000_000))
    }

    @Test
    fun `a save becomes due once editing goes quiet`() {
        val p = AutoSavePolicy(quietPeriodMs = 2_000, maxDelayMs = 30_000)
        p.onEdit(1_000)
        assertTrue(p.isDirty)
        assertFalse(p.shouldSave(2_500), "still inside the quiet period")
        assertTrue(p.shouldSave(3_000), "2s after the last edit")
    }

    @Test
    fun `continued editing keeps pushing the quiet deadline out`() {
        val p = AutoSavePolicy(quietPeriodMs = 2_000, maxDelayMs = 30_000)
        p.onEdit(1_000)
        p.onEdit(2_500)
        p.onEdit(4_000)
        assertFalse(p.shouldSave(5_000))
        assertTrue(p.shouldSave(6_000))
    }

    @Test
    fun `sustained editing still saves at the hard deadline`() {
        val p = AutoSavePolicy(quietPeriodMs = 2_000, maxDelayMs = 10_000)
        var t = 1_000L
        p.onEdit(t)
        // An edit every second: the quiet period never elapses on its own.
        while (t < 10_500) {
            t += 1_000
            p.onEdit(t)
        }
        assertTrue(p.shouldSave(t), "the 10s ceiling must force a save")
    }

    @Test
    fun `saving clears the dirty state`() {
        val p = AutoSavePolicy(quietPeriodMs = 2_000)
        p.onEdit(1_000)
        p.onSaved()
        assertFalse(p.isDirty)
        assertFalse(p.shouldSave(100_000))
        assertFalse(p.shouldSaveOnExit())
    }

    @Test
    fun `the scheduled delay counts down to the quiet deadline`() {
        val p = AutoSavePolicy(quietPeriodMs = 2_000, maxDelayMs = 30_000)
        p.onEdit(1_000)
        assertEquals(2_000L, p.delayUntilSave(1_000))
        assertEquals(500L, p.delayUntilSave(2_500))
        assertEquals(0L, p.delayUntilSave(3_000))
        assertEquals(0L, p.delayUntilSave(9_999))
    }

    @Test
    fun `the delay takes whichever deadline comes first`() {
        val p = AutoSavePolicy(quietPeriodMs = 5_000, maxDelayMs = 6_000)
        p.onEdit(1_000)
        p.onEdit(3_000)
        // Quiet would fire at 8_000, but the ceiling fires at 7_000.
        assertEquals(4_000L, p.delayUntilSave(3_000))
    }

    @Test
    fun `backgrounding saves immediately without waiting for quiet`() {
        val p = AutoSavePolicy(quietPeriodMs = 10_000)
        p.onEdit(1_000)
        assertFalse(p.shouldSave(1_100))
        assertTrue(p.shouldSaveOnExit(), "unsaved work must not wait on the timer")
    }

    @Test
    fun `nonsensical configuration is rejected`() {
        assertFailsWith<IllegalArgumentException> { AutoSavePolicy(quietPeriodMs = 0) }
        assertFailsWith<IllegalArgumentException> {
            AutoSavePolicy(quietPeriodMs = 5_000, maxDelayMs = 1_000)
        }
    }
}
