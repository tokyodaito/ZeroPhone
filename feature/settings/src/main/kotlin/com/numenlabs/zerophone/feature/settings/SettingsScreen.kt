package com.numenlabs.zerophone.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.numenlabs.zerophone.core.context.LogicalCapabilities
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.WeekDay
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.ui.icon.ZeroIcons

/** A launchable app presented by the settings screen (package + display label). */
data class AppRow(val packageName: String, val label: String)

/**
 * Launcher settings: default emergency-unlock duration, availability rules
 * (add/remove; richer editing lands with the rules UI iterations) and the
 * priority packages of the important-unread filter.
 */
@Composable
fun SettingsScreen(
    rules: List<Rule>,
    apps: List<AppRow>,
    priorityPackages: Set<String>,
    emergencyDurationMillis: Long,
    onBack: () -> Unit,
    onUpdateRules: (rules: List<Rule>) -> Unit,
    onTogglePriorityPackage: (packageName: String, priority: Boolean) -> Unit,
    onSetEmergencyDuration: (durationMillis: Long) -> Unit
) {
    BackHandler(onBack = onBack)
    var showAddRule by remember { mutableStateOf(false) }
    var priorityQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ZeroIcons.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back_desc)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        }

        SectionTitle(text = stringResource(R.string.settings_emergency_section))
        Text(
            text = stringResource(R.string.settings_emergency_duration_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            EmergencyWindow.PRESET_MINUTES.forEach { minutes ->
                val millis = minutes * 60_000L
                FilterChip(
                    selected = millis == emergencyDurationMillis,
                    onClick = { onSetEmergencyDuration(millis) },
                    label = { Text(stringResource(R.string.settings_duration_minutes, minutes)) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(
            text = stringResource(R.string.settings_rules_section),
            trailing = {
                TextButton(onClick = { showAddRule = true }) {
                    Icon(ZeroIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_rules_add))
                }
            }
        )
        Text(
            text = stringResource(R.string.settings_rules_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        rules.forEach { rule ->
            RuleRow(
                rule = rule,
                apps = apps,
                onDelete = { onUpdateRules(rules.filterNot { it.id == rule.id }) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(text = stringResource(R.string.settings_priority_section))
        Text(
            text = stringResource(R.string.settings_priority_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = priorityQuery,
            onValueChange = { priorityQuery = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_priority_search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val query = priorityQuery.trim().lowercase()
        val filteredApps = if (query.isEmpty()) apps else apps.filter {
            it.label.lowercase().contains(query) || it.packageName.contains(query)
        }
        filteredApps.forEach { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = app.packageName in priorityPackages,
                    onCheckedChange = { checked ->
                        onTogglePriorityPackage(app.packageName, checked)
                    }
                )
            }
        }
    }

    if (showAddRule) {
        AddRuleDialog(
            apps = apps,
            onAdd = { rule ->
                onUpdateRules(rules + rule)
                showAddRule = false
            },
            onDismiss = { showAddRule = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    apps: List<AppRow>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = describeTarget(rule, apps),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = describeRule(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ZeroIcons.Close,
                    contentDescription = stringResource(R.string.settings_rule_delete_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun describeTarget(rule: Rule, apps: List<AppRow>): String = when (val target = rule.target) {
    is com.numenlabs.zerophone.core.context.RuleTarget.Package ->
        apps.firstOrNull { it.packageName == target.packageName }?.label ?: target.packageName
    is com.numenlabs.zerophone.core.context.RuleTarget.Logical -> capabilityLabel(target.name)
    com.numenlabs.zerophone.core.context.RuleTarget.All ->
        stringResource(R.string.settings_rule_target_all)
}

@Composable
internal fun describeRule(rule: Rule): String {
    val condition = when (val c = rule.condition) {
        com.numenlabs.zerophone.core.context.RuleCondition.Always ->
            stringResource(R.string.settings_rule_condition_always)
        is com.numenlabs.zerophone.core.context.RuleCondition.TimeWindow -> {
            val range = stringResource(
                R.string.settings_rule_time_range,
                formatMinute(c.startMinuteOfDay),
                formatMinute(c.endMinuteOfDay)
            )
            if (c.days == WeekDay.ALL) range
            else range + " · " + stringResource(R.string.settings_rule_days_count, c.days.size)
        }
        is com.numenlabs.zerophone.core.context.RuleCondition.CalendarBusy -> ""
        is com.numenlabs.zerophone.core.context.RuleCondition.ActiveMode -> modeLabel(c.mode)
    }
    val decision = when (val d = rule.decision) {
        com.numenlabs.zerophone.core.context.RuleDecision.Allow ->
            stringResource(R.string.settings_rule_decision_allow)
        is com.numenlabs.zerophone.core.context.RuleDecision.Restrict ->
            d.dailyBudgetMillis?.let {
                stringResource(R.string.settings_rule_budget_minutes, (it / 60_000L).toInt())
            } ?: stringResource(R.string.settings_rule_decision_limit)
        is com.numenlabs.zerophone.core.context.RuleDecision.ContextualAllow ->
            stringResource(R.string.settings_rule_decision_allow)
        com.numenlabs.zerophone.core.context.RuleDecision.Block ->
            stringResource(R.string.settings_rule_decision_block)
    }
    return if (condition.isBlank()) decision else "$condition · $decision"
}

@Composable
internal fun capabilityLabel(capabilityId: String): String = when (capabilityId) {
    LogicalCapabilities.CALL -> stringResource(R.string.settings_capability_call)
    LogicalCapabilities.MESSAGE -> stringResource(R.string.settings_capability_message)
    LogicalCapabilities.NAVIGATE -> stringResource(R.string.settings_capability_navigate)
    LogicalCapabilities.PAY -> stringResource(R.string.settings_capability_pay)
    LogicalCapabilities.CAMERA -> stringResource(R.string.settings_capability_camera)
    else -> capabilityId
}

@Composable
internal fun modeLabel(mode: String): String = when (mode) {
    ModeIds.REST -> stringResource(R.string.settings_mode_rest)
    ModeIds.FOCUS -> stringResource(R.string.settings_mode_focus)
    else -> stringResource(R.string.settings_mode_work)
}

internal fun formatMinute(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
