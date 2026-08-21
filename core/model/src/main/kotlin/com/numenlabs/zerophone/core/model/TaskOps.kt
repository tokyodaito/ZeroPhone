package com.numenlabs.zerophone.core.model

import kotlin.comparisons.compareBy
import kotlin.comparisons.nullsLast

/**
 * Pure Kotlin operations of the local task store: all mutation rules
 * (trimming, blank-input rejection, newest-first ordering, completion)
 * live here so every repository implementation shares one tested behaviour.
 */
object TaskOps {

    /** Adds a task with a fresh [id] at the top; blank titles are ignored (no-op). */
    fun add(tasks: List<Task>, title: String, id: String, nowMillis: Long): List<Task> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return tasks
        return listOf(Task(id = id, title = trimmed, createdAtMillis = nowMillis)) + tasks
    }

    /** Marks the task with [id] as done / undone; unknown ids are ignored. */
    fun setDone(tasks: List<Task>, id: String, done: Boolean): List<Task> =
        tasks.map { task -> if (task.id == id) task.copy(done = done) else task }

    /** Tasks still requiring attention: dated tasks by due time first, undated last. */
    fun pending(tasks: List<Task>): List<Task> =
        tasks.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.dueAtMillis })
}
