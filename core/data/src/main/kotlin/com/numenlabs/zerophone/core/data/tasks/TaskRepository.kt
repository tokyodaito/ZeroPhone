package com.numenlabs.zerophone.core.data.tasks

import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.model.TaskOps
import java.util.UUID

/**
 * Port of ZeroPhone's own local task/reminder store (there is no universal
 * external task API on minSdk 24). All mutation semantics live in the pure
 * [TaskOps] so implementations only differ in persistence.
 */
interface TaskRepository {

    suspend fun getTasks(): List<Task>

    suspend fun addTask(title: String, dueAtMillis: Long? = null)

    suspend fun setTaskDone(id: String, done: Boolean)

    /** Rewrites title and due time; blank titles are ignored. */
    suspend fun updateTask(id: String, title: String, dueAtMillis: Long?)

    suspend fun deleteTask(id: String)

    suspend fun clearCompleted()

    fun generateId(): String = UUID.randomUUID().toString()
}

/** Pure in-memory [TaskRepository] for previews and tests. */
class InMemoryTaskRepository : TaskRepository {

    private var tasks: List<Task> = emptyList()

    override suspend fun getTasks(): List<Task> = tasks.toList()

    override suspend fun addTask(title: String, dueAtMillis: Long?) {
        tasks = TaskOps.add(tasks, title, generateId(), System.currentTimeMillis(), dueAtMillis)
    }

    override suspend fun setTaskDone(id: String, done: Boolean) {
        tasks = TaskOps.setDone(tasks, id, done)
    }

    override suspend fun updateTask(id: String, title: String, dueAtMillis: Long?) {
        tasks = TaskOps.update(tasks, id, title, dueAtMillis)
    }

    override suspend fun deleteTask(id: String) {
        tasks = TaskOps.remove(tasks, id)
    }

    override suspend fun clearCompleted() {
        tasks = tasks.filter { !it.done }
    }
}
