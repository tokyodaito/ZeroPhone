package com.numenlabs.zerophone.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.model.CalendarEvent
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.policy.PolicyApplier
import java.text.DateFormat
import java.util.Date

/**
 * The ZeroLauncher home screen — a contextual tool, not an app drawer: clock,
 * active mode, next calendar event, important unread count, tasks/reminders
 * and engine-gated quick actions; below them the allowlist app grid (the only
 * apps shown, per the launcher contract). No third-party widgets, feeds,
 * recommendations or app search.
 */
@Composable
fun HomeScreen(
    apps: List<PolicyApplier.LauncherApp>,
    allowlist: Set<String>,
    selfPackage: String,
    deviceOwner: Boolean,
    window: EmergencyWindow,
    nowMillis: Long,
    nextEvent: CalendarEvent?,
    tasks: List<Task>,
    importantUnreadCount: Int,
    activeMode: String,
    actionStates: Map<QuickAction, AvailabilityState>,
    onLaunch: (packageName: String) -> Unit,
    onOpenAllowlist: () -> Unit,
    onEmergencyUnlock: () -> Unit,
    onSetMode: (mode: String) -> Unit,
    onQuickAction: (action: QuickAction) -> Unit,
    onSendToPc: (url: String) -> Unit,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showSendToPcDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatClock(context, nowMillis),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = if (deviceOwner) stringResource(R.string.home_device_owner_active)
                    else stringResource(R.string.home_device_owner_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deviceOwner) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.home_mode_label),
                    style = MaterialTheme.typography.labelSmall
                )
                ModeSwitcher(activeMode = activeMode, onSetMode = onSetMode)
            }
        }
        if (!deviceOwner) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_not_device_owner_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (window is EmergencyWindow.Active) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.home_emergency_active_banner,
                        formatRemaining(window.remainingMillis)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ContextCard(
            nowMillis = nowMillis,
            nextEvent = nextEvent,
            tasks = tasks,
            importantUnreadCount = importantUnreadCount,
            onAddTask = onAddTask,
            onToggleTask = onToggleTask
        )
        Spacer(Modifier.height(8.dp))
        QuickActionsRow(
            actionStates = actionStates,
            onQuickAction = { action ->
                if (action == QuickAction.SEND_TO_PC) showSendToPcDialog = true else onQuickAction(action)
            }
        )
        Spacer(Modifier.height(8.dp))
        val visibleApps = apps.filter {
            it.packageName == selfPackage || allowlist.contains(it.packageName)
        }
        if (visibleApps.isEmpty()) {
            Text(
                stringResource(R.string.home_no_allowed_apps),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(visibleApps, key = { it.packageName }) { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onLaunch(app.packageName) }
                        .padding(4.dp)
                ) {
                    Image(
                        bitmap = remember(app.packageName) {
                            app.icon.toBitmap(96, 96).asImageBitmap()
                        },
                        contentDescription = app.label,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onOpenAllowlist,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.home_open_allowlist)) }
            Button(
                onClick = onEmergencyUnlock,
                enabled = deviceOwner,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (window) {
                        is EmergencyWindow.Active -> stringResource(
                            R.string.home_emergency_unlock_active,
                            formatRemaining(window.remainingMillis)
                        )
                        else -> stringResource(R.string.home_emergency_unlock_button)
                    },
                    maxLines = 2
                )
            }
        }
    }

    if (showSendToPcDialog) {
        SendToPcDialog(
            onSend = { url ->
                showSendToPcDialog = false
                onSendToPc(url)
            },
            onDismiss = { showSendToPcDialog = false }
        )
    }
}

/** "Отправить на ПК": URL input handed to the sync wiring, never auto-sent. */
@Composable
private fun SendToPcDialog(
    onSend: (url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.send_to_pc_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.send_to_pc_dialog_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.send_to_pc_dialog_cancel))
                    }
                    Button(
                        onClick = { onSend(url.trim()) },
                        enabled = url.startsWith("http://") || url.startsWith("https://")
                    ) {
                        Text(stringResource(R.string.send_to_pc_dialog_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcher(activeMode: String, onSetMode: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ModeIds.ALL.forEach { mode ->
            FilterChip(
                selected = mode == activeMode,
                onClick = { onSetMode(mode) },
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                ModeIds.REST -> R.string.mode_rest
                                ModeIds.FOCUS -> R.string.mode_focus
                                else -> R.string.mode_work
                            }
                        )
                    )
                }
            )
        }
    }
}

/** Useful information only: next event, important unread, tasks/reminders. */
@Composable
private fun ContextCard(
    nowMillis: Long,
    nextEvent: CalendarEvent?,
    tasks: List<Task>,
    importantUnreadCount: Int,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (nextEvent != null) {
                Text(
                    text = stringResource(
                        R.string.home_next_event,
                        nextEvent.title,
                        formatEventTime(nowMillis, nextEvent.beginMillis)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                nextEvent.location?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.home_no_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (importantUnreadCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.home_important_unread,
                        importantUnreadCount,
                        importantUnreadCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            TaskList(tasks = tasks, onAddTask = onAddTask, onToggleTask = onToggleTask)
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit
) {
    var newTaskTitle by remember { mutableStateOf("") }
    Text(
        text = stringResource(R.string.home_tasks_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp)
    )
    val pending = tasks.filter { !it.done }.take(MAX_TASKS_SHOWN)
    if (pending.isEmpty()) {
        Text(
            text = stringResource(R.string.home_no_tasks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        pending.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = false,
                    onCheckedChange = { onToggleTask(task.id, true) }
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTaskTitle,
            onValueChange = { newTaskTitle = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.home_add_task_hint)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = {
                onAddTask(newTaskTitle)
                newTaskTitle = ""
            }
        ) { Text(stringResource(R.string.home_add_task)) }
    }
}

@Composable
private fun QuickActionsRow(
    actionStates: Map<QuickAction, AvailabilityState>,
    onQuickAction: (QuickAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickAction.entries.forEach { action ->
            val state = actionStates[action]
            OutlinedButton(
                onClick = { onQuickAction(action) },
                enabled = state != null && state.allowsQuickAction
            ) {
                Text(
                    text = stringResource(
                        when (action) {
                            QuickAction.CALL -> R.string.quick_action_call
                            QuickAction.MESSAGE -> R.string.quick_action_message
                            QuickAction.NAVIGATE -> R.string.quick_action_navigate
                            QuickAction.PAY -> R.string.quick_action_pay
                            QuickAction.CAMERA -> R.string.quick_action_camera
                            QuickAction.SEND_TO_PC -> R.string.quick_action_send_to_pc
                        }
                    )
                )
            }
        }
    }
}

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatClock(context: android.content.Context, nowMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, context.resources.configuration.locales[0])
        .format(Date(nowMillis))

private fun formatEventTime(nowMillis: Long, eventMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        eventMillis,
        nowMillis,
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

private const val MAX_TASKS_SHOWN = 3
