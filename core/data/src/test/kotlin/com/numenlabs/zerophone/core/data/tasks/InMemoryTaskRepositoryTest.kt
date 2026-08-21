package com.numenlabs.zerophone.core.data.tasks

import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.model.TaskOps
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Repository behaviour on top of the shared [TaskOps] semantics. */
class InMemoryTaskRepositoryTest {

    @Test
    fun `add then complete then clear`() = runTest {
        val repository = InMemoryTaskRepository()
        repository.addTask("Купить хлеб")
        repository.addTask("   ")
        assertEquals(1, repository.getTasks().size)
        assertEquals("Купить хлеб", repository.getTasks().first().title)

        val id = repository.getTasks().first().id
        repository.setTaskDone(id, done = true)
        assertTrue(TaskOps.pending(repository.getTasks()).isEmpty())

        repository.addTask("Новая")
        repository.clearCompleted()
        assertEquals(1, repository.getTasks().size)
        assertFalse(repository.getTasks().first().done)
    }

    @Test
    fun `generated ids are unique`() {
        val repository = InMemoryTaskRepository()
        assertTrue(repository.generateId() != repository.generateId())
    }
}
