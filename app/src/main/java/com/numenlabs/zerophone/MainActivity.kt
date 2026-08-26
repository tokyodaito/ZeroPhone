package com.numenlabs.zerophone

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.numenlabs.zerophone.core.context.AvailabilityState
import com.numenlabs.zerophone.core.context.CapabilityPackages
import com.numenlabs.zerophone.core.context.ModeIds
import com.numenlabs.zerophone.core.context.Rule
import com.numenlabs.zerophone.core.context.RuleDecision
import com.numenlabs.zerophone.core.context.RuleTarget
import com.numenlabs.zerophone.core.data.calendar.CalendarSource
import com.numenlabs.zerophone.core.data.notifications.ImportantNotificationFilter
import com.numenlabs.zerophone.core.data.notifications.ImportantNotificationsStore
import com.numenlabs.zerophone.core.data.notifications.NotificationSettingsRepository
import com.numenlabs.zerophone.core.data.tasks.TaskRepository
import com.numenlabs.zerophone.core.data.usage.AndroidUsageStatsSource
import com.numenlabs.zerophone.core.model.CalendarEvent
import com.numenlabs.zerophone.core.model.EmergencyWindow
import com.numenlabs.zerophone.core.model.Task
import com.numenlabs.zerophone.core.policy.PolicyApplier
import com.numenlabs.zerophone.core.ui.theme.ZeroPhoneTheme
import com.numenlabs.zerophone.feature.allowlist.AllowlistScreen
import com.numenlabs.zerophone.feature.home.EmergencyUnlockDialog
import com.numenlabs.zerophone.feature.home.HomeScreen
import com.numenlabs.zerophone.feature.home.QuickAction
import com.numenlabs.zerophone.feature.home.R as HomeR
import com.numenlabs.zerophone.feature.home.quickActionLabel
import com.numenlabs.zerophone.feature.settings.AppRow
import com.numenlabs.zerophone.feature.settings.SettingsScreen
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

    @Inject
    lateinit var usageStatsSource: AndroidUsageStatsSource

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
                    usageStatsSource = usageStatsSource,
                    onQuickActionIntent = ::launchQuickAction
                )
            }
        }
    }

    /**
     * Intent templates behind the quick actions. Every action resolves to a
     * plain system intent (dialer / messenger / maps / wallet / camera) —
     * the engine has already decided the action is allowed.
     *
     * @return false when the device has no handler for the action, so the UI
     * can explain instead of failing silently.
     */
    private fun launchQuickAction(action: QuickAction): Boolean {
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
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = try {
            packageManager.resolveActivity(intent, 0) != null
        } catch (_: Exception) {
            false
        }
        if (!resolved && action == QuickAction.CAMERA) {
            // Device without a dedicated still-camera activity — plain capture.
            return try {
                startActivity(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) {
                false
            }
        }
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            // No handler for the action — report back to the UI.
            false
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

@Serializable
internal object SettingsRoute

/** True when the exact-alarm permission is missing, i.e. re-lock alarms run inexact. */
private fun exactAlarmsDisabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
    return !alarmManager.canScheduleExactAlarms()
}

/** Duration of the per-capability grant given by a long-press on a quick action. */
private const val GRANT_DURATION_MILLIS = 15L * 60_000L

/** Start of the current device-local day (midnight) for absolute usage measures. */
private fun localDayStartMillis(nowMillis: Long): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

/**
 * Capability ids → packages whose foreground time feeds the daily budgets:
 * the five quick-action capabilities plus every package-targeted rule that
 * carries a budget.
 */
private fun trackedCapabilityPackages(rules: List<Rule>): Map<String, Set<String>> {
    val tracked = QuickAction.entries.associate { action ->
        action.capabilityId to CapabilityPackages.packagesOf(action.capabilityId)
    }.toMutableMap()
    rules.forEach { rule ->
        val target = rule.target as? RuleTarget.Package ?: return@forEach
        val budget = (rule.decision as? RuleDecision.Restrict)?.dailyBudgetMillis ?: return@forEach
        if (budget > 0L) {
            tracked[target.packageName] = setOf(target.packageName)
        }
    }
    return tracked
}

@Composable
private fun ZeroPhoneApp(
    applier: PolicyApplier,
    calendarSource: CalendarSource,
    taskRepository: TaskRepository,
    notificationsStore: ImportantNotificationsStore,
    notificationSettings: NotificationSettingsRepository,
    syncEngine: com.numenlabs.zerophone.core.data.sync.PhoneSyncEngine,
    usageStatsSource: AndroidUsageStatsSource,
    onQuickActionIntent: (QuickAction) -> Boolean
) {
    val context = LocalContext.current
    val selfPackage = remember { applier.selfPackageName }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var apps by remember { mutableStateOf<List<PolicyApplier.LauncherApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var allowlist by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deviceOwner by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(EmergencyWindow.NONE_DEADLINE) }
    var emergencyDuration by remember { mutableStateOf(EmergencyWindow.DEFAULT_DURATION_MS) }
    var showEmergencyDialog by rememberSaveable { mutableStateOf(false) }

    // Contextual home-screen data.
    var nextEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var priorityPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeMode by remember { mutableStateOf<String>(ModeIds.WORK) }
    var actionStates by remember {
        mutableStateOf<Map<QuickAction, AvailabilityState>>(emptyMap())
    }
    var notificationAccessEnabled by remember { mutableStateOf(true) }
    var exactAlarmsMissing by remember { mutableStateOf(false) }
    var usageStatsMissing by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf<List<Rule>>(emptyList()) }
    var budgetRemaining by remember {
        mutableStateOf<Map<QuickAction, Long?>>(emptyMap())
    }
    // Snackbar strings resolved in composition (not via context.getString)
    // so configuration changes invalidate them properly.
    val noHandlerSnackbarFormat = stringResource(HomeR.string.home_no_handler_snackbar)
    val grantedSnackbarFormat = stringResource(HomeR.string.home_granted_snackbar)
    val settingsUnavailableMessage = stringResource(HomeR.string.home_settings_unavailable)
    val actionLabels = QuickAction.entries.associate { action ->
        action to stringResource(quickActionLabel(action))
    }

    // Banner deep links: some OEM builds lack the target settings screen —
    // a launcher must never crash on a missing activity.
    fun openSettingsSafely(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar(settingsUnavailableMessage) }
        }
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
            rules = applier.getRules()
            // Accrue time budgets from real foreground usage. The measure is
            // absolute (whole local day, every pass), so process restarts,
            // racing refreshes and midnight crossings never double-count.
            val usageGranted = usageStatsSource.isGranted()
            if (deviceOwner && usageGranted) {
                val now = System.currentTimeMillis()
                val dayStart = localDayStartMillis(now)
                for ((capabilityId, packages) in trackedCapabilityPackages(rules)) {
                    applier.setCapabilityUsage(
                        capabilityId,
                        usageStatsSource.foregroundMillisBetween(packages, dayStart, now)
                    )
                }
            }
            deadline = applier.emergencyDeadline()
            emergencyDuration = applier.emergencyDurationMillis()
            allowlist = applier.getAllowlist()
            activeMode = applier.getActiveMode()
            val snapshot = applier.logicalSnapshot(QuickAction.entries.map { it.capabilityId })
            actionStates = QuickAction.entries.associateWith { action ->
                snapshot.states[action.capabilityId] ?: AvailabilityState.Blocked
            }
            budgetRemaining = QuickAction.entries.associateWith { action ->
                snapshot.budgetRemainingMillis[action.capabilityId]
            }
            nextEvent = calendarSource.nextEvent(nowMillis = System.currentTimeMillis())
            tasks = taskRepository.getTasks()
            priorityPackages = notificationSettings.getPriorityPackages()
            notificationAccessEnabled =
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            exactAlarmsMissing = exactAlarmsDisabled(context)
            // The usage banner only matters once a rule actually relies on
            // time budgets.
            usageStatsMissing = usageGranted.not() &&
                rules.any { (it.decision as? RuleDecision.Restrict)?.dailyBudgetMillis != null }
            if (reloadApps) {
                apps = applier.getLauncherApps()
                appsLoaded = true
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

    // Re-lock catch-up without a per-second recomposition: wake exactly once
    // at the persisted deadline. Process death / Doze are covered by the
    // AlarmManager alarm and the ON_RESUME reconcile above.
    LaunchedEffect(deadline) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining > 0) {
            delay(remaining)
            refresh(reloadApps = false)
        }
    }

    // Contextual data AND the policy itself re-evaluate every minute while
    // the launcher is composed: time windows flip without a resume.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refresh(reloadApps = false)
        }
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
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 4 }
            }
        ) {
        composable<HomeRoute> {
            HomeScreen(
                apps = apps,
                allowlist = allowlist,
                selfPackage = selfPackage,
                deviceOwner = deviceOwner,
                appsLoading = !appsLoaded,
                emergencyDeadline = deadline,
                nextEvent = nextEvent,
                tasks = tasks,
                importantUnreadCount = importantUnreadCount,
                activeMode = activeMode,
                actionStates = actionStates,
                budgetRemaining = budgetRemaining,
                notificationAccessEnabled = notificationAccessEnabled,
                exactAlarmsDisabled = deviceOwner && exactAlarmsMissing,
                usageTrackingMissing = deviceOwner && usageStatsMissing,
                onLaunch = { packageName -> applier.launchPackage(packageName) },
                onOpenAllowlist = { navController.navigate(AllowlistRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onEmergencyUnlock = { showEmergencyDialog = true },
                onRelockNow = {
                    scope.launch(Dispatchers.Default) {
                        applier.cancelEmergencyUnlock()
                        refresh(reloadApps = false)
                    }
                },
                onSetMode = { mode ->
                    scope.launch(Dispatchers.Default) {
                        applier.setActiveMode(mode)
                        refresh(reloadApps = false)
                    }
                },
                onQuickAction = { action ->
                    if (!onQuickActionIntent(action)) {
                        val message = String.format(noHandlerSnackbarFormat, actionLabels[action].orEmpty())
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                },
                onGrantCapability = { action ->
                    scope.launch(Dispatchers.Default) {
                        applier.grantCapability(action.capabilityId, GRANT_DURATION_MILLIS)
                        refresh(reloadApps = false)
                        val message = String.format(grantedSnackbarFormat, actionLabels[action].orEmpty())
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                },
                onOpenNotificationAccessSettings = {
                    openSettingsSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onRequestExactAlarms = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        openSettingsSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                },
                onOpenUsageAccessSettings = {
                    openSettingsSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
                },
                onUpdateTask = { id, title, dueAtMillis ->
                    scope.launch(Dispatchers.Default) {
                        taskRepository.updateTask(id, title, dueAtMillis)
                        tasks = taskRepository.getTasks()
                    }
                },
                onDeleteTask = { id ->
                    scope.launch(Dispatchers.Default) {
                        taskRepository.deleteTask(id)
                        tasks = taskRepository.getTasks()
                    }
                },
                onClearCompleted = {
                    scope.launch(Dispatchers.Default) {
                        taskRepository.clearCompleted()
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
        composable<SettingsRoute> {
            SettingsScreen(
                rules = rules,
                apps = apps
                    .filter { it.packageName != selfPackage }
                    .map { AppRow(packageName = it.packageName, label = it.label) },
                priorityPackages = priorityPackages,
                emergencyDurationMillis = emergencyDuration,
                onBack = { navController.popBackStack() },
                onUpdateRules = { updated ->
                    scope.launch(Dispatchers.Default) {
                        applier.updateRules(updated)
                        refresh(reloadApps = false)
                    }
                },
                onTogglePriorityPackage = { packageName, priority ->
                    scope.launch(Dispatchers.Default) {
                        val updated = notificationSettings.getPriorityPackages().toMutableSet()
                        if (priority) updated.add(packageName) else updated.remove(packageName)
                        notificationSettings.setPriorityPackages(updated)
                        priorityPackages = updated
                    }
                },
                onSetEmergencyDuration = { durationMillis ->
                    scope.launch(Dispatchers.Default) {
                        applier.setEmergencyDuration(durationMillis)
                        emergencyDuration = durationMillis
                    }
                }
            )
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
