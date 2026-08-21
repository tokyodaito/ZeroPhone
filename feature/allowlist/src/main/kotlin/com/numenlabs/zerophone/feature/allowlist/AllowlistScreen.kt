package com.numenlabs.zerophone.feature.allowlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.numenlabs.zerophone.core.policy.PolicyApplier

@Composable
fun AllowlistScreen(
    apps: List<PolicyApplier.LauncherApp>,
    allowlist: Set<String>,
    deviceOwner: Boolean,
    onBack: () -> Unit,
    onToggle: (packageName: String, allowed: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.allowlist_back)) }
            Text(stringResource(R.string.allowlist_title), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.allowlist_search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (!deviceOwner) {
            Text(
                text = stringResource(R.string.allowlist_not_device_owner),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        val visibleApps = apps
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        if (visibleApps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.allowlist_empty else R.string.allowlist_no_results
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val checked = allowlist.contains(app.packageName)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName, !checked) }
                            .padding(vertical = 4.dp)
                    ) {
                        Image(
                            bitmap = remember(app.packageName) {
                                app.icon.toBitmap(72, 72).asImageBitmap()
                            },
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { allowed -> onToggle(app.packageName, allowed) }
                        )
                    }
                }
            }
        }
    }
}
