package com.numenlabs.zerophone.sync.server

import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.installDeviceTokenAuth
import com.numenlabs.zerophone.sync.server.pairing.PairingStore
import com.numenlabs.zerophone.sync.server.pairing.installPairing
import com.numenlabs.zerophone.sync.server.state.StateStore
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import com.numenlabs.zerophone.sync.server.state.installStateSync
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import java.nio.file.Path

/**
 * Assembles the sync server from its feature installers over one data
 * directory of file-backed JSON stores: device-token auth, conditional
 * state sync with WebSocket pushes and shortcode pairing (which hands
 * out the device tokens). See [MainKt] for the runnable entry point and
 * the fat-jar packaging.
 */
fun Application.syncServerModule(dataDir: Path) {
    install(WebSockets)
    val tokenStore = DeviceTokenStore(dataDir.resolve("device-tokens.json"))
    val stateStore = StateStore(dataDir.resolve("state.json"))
    val pairingStore = PairingStore(dataDir.resolve(PairingStore.FILE_NAME))
    val broadcaster = WsBroadcaster()
    installDeviceTokenAuth(tokenStore)
    installStateSync(stateStore, broadcaster)
    installPairing(pairingStore, tokenStore)
}
