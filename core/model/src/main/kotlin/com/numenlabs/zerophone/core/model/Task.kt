package com.numenlabs.zerophone.core.model

import kotlinx.serialization.Serializable

/**
 * A local task / reminder shown on the ZeroLauncher home screen and stored in
 * ZeroPhone's own persistence (there is no universal external task API on
 * minSdk 24, so ZeroPhone keeps its own store).
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAtMillis: Long = 0L,
    val dueAtMillis: Long? = null
)
