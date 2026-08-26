package com.numenlabs.zerophone.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-Kotlin tests of the local task-store operations. */
class TaskOpsTest {

    @Test
    fun `add prepends trimmed task`() {
        val tasks = TaskOps.add(emptyList(), "  Позвонить маме  ", "t1", nowMillis = 100L)
        assertEquals(listOf(Task("t1", "Позвонить маме", done = false, createdAtMillis = 100L)), tasks)

        val grown = TaskOps.add(tasks, "Вторая", "t2", nowMillis = 200L)
        assertEquals("t2", grown.first().id)
        assertEquals(2, grown.size)
    }

    @Test
    fun `add ignores blank titles`() {
        val tasks = listOf(Task("t1", "Есть"))
        assertEquals(tasks, TaskOps.add(tasks, "   ", "t2", nowMillis = 0L))
        assertEquals(tasks, TaskOps.add(tasks, "", "t2", nowMillis = 0L))
    }

    @Test
    fun `setDone toggles only the target task`() {
        val tasks = listOf(Task("t1", "A"), Task("t2", "B"))
        val updated = TaskOps.setDone(tasks, "t2", done = true)
        assertEquals(false, updated[0].done)
        assertEquals(true, updated[1].done)
        // toggling back
        assertEquals(false, TaskOps.setDone(updated, "t2", done = false)[1].done)
    }

    @Test
    fun `setDone ignores unknown id`() {
        val tasks = listOf(Task("t1", "A"))
        assertEquals(tasks, TaskOps.setDone(tasks, "missing", done = true))
    }

    @Test
    fun `pending filters done tasks and sorts by due time with undated last`() {
        val tasks = listOf(
            Task("undated", "C"),
            Task("late", "A", dueAtMillis = 200L),
            Task("done", "D", done = true),
            Task("soon", "B", dueAtMillis = 100L)
        )
        val pending = TaskOps.pending(tasks)
        assertEquals(listOf("soon", "late", "undated"), pending.map { it.id })
        assertTrue(pending.none { it.done })
    }

    @Test
    fun `add accepts an optional due time`() {
        val tasks = TaskOps.add(emptyList(), "Дедлайн", "t1", nowMillis = 1L, dueAtMillis = 500L)
        assertEquals(500L, tasks.single().dueAtMillis)
    }

    @Test
    fun `update rewrites title and due of the target task only`() {
        val tasks = listOf(Task("t1", "Старое"), Task("t2", "Другая", dueAtMillis = 100L))
        val updated = TaskOps.update(tasks, "t1", "  Новое  ", dueAtMillis = 900L)
        assertEquals(Task("t1", "Новое", dueAtMillis = 900L), updated[0])
        assertEquals(Task("t2", "Другая", dueAtMillis = 100L), updated[1])
    }

    @Test
    fun `update ignores blank titles and unknown ids`() {
        val tasks = listOf(Task("t1", "Есть"))
        assertEquals(tasks, TaskOps.update(tasks, "t1", "   ", dueAtMillis = null))
        assertEquals(tasks, TaskOps.update(tasks, "missing", "Новое", dueAtMillis = null))
    }

    @Test
    fun `remove deletes only the target task`() {
        val tasks = listOf(Task("t1", "A"), Task("t2", "B"))
        assertEquals(listOf(Task("t2", "B")), TaskOps.remove(tasks, "t1"))
        assertEquals(tasks, TaskOps.remove(tasks, "missing"))
    }
}
