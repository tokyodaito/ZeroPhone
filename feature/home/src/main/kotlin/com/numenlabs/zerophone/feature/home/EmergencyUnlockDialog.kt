package com.numenlabs.zerophone.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.ui.icon.ZeroIcons

/**
 * Emergency-unlock dialog with a configurable window duration: presets
 * (5/15/30/60 minutes) or a custom duration in minutes. The selected
 * duration is persisted by the caller so it becomes the new default.
 */
@Composable
fun EmergencyUnlockDialog(
    currentDurationMillis: Long,
    onConfirm: (durationMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    // null = no preset touched yet: start from the persisted duration when it
    // is one of the presets, otherwise from the 30-minute default.
    var presetMinutes by remember {
        mutableStateOf(
            EmergencyWindow.PRESET_MINUTES.firstOrNull { it * 60_000L == currentDurationMillis } ?: 30L
        )
    }
    var customInput by remember { mutableStateOf("") }

    val customMinutes = customInput.trim().toLongOrNull()
    val effectiveMillis = when {
        customMinutes != null -> EmergencyWindow.sanitizeDurationMillis(customMinutes * 60_000L)
        else -> presetMinutes * 60_000L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ZeroIcons.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = { Text(stringResource(R.string.emergency_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.emergency_dialog_text, effectiveMillis / 60_000L))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmergencyWindow.PRESET_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = customInput.isBlank() && minutes == presetMinutes,
                            onClick = {
                                presetMinutes = minutes
                                customInput = ""
                            },
                            label = { Text(stringResource(R.string.emergency_duration_minutes, minutes)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { value -> customInput = value.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.emergency_duration_custom)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.emergency_duration_custom_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(effectiveMillis) }) {
                Text(stringResource(R.string.emergency_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.emergency_dialog_dismiss))
            }
        }
    )
}
