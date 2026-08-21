package com.numenlabs.zerophone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.numenlabs.zerophone.policy.EmergencyWindow
import com.numenlabs.zerophone.policy.PolicyApplier
import com.numenlabs.zerophone.ui.theme.ZeroPhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeroPhoneTheme {
                ZeroPhoneApp()
            }
        }
    }
}

private enum class Screen { Home, Allowlist }

@Composable
private fun ZeroPhoneApp() {
    val context = LocalContext.current
    val applier = remember { PolicyApplier(context) }
    val selfPackage = remember { applier.selfPackageName }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.Home) }
    var apps by remember { mutableStateOf<List<PolicyApplier.LauncherApp>>(emptyList()) }
    var allowlist by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deviceOwner by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(EmergencyWindow.NONE_DEADLINE) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    fun refresh(reloadApps: Boolean = true) {
        scope.launch(Dispatchers.Default) {
            applier.reconcile()
            deviceOwner = applier.isDeviceOwner()
            deadline = applier.emergencyDeadline()
            allowlist = applier.getAllowlist()
            if (reloadApps) {
                apps = applier.getLauncherApps()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Catch-up on every resume: if the (possibly inexact) alarm fired late or was missed,
    // the persisted-deadline check inside reconcile re-locks immediately.
    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val window = EmergencyWindow.evaluate(deadline, now)
    LaunchedEffect(window) {
        if (window is EmergencyWindow.Expired) refresh(reloadApps = false)
    }

    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = { Text("Экстренная разблокировка") },
            text = {
                Text(
                    "Разблокировать все приложения на 30 минут? " +
                        "После этого блокировка будет применена автоматически."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmergencyDialog = false
                    scope.launch(Dispatchers.Default) {
                        applier.startEmergencyUnlock()
                        refresh(reloadApps = false)
                    }
                }) { Text("Разблокировать") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) { Text("Отмена") }
            }
        )
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            apps = apps,
            allowlist = allowlist,
            selfPackage = selfPackage,
            deviceOwner = deviceOwner,
            window = window,
            onLaunch = { packageName -> applier.launchPackage(packageName) },
            onOpenAllowlist = { screen = Screen.Allowlist },
            onEmergencyUnlock = { showEmergencyDialog = true }
        )
        Screen.Allowlist -> AllowlistScreen(
            apps = apps.filter { it.packageName != selfPackage },
            allowlist = allowlist,
            deviceOwner = deviceOwner,
            onBack = { screen = Screen.Home },
            onToggle = { packageName, allowed ->
                scope.launch(Dispatchers.Default) {
                    applier.setAllowed(packageName, allowed)
                    allowlist = applier.getAllowlist()
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
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
        Text("ZeroPhone", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (deviceOwner) "Device Owner: блокировка активна"
            else "Не Device Owner: блокировка отключена",
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
                    text = "ZeroPhone не назначен Device Owner — приложения не блокируются. " +
                        "Инструкция по provisioning — в README.md.",
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
                    text = "Экстренная разблокировка: повторная блокировка через " +
                        formatRemaining(window.remainingMillis),
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
                "Нет разрешённых приложений. Откройте управление allowlist и отметьте нужные.",
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
            ) { Text("Allowlist") }
            Button(
                onClick = onEmergencyUnlock,
                enabled = deviceOwner,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (window) {
                        is EmergencyWindow.Active ->
                            "Разблокировано: ${formatRemaining(window.remainingMillis)}"
                        else -> "Экстренная разблокировка"
                    },
                    maxLines = 2
                )
            }
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
