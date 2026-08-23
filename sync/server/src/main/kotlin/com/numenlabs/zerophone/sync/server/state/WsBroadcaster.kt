package com.numenlabs.zerophone.sync.server.state

import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registry of the live WebSocket sessions of the sync server. Any part of
 * the server (state writes, send-link delivery, …) pushes the same thin
 * [SyncPushMessage] envelopes to every connected device.
 *
 * [broadcast] never suspends: it drops frames into each session's
 * outgoing buffer with `trySend`, which makes it safe to call from inside
 * the state store's critical section and keeps pushes strictly ordered
 * by revision. Slow consumers drop frames and catch up with one REST
 * pull instead of blocking the writer.
 */
class WsBroadcaster {

    private val mutex = Mutex()
    private val sessions = mutableSetOf<SendChannel<Frame>>()

    suspend fun register(session: SendChannel<Frame>): Boolean = mutex.withLock {
        !session.isClosedForSend && sessions.add(session)
    }

    suspend fun unregister(session: SendChannel<Frame>) {
        mutex.withLock { sessions.remove(session) }
    }

    /**
     * Whether at least one session is currently registered — the
     * store-and-forward queues consult this before deciding between a
     * live push and keeping a link queued for the next connect.
     */
    suspend fun hasSessions(): Boolean = mutex.withLock { sessions.isNotEmpty() }

    fun broadcast(message: SyncPushMessage) {
        if (sessions.isEmpty()) return
        val text = SyncServerJson.encodeToString(SyncPushMessage.serializer(), message)
        // A Frame is stateful while it is written to the wire, so every
        // session must get its own instance — sharing one frame across
        // sockets would empty its payload under concurrent writers.
        sessions.forEach { it.trySend(Frame.Text(text)) }
        sessions.removeAll { it.isClosedForSend }
    }
}
