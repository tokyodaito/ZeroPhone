package com.numenlabs.zerophone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.ui.theme.ZeroPhoneTheme
import com.numenlabs.zerophone.feature.allowlist.AllowlistScreen
import com.numenlabs.zerophone.feature.home.EmergencyUnlockDialog
import com.numenlabs.zerophone.feature.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.collections.filter

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var applier: PolicyApplier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeroPhoneTheme {
                ZeroPhoneApp(applier)
            }
        }
    }
}

/** Type-safe navigation routes (single-activity, nav host owned by [ZeroPhoneApp]). */
@Serializable
internal object HomeRoute

@Serializable
internal object AllowlistRoute

@Composable
private fun ZeroPhoneApp(applier: PolicyApplier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selfPackage = remember { applier.selfPackageName }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<PolicyApplier.LauncherApp>>(emptyList()) }
    var allowlist by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deviceOwner by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(EmergencyWindow.NONE_DEADLINE) }
    var emergencyDuration by remember { mutableStateOf(EmergencyWindow.DEFAULT_DURATION_MS) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    fun refresh(reloadApps: Boolean = true) {
        scope.launch(Dispatchers.Default) {
            applier.reconcile()
            deviceOwner = applier.isDeviceOwner()
            deadline = applier.emergencyDeadline()
            emergencyDuration = applier.emergencyDurationMillis()
            allowlist = applier.getAllowlist()
            if (reloadApps) {
                apps = applier.getLauncherApps()
            }
        }
    }

    LaunchedEffect(Unit) {
        applier.ensureDefaultRules()
        refresh()
    }

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
        EmergencyUnlockDialog(
            currentDurationMillis = emergencyDuration,
            onConfirm = { durationMillis ->
                showEmergencyDialog = false
                scope.launch(Dispatchers.Default) {
                    applier.setEmergencyDuration(durationMillis)
                    emergencyDuration = durationMillis
                    applier.startEmergencyUnlock(durationMillis)
                    refresh(reloadApps = false)
                }
            },
            onDismiss = { showEmergencyDialog = false }
        )
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                apps = apps,
                allowlist = allowlist,
                selfPackage = selfPackage,
                deviceOwner = deviceOwner,
                window = window,
                onLaunch = { packageName -> applier.launchPackage(packageName) },
                onOpenAllowlist = { navController.navigate(AllowlistRoute) },
                onEmergencyUnlock = { showEmergencyDialog = true }
            )
        }
        composable<AllowlistRoute> {
            AllowlistScreen(
                apps = apps.filter { it.packageName != selfPackage },
                allowlist = allowlist,
                deviceOwner = deviceOwner,
                onBack = { navController.popBackStack() },
                onToggle = { packageName, allowed ->
                    scope.launch(Dispatchers.Default) {
                        applier.setAllowed(packageName, allowed)
                        allowlist = applier.getAllowlist()
                    }
                }
            )
        }
    }
}
