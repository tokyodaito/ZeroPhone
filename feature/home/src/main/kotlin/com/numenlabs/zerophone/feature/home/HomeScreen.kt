package com.numenlabs.zerophone.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.policy.PolicyApplier

@Composable
fun HomeScreen(
    apps: List<PolicyApplier.LauncherApp>,
    allowlist: Set<String>,
    selfPackage: String,
    deviceOwner: Boolean,
    window: EmergencyWindow,
    onLaunch: (packageName: String) -> Unit,
    onOpenAllowlist: () -> Unit,
    onEmergencyUnlock: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (deviceOwner) stringResource(R.string.home_device_owner_active)
            else stringResource(R.string.home_device_owner_inactive),
            style = MaterialTheme.typography.bodySmall,
            color = if (deviceOwner) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
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
}

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
