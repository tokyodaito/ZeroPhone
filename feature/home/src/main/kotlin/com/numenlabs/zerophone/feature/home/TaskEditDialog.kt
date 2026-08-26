package com.numenlabs.zerophone.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.numenlabs.zerophone.core.model.Task
import java.util.Calendar

/**
 * Edit an existing task: rename, set/clear a due time (today, rolling to
 * tomorrow when the picked time already passed) or delete it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(
    task: Task,
    nowMillis: Long,
    onSave: (title: String, dueAtMillis: Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var dueAtMillis by remember(task.id) { mutableStateOf(task.dueAtMillis) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val initial = dueAtMillis?.let { due ->
            Calendar.getInstance().apply { timeInMillis = due }
        }
        val pickerState = rememberTimePickerState(
            initialHour = initial?.get(Calendar.HOUR_OF_DAY) ?: 9,
            initialMinute = initial?.get(Calendar.MINUTE) ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.home_task_pick_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    dueAtMillis = nextOccurrence(pickerState.hour, pickerState.minute, nowMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.home_task_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.emergency_dialog_dismiss))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_edit_task_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_task_title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dueAtMillis?.let {
                            stringResource(
                                R.string.home_task_due_format,
                                formatDue(it, nowMillis)
                            )
                        } ?: stringResource(R.string.home_task_no_due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (dueAtMillis != null) {
                        TextButton(onClick = { dueAtMillis = null }) {
                            Text(stringResource(R.string.home_task_clear_due))
                        }
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(stringResource(R.string.home_task_pick_time))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, dueAtMillis) }
            ) { Text(stringResource(R.string.home_task_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(R.string.home_task_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.emergency_dialog_dismiss))
                }
            }
        }
    )
}

/** Today at hour:minute, rolling to tomorrow when that moment already passed. */
internal fun nextOccurrence(hour: Int, minute: Int, nowMillis: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= nowMillis) calendar.add(Calendar.DAY_OF_YEAR, 1)
    return calendar.timeInMillis
}

internal fun formatDue(dueMillis: Long, nowMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        dueMillis,
        nowMillis,
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()
