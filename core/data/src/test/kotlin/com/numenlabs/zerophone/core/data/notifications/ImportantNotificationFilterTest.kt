package com.numenlabs.zerophone.core.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-Kotlin tests of the important-unread importance filter. */
class ImportantNotificationFilterTest {

    private fun notification(
        pkg: String,
        importance: Int,
        key: String = "$pkg#$importance"
    ) = ActiveNotification(key = key, packageName = pkg, importance = importance, postedAtMillis = 0L)

    @Test
    fun `high importance counts as important`() {
        val filter = ImportantNotificationFilter()
        assertTrue(filter.isImportant(notification("im.app", ImportantNotificationFilter.IMPORTANCE_HIGH)))
        assertTrue(filter.isImportant(notification("im.app", ImportantNotificationFilter.IMPORTANCE_HIGH + 1)))
    }

    @Test
    fun `low importance does not count`() {
        val filter = ImportantNotificationFilter()
        assertFalse(filter.isImportant(notification("feed.app", ImportantNotificationFilter.IMPORTANCE_DEFAULT)))
        assertFalse(filter.isImportant(notification("feed.app", ImportantNotificationFilter.IMPORTANCE_NONE)))
    }

    @Test
    fun `priority package counts regardless of importance`() {
        val filter = ImportantNotificationFilter(priorityPackages = setOf("boss.im"))
        assertTrue(filter.isImportant(notification("boss.im", ImportantNotificationFilter.IMPORTANCE_NONE)))
        assertFalse(filter.isImportant(notification("other.im", ImportantNotificationFilter.IMPORTANCE_NONE)))
    }

    @Test
    fun `filter and count agree`() {
        val filter = ImportantNotificationFilter(priorityPackages = setOf("boss.im"))
        val notifications = listOf(
            notification("boss.im", ImportantNotificationFilter.IMPORTANCE_NONE),
            notification("im.app", ImportantNotificationFilter.IMPORTANCE_HIGH),
            notification("feed.app", ImportantNotificationFilter.IMPORTANCE_DEFAULT),
            notification("feed.app2", ImportantNotificationFilter.IMPORTANCE_HIGH, key = "x")
        )
        val important = filter.filter(notifications)
        assertEquals(listOf("boss.im", "im.app", "feed.app2"), important.map { it.packageName })
        assertEquals(3, filter.countImportant(notifications))
    }

    @Test
    fun `empty input yields zero`() {
        val filter = ImportantNotificationFilter()
        assertEquals(0, filter.countImportant(emptyList()))
    }
}
