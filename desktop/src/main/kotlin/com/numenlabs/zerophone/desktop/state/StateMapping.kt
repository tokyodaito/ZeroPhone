package com.numenlabs.zerophone.desktop.state

import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.core.model.CapabilityAvailability
import com.numenlabs.zerophone.core.model.EmergencyWindow

/**
 * Pure mapping from `:core:model` types held in [DesktopAppState] to plain
 * view models for rendering. No Compose/framework dependencies — unit-tested
 * as plain functions.
 */

/** One row of the dashboard availability list. */
data class CapabilityRow(
    val id: String,
    val label: String,
    val kind: com.numenlabs.zerophone.core.model.CapabilityKind,
    val state: Availability,
    val detail: String? = null,
)

/** Aggregate the dashboard renders: mode, emergency window, availability rows. */
data class DashboardModel(
    val activeMode: String? = null,
    val emergency: EmergencyWindow = EmergencyWindow.None,
    val generatedAtMillis: Long? = null,
    val deviceId: String? = null,
    val rows: List<CapabilityRow> = emptyList(),
) {
    val countByState: Map<Availability, Int>
        get() = rows.groupingBy { it.state }.eachCount()
}

/** One inbox entry as shown in the list. */
data class InboxEntry(
    val id: String,
    val url: String,
    val title: String?,
    val sourceDeviceName: String?,
    val receivedAtMillis: Long,
    val sentAtMillis: Long,
    val isOpened: Boolean,
)

fun InboxItem.toEntry(): InboxEntry = InboxEntry(
    id = link.id,
    url = link.url,
    title = link.title,
    sourceDeviceName = link.sourceDeviceName,
    receivedAtMillis = receivedAtMillis,
    sentAtMillis = link.sentAtMillis,
    isOpened = isOpened,
)

fun DesktopAppState.toInboxEntries(): List<InboxEntry> = inbox.map { it.toEntry() }

fun DesktopAppState.toDashboardModel(): DashboardModel {
    val snapshot = snapshot ?: return DashboardModel()
    return DashboardModel(
        activeMode = snapshot.activeMode,
        emergency = if (snapshot.emergencyRemainingMillis > EmergencyWindow.NONE_DEADLINE) {
            EmergencyWindow.Active(snapshot.emergencyRemainingMillis)
        } else {
            EmergencyWindow.None
        },
        generatedAtMillis = snapshot.generatedAtMillis,
        deviceId = snapshot.deviceId,
        rows = snapshot.capabilities.map { it.toRow() },
    )
}

fun CapabilityAvailability.toRow(): CapabilityRow = CapabilityRow(
    id = id,
    label = label ?: id,
    kind = kind,
    state = state,
    detail = when (state) {
        Availability.RESTRICTED -> restrictionReason
        Availability.CONTEXTUAL -> contextCondition
        Availability.TEMPORARILY_AVAILABLE ->
            remainingMillis?.let { "remaining ${it / 1000}s" }
        else -> null
    },
)
