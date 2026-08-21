package com.numenlabs.zerophone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.ui.theme.ZeroPhoneTheme
import com.numenlabs.zerophone.feature.allowlist.AllowlistScreen
import com.numenlabs.zerophone.feature.home.HomeScreen
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            title = { Text(androidx.compose.ui.res.stringResource(R.string.emergency_dialog_title)) },
            text = {
                Text(androidx.compose.ui.res.stringResource(R.string.emergency_dialog_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmergencyDialog = false
                    scope.launch(Dispatchers.Default) {
                        applier.startEmergencyUnlock()
                        refresh(reloadApps = false)
                    }
                }) { Text(androidx.compose.ui.res.stringResource(R.string.emergency_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.emergency_dialog_dismiss))
                }
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
