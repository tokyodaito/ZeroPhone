package com.numenlabs.zerophone.core.data.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.model.TaskOps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.tasksDataStore: DataStore<Preferences> by preferencesDataStore(name = "zerophone_tasks")

/**
 * Structured persistence of the local task store (Preferences DataStore,
 * JSON-encoded task list). Decode failures degrade to an empty store — never
 * a crash; mutation semantics are shared with every other implementation via
 * [TaskOps].
 */
class DataStoreTaskRepository(context: Context) : TaskRepository {

    private val dataStore = context.applicationContext.tasksDataStore

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getTasks(): List<Task> =
        dataStore.data.map { prefs ->
            prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
        }.first()

    override suspend fun addTask(title: String, dueAtMillis: Long?) {
        dataStore.edit { prefs ->
            val tasks = prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_TASKS] = encode(
                TaskOps.add(tasks, title, generateId(), System.currentTimeMillis(), dueAtMillis)
            )
        }
    }

    override suspend fun setTaskDone(id: String, done: Boolean) {
        dataStore.edit { prefs ->
            val tasks = prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_TASKS] = encode(TaskOps.setDone(tasks, id, done))
        }
    }

    override suspend fun updateTask(id: String, title: String, dueAtMillis: Long?) {
        dataStore.edit { prefs ->
            val tasks = prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_TASKS] = encode(TaskOps.update(tasks, id, title, dueAtMillis))
        }
    }

    override suspend fun deleteTask(id: String) {
        dataStore.edit { prefs ->
            val tasks = prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_TASKS] = encode(TaskOps.remove(tasks, id))
        }
    }

    override suspend fun clearCompleted() {
        dataStore.edit { prefs ->
            val tasks = prefs[KEY_TASKS]?.let { decode(it) } ?: emptyList()
            prefs[KEY_TASKS] = encode(tasks.filter { !it.done })
        }
    }

    private fun decode(value: String): List<Task>? = try {
        json.decodeFromString(ListSerializer(Task.serializer()), value)
    } catch (_: Exception) {
        null
    }

    private fun encode(tasks: List<Task>): String =
        json.encodeToString(ListSerializer(Task.serializer()), tasks)

    private companion object {
        val KEY_TASKS = stringPreferencesKey("tasks_json")
    }
}
