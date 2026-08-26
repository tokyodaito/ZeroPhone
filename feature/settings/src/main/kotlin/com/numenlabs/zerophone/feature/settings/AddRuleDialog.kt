package com.numenlabs.zerophone.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.numenlabs.zerophone.core.context.LogicalCapabilities
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.context.RestrictionReason
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.RuleCondition
import com.numenlabs.zerophone.core.context.RuleDecision
import com.numenlabs.zerophone.core.context.RuleTarget
import com.numenlabs.zerophone.core.context.WeekDay
import java.util.UUID

private enum class TargetKind { CAPABILITY, PACKAGE }
private enum class ConditionKind { ALWAYS, TIME, MODE }
private enum class DecisionKind { ALLOW, LIMIT, BLOCK }

/**
 * Creates one availability rule: target (capability or package), condition
 * (always / daily time window / active mode) and decision (allow / timed
 * budget / block). Constructed rules get a "custom-" id so they never clash
 * with the seeded mode-catalog rules.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    apps: List<AppRow>,
    onAdd: (Rule) -> Unit,
    onDismiss: () -> Unit
) {
    var targetKind by remember { mutableStateOf(TargetKind.CAPABILITY) }
    var capability by remember { mutableStateOf(LogicalCapabilities.CALL) }
    var packageName by remember { mutableStateOf<String?>(null) }
    var packageQuery by remember { mutableStateOf("") }

    var conditionKind by remember { mutableStateOf(ConditionKind.ALWAYS) }
    var startMinute by remember { mutableStateOf(22 * 60) }
    var endMinute by remember { mutableStateOf(7 * 60) }
    var days by remember { mutableStateOf(WeekDay.ALL.toSet()) }
    var mode by remember { mutableStateOf(ModeIds.WORK) }

    var decisionKind by remember { mutableStateOf(DecisionKind.LIMIT) }
    var budgetInput by remember { mutableStateOf("30") }
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    if (editingStart || editingEnd) {
        val isStart = editingStart
        val initial = if (isStart) startMinute else endMinute
        val pickerState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingStart = false; editingEnd = false },
            title = {
                Text(
                    stringResource(
                        if (isStart) R.string.settings_rule_start_time
                        else R.string.settings_rule_end_time
                    )
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    if (isStart) {
                        startMinute = pickerState.hour * 60 + pickerState.minute
                    } else {
                        endMinute = pickerState.hour * 60 + pickerState.minute
                    }
                    editingStart = false
                    editingEnd = false
                }) { Text(stringResource(R.string.settings_rule_add_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { editingStart = false; editingEnd = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val budgetMinutes = budgetInput.trim().toIntOrNull()
    val valid = when {
        targetKind == TargetKind.PACKAGE && packageName == null -> false
        conditionKind == ConditionKind.TIME && startMinute == endMinute -> false
        // No days selected would silently mean "every day" — require a choice.
        conditionKind == ConditionKind.TIME && days.isEmpty() -> false
        decisionKind == DecisionKind.LIMIT &&
            (budgetMinutes == null || budgetMinutes < 1 || budgetMinutes > 720) -> false
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rules_add)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // ---- target ----
                ChipLine(label = null, chips = {
                    ChoiceChip(
                        selected = targetKind == TargetKind.CAPABILITY,
                        onClick = { targetKind = TargetKind.CAPABILITY },
                        text = stringResource(R.string.settings_rule_target_capability)
                    )
                    ChoiceChip(
                        selected = targetKind == TargetKind.PACKAGE,
                        onClick = { targetKind = TargetKind.PACKAGE },
                        text = stringResource(R.string.settings_rule_target_package)
                    )
                })
                if (targetKind == TargetKind.CAPABILITY) {
                    ChipLine(label = null, chips = {
                        LogicalCapabilities.ALL.forEach { id ->
                            ChoiceChip(
                                selected = capability == id,
                                onClick = { capability = id },
                                text = capabilityLabel(id)
                            )
                        }
                    })
                } else {
                    OutlinedTextField(
                        value = packageQuery,
                        onValueChange = { packageQuery = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.settings_priority_search_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val query = packageQuery.trim().lowercase()
                    val candidates = apps
                        .filter { query.isEmpty() || it.label.lowercase().contains(query) || it.packageName.contains(query) }
                        .take(6)
                    candidates.forEach { app ->
                        ChoiceChip(
                            selected = packageName == app.packageName,
                            onClick = { packageName = app.packageName },
                            text = app.label
                        )
                    }
                }

                // ---- condition ----
                ChipLine(label = null, chips = {
                    ChoiceChip(
                        selected = conditionKind == ConditionKind.ALWAYS,
                        onClick = { conditionKind = ConditionKind.ALWAYS },
                        text = stringResource(R.string.settings_rule_condition_always)
                    )
                    ChoiceChip(
                        selected = conditionKind == ConditionKind.TIME,
                        onClick = { conditionKind = ConditionKind.TIME },
                        text = stringResource(R.string.settings_rule_condition_time)
                    )
                    ChoiceChip(
                        selected = conditionKind == ConditionKind.MODE,
                        onClick = { conditionKind = ConditionKind.MODE },
                        text = stringResource(R.string.settings_rule_condition_mode)
                    )
                })
                if (conditionKind == ConditionKind.TIME) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { editingStart = true }) {
                            Text(formatMinute(startMinute))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("–")
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { editingEnd = true }) {
                            Text(formatMinute(endMinute))
                        }
                    }
                    ChipLine(label = null, chips = {
                        WeekDay.ALL.forEach { day ->
                            ChoiceChip(
                                selected = day in days,
                                onClick = {
                                    days = if (day in days) days - day else days + day
                                },
                                text = dayLabel(day)
                            )
                        }
                    })
                }
                if (conditionKind == ConditionKind.MODE) {
                    ChipLine(label = null, chips = {
                        ModeIds.ALL.forEach { id ->
                            ChoiceChip(
                                selected = mode == id,
                                onClick = { mode = id },
                                text = modeLabel(id)
                            )
                        }
                    })
                }

                // ---- decision ----
                ChipLine(label = null, chips = {
                    ChoiceChip(
                        selected = decisionKind == DecisionKind.ALLOW,
                        onClick = { decisionKind = DecisionKind.ALLOW },
                        text = stringResource(R.string.settings_rule_decision_allow)
                    )
                    ChoiceChip(
                        selected = decisionKind == DecisionKind.LIMIT,
                        onClick = { decisionKind = DecisionKind.LIMIT },
                        text = stringResource(R.string.settings_rule_decision_limit)
                    )
                    ChoiceChip(
                        selected = decisionKind == DecisionKind.BLOCK,
                        onClick = { decisionKind = DecisionKind.BLOCK },
                        text = stringResource(R.string.settings_rule_decision_block)
                    )
                })
                if (decisionKind == DecisionKind.LIMIT) {
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_rule_budget_minutes_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val target = when (targetKind) {
                        TargetKind.CAPABILITY -> RuleTarget.Logical(capability)
                        TargetKind.PACKAGE ->
                            packageName?.let { RuleTarget.Package(it) } ?: return@TextButton
                    }
                    val condition = when (conditionKind) {
                        ConditionKind.ALWAYS -> RuleCondition.Always
                        ConditionKind.TIME -> RuleCondition.TimeWindow(
                            startMinuteOfDay = startMinute,
                            endMinuteOfDay = endMinute,
                            days = if (days.isEmpty()) WeekDay.ALL else days
                        )
                        ConditionKind.MODE -> RuleCondition.ActiveMode(mode)
                    }
                    val decision = when (decisionKind) {
                        DecisionKind.ALLOW -> RuleDecision.Allow
                        DecisionKind.LIMIT -> RuleDecision.Restrict(
                            reason = RestrictionReason.TIME_BUDGET,
                            dailyBudgetMillis = (budgetMinutes ?: 30) * 60_000L
                        )
                        DecisionKind.BLOCK -> RuleDecision.Block
                    }
                    onAdd(Rule(id = "custom-${UUID.randomUUID()}", target, condition, decision))
                }
            ) { Text(stringResource(R.string.settings_rule_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, text: String) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun ChipLine(label: String?, chips: @Composable () -> Unit) {
    Column {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            chips()
        }
    }
}

@Composable
private fun dayLabel(day: WeekDay): String = when (day) {
    WeekDay.MONDAY -> "Пн"
    WeekDay.TUESDAY -> "Вт"
    WeekDay.WEDNESDAY -> "Ср"
    WeekDay.THURSDAY -> "Чт"
    WeekDay.FRIDAY -> "Пт"
    WeekDay.SATURDAY -> "Сб"
    WeekDay.SUNDAY -> "Вс"
}
