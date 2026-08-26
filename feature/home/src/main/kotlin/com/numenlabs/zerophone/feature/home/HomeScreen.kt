package com.numenlabs.zerophone.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.model.CalendarEvent
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.model.TaskOps
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.ui.icon.ZeroIcons
import kotlinx.coroutines.delay
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
    appsLoading: Boolean,
    emergencyDeadline: Long,
    nextEvent: CalendarEvent?,
    tasks: List<Task>,
    importantUnreadCount: Int,
    activeMode: String,
    actionStates: Map<QuickAction, AvailabilityState>,
    budgetRemaining: Map<QuickAction, Long?>,
    notificationAccessEnabled: Boolean,
    exactAlarmsDisabled: Boolean,
    usageTrackingMissing: Boolean,
    onLaunch: (packageName: String) -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmergencyUnlock: () -> Unit,
    onRelockNow: () -> Unit,
    onSetMode: (mode: String) -> Unit,
    onQuickAction: (action: QuickAction) -> Unit,
    onGrantCapability: (action: QuickAction) -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit,
    onUpdateTask: (id: String, title: String, dueAtMillis: Long?) -> Unit,
    onDeleteTask: (id: String) -> Unit,
    onClearCompleted: () -> Unit
) {
    // Future-only: a leftover expired deadline (e.g. admin revoked mid-window)
    // must not render a stuck "00:00" banner and re-lock button.
    val activeEmergency = emergencyDeadline > EmergencyWindow.NONE_DEADLINE &&
        emergencyDeadline > System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        DateTimeHeading()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                imageVector = if (deviceOwner) ZeroIcons.ShieldCheck else ZeroIcons.Warning,
                contentDescription = null,
                tint = if (deviceOwner) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (deviceOwner) stringResource(R.string.home_device_owner_active)
                else stringResource(R.string.home_device_owner_inactive),
                style = MaterialTheme.typography.labelMedium,
                color = if (deviceOwner) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = ZeroIcons.Settings,
                    contentDescription = stringResource(R.string.home_open_settings_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ModeSwitcher(
            activeMode = activeMode,
            onSetMode = onSetMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        )

        Column(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .animateContentSize()
        ) {
            AnimatedVisibility(
                visible = !deviceOwner,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                StatusBanner(
                    icon = ZeroIcons.Warning,
                    text = stringResource(R.string.home_not_device_owner_hint),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    onContainerColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            AnimatedVisibility(
                visible = deviceOwner && exactAlarmsDisabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                StatusBanner(
                    icon = ZeroIcons.Warning,
                    text = stringResource(R.string.home_exact_alarm_banner),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    onContainerColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionLabel = stringResource(R.string.home_exact_alarm_action),
                    onAction = onRequestExactAlarms,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            AnimatedVisibility(
                visible = deviceOwner && usageTrackingMissing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                StatusBanner(
                    icon = ZeroIcons.Timer,
                    text = stringResource(R.string.home_usage_access_banner),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionLabel = stringResource(R.string.home_usage_access_action),
                    onAction = onOpenUsageAccessSettings,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            AnimatedVisibility(
                visible = !notificationAccessEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                StatusBanner(
                    icon = ZeroIcons.Notifications,
                    text = stringResource(R.string.home_notification_access_banner),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionLabel = stringResource(R.string.home_notification_access_action),
                    onAction = onOpenNotificationAccessSettings,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            AnimatedVisibility(
                visible = activeEmergency,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                EmergencyCountdownBanner(
                    deadlineMillis = emergencyDeadline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        ContextCard(
            nextEvent = nextEvent,
            tasks = tasks,
            importantUnreadCount = importantUnreadCount,
            onAddTask = onAddTask,
            onToggleTask = onToggleTask,
            onUpdateTask = onUpdateTask,
            onDeleteTask = onDeleteTask,
            onClearCompleted = onClearCompleted
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel(text = stringResource(R.string.home_quick_actions_section))
        Spacer(Modifier.height(8.dp))
        QuickActionsRow(
            actionStates = actionStates,
            budgetRemaining = budgetRemaining,
            onQuickAction = onQuickAction,
            onGrantCapability = onGrantCapability
        )

        val visibleApps = apps.filter {
            it.packageName == selfPackage || allowlist.contains(it.packageName)
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel(text = stringResource(R.string.home_apps_section))
        Spacer(Modifier.height(4.dp))
        if (appsLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (visibleApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = ZeroIcons.AppsGrid,
                    text = stringResource(R.string.home_no_allowed_apps)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    AppGridCell(
                        app = app,
                        onLaunch = onLaunch,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onOpenAllowlist,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(ZeroIcons.AppsGrid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_open_allowlist), maxLines = 1)
            }
            if (activeEmergency) {
                // Early re-lock: the active window is otherwise irreversible
                // for its whole duration.
                Button(
                    onClick = onRelockNow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(ZeroIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_relock_now), maxLines = 2)
                }
            } else {
                Button(
                    onClick = onEmergencyUnlock,
                    enabled = deviceOwner,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(ZeroIcons.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_emergency_unlock_button), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Date + clock with an isolated one-second tick: only this heading recomposes
 * per second, the rest of the home screen stays static.
 */
@Composable
private fun DateTimeHeading() {
    val context = LocalContext.current
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            // Align to wall-clock second boundaries so the tick never drifts.
            delay(1_000 - value % 1_000)
        }
    }
    Text(
        text = formatDate(context, nowMillis),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = formatClock(context, nowMillis),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Emergency-unlock banner with a live countdown. The tick lives here (not in
 * the parent) so the per-second recomposition stays inside this banner; the
 * actual re-lock is driven by the persisted deadline, not by this UI.
 */
@Composable
private fun EmergencyCountdownBanner(deadlineMillis: Long, modifier: Modifier = Modifier) {
    val remainingMillis by produceState(
        initialValue = deadlineMillis - System.currentTimeMillis(),
        key1 = deadlineMillis
    ) {
        while (true) {
            value = deadlineMillis - System.currentTimeMillis()
            if (value <= 0L) return@produceState
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    StatusBanner(
        icon = ZeroIcons.Timer,
        text = stringResource(
            R.string.home_emergency_active_banner,
            formatRemaining(remainingMillis.coerceAtLeast(0L))
        ),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
    )
}

@Composable
private fun StatusBanner(
    icon: ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = onContainerColor,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModeSwitcher(activeMode: String, onSetMode: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
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
    nextEvent: CalendarEvent?,
    tasks: List<Task>,
    importantUnreadCount: Int,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit,
    onUpdateTask: (id: String, title: String, dueAtMillis: Long?) -> Unit,
    onDeleteTask: (id: String) -> Unit,
    onClearCompleted: () -> Unit
) {
    // Minute-granularity "now" for the relative event time — enough for
    // DateUtils.getRelativeTimeSpanString without a per-second recomposition.
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContextIcon(icon = ZeroIcons.Event)
                Spacer(Modifier.width(12.dp))
                if (nextEvent != null) {
                    Column {
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
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = importantUnreadCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContextIcon(icon = ZeroIcons.Notifications)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_important_unread,
                            importantUnreadCount,
                            importantUnreadCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TaskList(
                nowMillis = nowMillis,
                tasks = tasks,
                onAddTask = onAddTask,
                onToggleTask = onToggleTask,
                onUpdateTask = onUpdateTask,
                onDeleteTask = onDeleteTask,
                onClearCompleted = onClearCompleted
            )
        }
    }
}

@Composable
private fun ContextIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TaskList(
    nowMillis: Long,
    tasks: List<Task>,
    onAddTask: (title: String) -> Unit,
    onToggleTask: (id: String, done: Boolean) -> Unit,
    onUpdateTask: (id: String, title: String, dueAtMillis: Long?) -> Unit,
    onDeleteTask: (id: String) -> Unit,
    onClearCompleted: () -> Unit
) {
    var newTaskTitle by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    editing?.let { task ->
        TaskEditDialog(
            task = task,
            nowMillis = nowMillis,
            onSave = { title, due ->
                onUpdateTask(task.id, title, due)
                editing = null
            },
            onDelete = {
                onDeleteTask(task.id)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    val doneCount = tasks.count { it.done }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = ZeroIcons.TaskAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_tasks_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (doneCount > 0) {
            TextButton(onClick = onClearCompleted) {
                Text(stringResource(R.string.home_clear_completed, doneCount))
            }
        }
    }
    val pending = remember(tasks) { TaskOps.pending(tasks) }
    val visible = if (expanded) pending else pending.take(COLLAPSED_TASKS)
    if (pending.isEmpty()) {
        Text(
            text = stringResource(R.string.home_no_tasks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp)
        )
    } else {
        visible.forEach { task ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { editing = task }
            ) {
                Checkbox(
                    checked = false,
                    onCheckedChange = { onToggleTask(task.id, true) },
                    modifier = Modifier.size(40.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    task.dueAtMillis?.let { due ->
                        Text(
                            text = stringResource(
                                R.string.home_task_due_short,
                                formatDue(due, nowMillis)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (due <= nowMillis) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onDeleteTask(task.id) }) {
                    Icon(
                        imageVector = ZeroIcons.Close,
                        contentDescription = stringResource(R.string.home_task_delete_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (pending.size > COLLAPSED_TASKS) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    if (expanded) stringResource(R.string.home_task_show_less)
                    else stringResource(R.string.home_task_show_more, pending.size - COLLAPSED_TASKS)
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp)
    ) {
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
        ) {
            Icon(ZeroIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.home_add_task))
        }
    }
}

@Composable
private fun QuickActionsRow(
    actionStates: Map<QuickAction, AvailabilityState>,
    budgetRemaining: Map<QuickAction, Long?>,
    onQuickAction: (QuickAction) -> Unit,
    onGrantCapability: (QuickAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAction.entries.forEach { action ->
            val state = actionStates[action]
            QuickActionCard(
                action = action,
                enabled = state != null && state.allowsQuickAction,
                budgetRemainingMillis = budgetRemaining[action],
                onClick = { onQuickAction(action) },
                onLongPress = { onGrantCapability(action) }
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    enabled: Boolean,
    budgetRemainingMillis: Long?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "quickActionPressScale"
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .widthIn(min = 88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                // Always interactive: a long-press grant must work even on a
                // blocked card (e.g. an exhausted daily budget).
                onClick = { if (enabled) onClick() },
                onLongClick = onLongPress
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = quickActionIcon(action),
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(quickActionLabel(action)),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            if (budgetRemainingMillis != null) {
                Text(
                    text = if (budgetRemainingMillis > 0L) {
                        stringResource(
                            R.string.home_budget_remaining,
                            ((budgetRemainingMillis + 59_999L) / 60_000L).toInt()
                        )
                    } else {
                        stringResource(R.string.home_budget_exhausted)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (budgetRemainingMillis > 0L) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppGridCell(
    app: PolicyApplier.LauncherApp,
    onLaunch: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "appCellPressScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onLaunch(app.packageName) }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = remember(app.packageName) {
                    app.icon.toBitmap(96, 96).asImageBitmap()
                },
                contentDescription = app.label,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

private fun quickActionIcon(action: QuickAction): ImageVector = when (action) {
    QuickAction.CALL -> ZeroIcons.Call
    QuickAction.MESSAGE -> ZeroIcons.Message
    QuickAction.NAVIGATE -> ZeroIcons.Navigate
    QuickAction.PAY -> ZeroIcons.Pay
    QuickAction.CAMERA -> ZeroIcons.Camera
}

/** @StringRes label of a quick action, shared with the activity-level snackbar. */
fun quickActionLabel(action: QuickAction): Int = when (action) {
    QuickAction.CALL -> R.string.quick_action_call
    QuickAction.MESSAGE -> R.string.quick_action_message
    QuickAction.NAVIGATE -> R.string.quick_action_navigate
    QuickAction.PAY -> R.string.quick_action_pay
    QuickAction.CAMERA -> R.string.quick_action_camera
}

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatClock(context: android.content.Context, nowMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, context.resources.configuration.locales[0])
        .format(Date(nowMillis))

private fun formatDate(context: android.content.Context, nowMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.LONG, context.resources.configuration.locales[0])
        .format(Date(nowMillis))

private fun formatEventTime(nowMillis: Long, eventMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        eventMillis,
        nowMillis,
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

private const val COLLAPSED_TASKS = 5
