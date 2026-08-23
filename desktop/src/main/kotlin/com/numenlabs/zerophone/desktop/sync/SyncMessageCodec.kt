package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.desktop.state.InboxItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * JSON codec for everything crossing the wire or the local disk.
 * Malformed input decodes to `null` — a bad frame must never crash the
 * client loop. Unknown keys are ignored for forward compatibility.
 */
object SyncMessageCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodePush(text: String): SyncPushMessage? =
        runCatching { json.decodeFromString<SyncPushMessage>(text) }.getOrNull()

    fun encodePush(message: SyncPushMessage): String =
        json.encodeToString(SyncPushMessage.serializer(), message)

    fun decodeEnvelope(text: String): StateEnvelope? =
        runCatching { json.decodeFromString<StateEnvelope>(text) }.getOrNull()

    fun encodeEnvelope(envelope: StateEnvelope): String =
        json.encodeToString(StateEnvelope.serializer(), envelope)

    fun encodeUpdate(update: StateUpdateRequest): String =
        json.encodeToString(StateUpdateRequest.serializer(), update)

    fun decodeCredentials(text: String): DeviceCredentials? =
        runCatching { json.decodeFromString<DeviceCredentials>(text) }.getOrNull()

    fun encodeCredentials(credentials: DeviceCredentials): String =
        json.encodeToString(DeviceCredentials.serializer(), credentials)

    fun decodeInbox(text: String): List<InboxItem>? =
        runCatching { json.decodeFromString(ListSerializer(InboxItem.serializer()), text) }.getOrNull()

    fun encodeInbox(items: List<InboxItem>): String =
        json.encodeToString(ListSerializer(InboxItem.serializer()), items)
}
