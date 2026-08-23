package com.numenlabs.zerophone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.CapabilityRef
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.data.calendar.CalendarSource
import com.numenlabs.zerophone.core.data.notifications.ImportantNotificationFilter
import com.numenlabs.zerophone.core.data.notifications.ImportantNotificationsStore
import com.numenlabs.zerophone.core.data.notifications.NotificationSettingsRepository
import com.numenlabs.zerophone.core.data.tasks.TaskRepository
import com.numenlabs.zerophone.core.model.CalendarEvent
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.ui.theme.ZeroPhoneTheme
import com.numenlabs.zerophone.feature.allowlist.AllowlistScreen
import com.numenlabs.zerophone.feature.home.EmergencyUnlockDialog
import com.numenlabs.zerophone.feature.home.HomeScreen
import com.numenlabs.zerophone.feature.home.QuickAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var applier: PolicyApplier

    @Inject
    lateinit var calendarSource: CalendarSource

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var notificationsStore: ImportantNotificationsStore

    @Inject
    lateinit var notificationSettings: NotificationSettingsRepository

    @Inject
    lateinit var syncEngine: com.numenlabs.zerophone.core.data.sync.PhoneSyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeroPhoneTheme {
                ZeroPhoneApp(
                    applier = applier,
                    calendarSource = calendarSource,
                    taskRepository = taskRepository,
                    notificationsStore = notificationsStore,
                    notificationSettings = notificationSettings,
                    syncEngine = syncEngine,
                    onQuickActionIntent = ::launchQuickAction
                )
            }
        }
    }

    /**
     * Intent templates behind the quick actions. Every action resolves to a
     * plain system intent (dialer / messenger / maps / wallet / camera) —
     * the engine has already decided the action is allowed. Send-to-PC is
     * handled in the Compose layer (URL dialog), never here.
     */
    private fun launchQuickAction(action: QuickAction) {
        val intent = when (action) {
            QuickAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            QuickAction.MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            QuickAction.NAVIGATE -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            // No standard payment intent exists: open the default wallet app,
            // falling back to the NFC/contactless settings page.
            QuickAction.PAY ->
                packageManager.getLaunchIntentForPackage(WALLET_PACKAGE)
                    ?: Intent(Settings.ACTION_NFC_SETTINGS)
            QuickAction.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            QuickAction.SEND_TO_PC -> return
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = try {
            packageManager.resolveActivity(intent, 0) != null
        } catch (_: Exception) {
            false
        }
        if (!resolved && action == QuickAction.CAMERA) {
            // Device without a dedicated still-camera activity — plain capture.
            try {
                startActivity(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Exception) {
                return
            }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // No handler for the action — silently ignore.
        }
    }

    private companion object {
        const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrent"
    }
}

/** Type-safe navigation routes (single-activity, nav host owned by [ZeroPhoneApp]). */
@Serializable
internal object HomeRoute

@Serializable
internal object AllowlistRoute

@Composable
private fun ZeroPhoneApp(
    applier: PolicyApplier,
    calendarSource: CalendarSource,
    taskRepository: TaskRepository,
    notificationsStore: ImportantNotificationsStore,
    notificationSettings: NotificationSettingsRepository,
    syncEngine: com.numenlabs.zerophone.core.data.sync.PhoneSyncEngine,
    onQuickActionIntent: (QuickAction) -> Unit
) {
    val context = LocalContext.current
    val selfPackage = remember { applier.selfPackageName }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<PolicyApplier.LauncherApp>>(emptyList()) }
    var allowlist by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deviceOwner by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(EmergencyWindow.NONE_DEADLINE) }
    var emergencyDuration by remember { mutableStateOf(EmergencyWindow.DEFAULT_DURATION_MS) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    // Contextual home-screen data.
    var nextEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var priorityPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeMode by remember { mutableStateOf<String>(ModeIds.WORK) }
    var actionStates by remember {
        mutableStateOf<Map<QuickAction, AvailabilityState>>(emptyMap())
    }
    val activeNotifications by notificationsStore.active.collectAsState()
    val importantUnreadCount = remember(activeNotifications, priorityPackages) {
        ImportantNotificationFilter(priorityPackages).countImportant(activeNotifications)
    }

    // Phone-side sync: the engine keeps the policy store synchronized and
    // re-runs the applier whenever a remote change landed.
    LaunchedEffect(Unit) {
        syncEngine.start(this)
    }

    fun refresh(reloadApps: Boolean = true) {
        scope.launch(Dispatchers.Default) {
            applier.reconcile()
            deviceOwner = applier.isDeviceOwner()
            deadline = applier.emergencyDeadline()
            emergencyDuration = applier.emergencyDurationMillis()
            allowlist = applier.getAllowlist()
            activeMode = applier.getActiveMode()
            actionStates = QuickAction.entries.associateWith { action ->
                if (action == QuickAction.SEND_TO_PC) {
                    AvailabilityState.Available
                } else {
                    applier.availabilityOf(CapabilityRef.Logical(action.capabilityId))
                }
            }
            nextEvent = calendarSource.nextEvent(nowMillis = System.currentTimeMillis())
            tasks = taskRepository.getTasks()
            priorityPackages = notificationSettings.getPriorityPackages()
            if (reloadApps) {
                apps = applier.getLauncherApps()
            }
        }
    }

    val hasCalendarPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refresh(reloadApps = false) }

    LaunchedEffect(Unit) {
        applier.ensureDefaultRules()
        if (!hasCalendarPermission) {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
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

    // Contextual data (next event / tasks) refreshes every minute, not every second.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nextEvent = calendarSource.nextEvent(nowMillis = System.currentTimeMillis())
            tasks = taskRepository.getTasks()
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
                nowMillis = now,
                nextEvent = nextEvent,
                tasks = tasks,
                importantUnreadCount = importantUnreadCount,
                activeMode = activeMode,
                actionStates = actionStates,
                onLaunch = { packageName -> applier.launchPackage(packageName) },
                onOpenAllowlist = { navController.navigate(AllowlistRoute) },
                onEmergencyUnlock = { showEmergencyDialog = true },
                onSetMode = { mode ->
                    scope.launch(Dispatchers.Default) {
                        applier.setActiveMode(mode)
                        refresh(reloadApps = false)
                    }
                },
                onQuickAction = onQuickActionIntent,
                onSendToPc = { url ->
                    scope.launch(Dispatchers.Default) {
                        runCatching { syncEngine.sendLink(url) }
                    }
                },
                onAddTask = { title ->
                    scope.launch(Dispatchers.Default) {
                        taskRepository.addTask(title)
                        tasks = taskRepository.getTasks()
                    }
                },
                onToggleTask = { id, done ->
                    scope.launch(Dispatchers.Default) {
                        taskRepository.setTaskDone(id, done)
                        tasks = taskRepository.getTasks()
                    }
                }
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
