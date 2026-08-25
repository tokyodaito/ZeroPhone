package com.numenlabs.zerophone.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.desktop.theme.ZeroDesktopTheme
import com.numenlabs.zerophone.desktop.state.AppStateHolder
import com.numenlabs.zerophone.desktop.state.ConnectionState
import com.numenlabs.zerophone.desktop.state.DashboardModel
import com.numenlabs.zerophone.desktop.state.InboxEntry
import com.numenlabs.zerophone.desktop.state.SyncEvent
import com.numenlabs.zerophone.desktop.state.toDashboardModel
import com.numenlabs.zerophone.desktop.state.toInboxEntries
import com.numenlabs.zerophone.desktop.sync.DeviceTokenStore
import com.numenlabs.zerophone.desktop.sync.InboxStore
import com.numenlabs.zerophone.desktop.sync.KtorSyncTransport
import com.numenlabs.zerophone.desktop.sync.PairingOutcome
import com.numenlabs.zerophone.desktop.sync.SyncEngine
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * Compose Desktop entry point: a single read-only window over the phone's
 * synced state — dashboard of policy/availability, the "send to PC" inbox
 * and a status bar. The whole UI renders
 * [AppStateHolder.state]; pairing happens inline when the engine reports
 * [ConnectionState.NEEDS_PAIRING].
 *
 * Usage: `:desktop:run --args="--server=https://sync.example.com"`
 * (defaults to `http://127.0.0.1:8080`).
 */
fun main(args: Array<String>) {
    val options = args.associate {
        val key = it.substringBefore('=')
        val value = it.substringAfter('=', "")
        key to value
    }
    val serverUrl = options["--server"] ?: DEFAULT_SERVER_URL

    val tokenStore = DeviceTokenStore()
    val inboxStore = InboxStore()
    val holder = AppStateHolder(inboxStore = inboxStore)
    val engine = SyncEngine(
        transport = KtorSyncTransport(
            serverUrl = serverUrl,
            tokenProvider = { tokenStore.load()?.token },
        ),
        tokenStore = tokenStore,
        onEvent = holder::dispatch,
    )

    application {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { engine.start(this) }

        Window(onCloseRequest = ::exitApplication, title = "ZeroPhone Desktop") {
            ZeroDesktopTheme {
                AppWindow(
                    state = holder.state.value,
                    serverUrl = serverUrl,
                    onOpenLink = { url -> LinkOpener.open(url) },
                    onMarkOpened = { id ->
                        holder.markOpened(id)
                    },
                    onPair = { code, onResult ->
                        scope.launch {
                            onResult(
                                when (engine.pair(code, deviceName())) {
                                    PairingOutcome.PAIRED -> true
                                    PairingOutcome.REJECTED -> false
                                }
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun deviceName(): String =
    runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("desktop")

private const val DEFAULT_SERVER_URL = "http://127.0.0.1:8080"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppWindow(
    state: com.numenlabs.zerophone.desktop.state.DesktopAppState,
    serverUrl: String,
    onOpenLink: (String) -> Unit,
    onMarkOpened: (String) -> Unit,
    onPair: (code: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ZeroPhone") })
        },
        bottomBar = { StatusBar(state, serverUrl) },
    ) { padding ->
        when (state.connection) {
            ConnectionState.NEEDS_PAIRING -> PairingPanel(onPair)

            else ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DashboardSection(state.toDashboardModel())
                    InboxSection(
                        entries = state.toInboxEntries(),
                        onOpen = { entry ->
                            onOpenLink(entry.url)
                            onMarkOpened(entry.id)
                        },
                    )
                }
        }
    }
}

@Composable
private fun DashboardSection(model: DashboardModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Policy dashboard", style = MaterialTheme.typography.titleMedium)
            if (model.rows.isEmpty()) {
                Text(
                    if (model.activeMode == null) "Waiting for the first sync…" else "No capabilities reported.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("mode: ${model.activeMode ?: "-"}  ·  capabilities: ${model.rows.size}")
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(model.rows) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                row.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                row.state.name,
                                color = availabilityColor(row.state),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxSection(entries: List<InboxEntry>, onOpen: (InboxEntry) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Inbox — sent from the phone", style = MaterialTheme.typography.titleMedium)
            if (entries.isEmpty()) {
                Text("Nothing yet. Use “send to PC” on the phone.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (entry.isOpened) "✓" else "•",
                                modifier = Modifier.width(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title ?: entry.url, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "from ${entry.sourceDeviceName ?: "phone"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { onOpen(entry) }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingPanel(onPair: (code: String, onResult: (Boolean) -> Unit) -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp).width(360.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pair with your phone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enter the shortcode shown by the ZeroPhone sync server.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.trim().uppercase()
                        error = null
                    },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = code.length == 6 && !busy,
                    onClick = {
                        busy = true
                        error = null
                        onPair(code) { paired ->
                            busy = false
                            if (!paired) error = "Code rejected — check it and try again."
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Pair")
                }
            }
        }
    }
}

@Composable
private fun StatusBar(state: com.numenlabs.zerophone.desktop.state.DesktopAppState, serverUrl: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            when (state.connection) {
                ConnectionState.CONNECTED -> "connected"
                ConnectionState.CONNECTING -> "connecting…"
                ConnectionState.DISCONNECTED -> "disconnected"
                ConnectionState.NEEDS_PAIRING -> "needs pairing"
            } + if (state.snapshot != null) {
                "  ·  unopened ${state.unopenedCount}"
            } else {
                ""
            },
            style = MaterialTheme.typography.labelSmall,
        )
        Text(serverUrl, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun availabilityColor(state: Availability): Color = when (state) {
    Availability.AVAILABLE -> MaterialTheme.colorScheme.primary
    Availability.TEMPORARILY_AVAILABLE -> MaterialTheme.colorScheme.secondary
    Availability.CONTEXTUAL -> MaterialTheme.colorScheme.tertiary
    Availability.RESTRICTED -> MaterialTheme.colorScheme.error
    Availability.BLOCKED -> MaterialTheme.colorScheme.error
}
