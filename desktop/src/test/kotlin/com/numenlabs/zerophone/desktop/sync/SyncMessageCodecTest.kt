package com.numenlabs.zerophone.desktop.sync

import com.numenlabs.zerophone.core.model.CapabilityAvailability
import com.numenlabs.zerophone.core.model.Availability
import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.LinkPayload
import com.numenlabs.zerophone.core.model.StateEnvelope
import com.numenlabs.zerophone.core.model.StateUpdateRequest
import com.numenlabs.zerophone.core.model.SyncPushMessage
import com.numenlabs.zerophone.core.model.SyncState
import com.numenlabs.zerophone.core.model.PolicySnapshot
import com.numenlabs.zerophone.desktop.state.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-level decoding against the actual `:core:model` contract names:
 * `state.snapshot` / `state.updated` / `link.received` push frames, the
 * [StateEnvelope] of GET/409 bodies, the [StateUpdateRequest] of the
 * conditional PUT, plus the local-disk formats (credentials, inbox).
 */
class SyncMessageCodecTest {

    @Test
    fun `decodes link received frame`() {
        val push = SyncMessageCodec.decodePush(
            """{"type":"link.received","payload":{"id":"l1","url":"https://example.com/a","title":"A","sourceDeviceName":"Pixel","sentAtMillis":10}}"""
        )
        assertTrue(push is SyncPushMessage.LinkReceived)
        assertEquals(
            LinkPayload(
                id = "l1",
                url = "https://example.com/a",
                title = "A",
                sourceDeviceName = "Pixel",
                sentAtMillis = 10,
            ),
            (push as SyncPushMessage.LinkReceived).payload,
        )
    }

    @Test
    fun `decodes state snapshot frame`() {
        assertEquals(
            SyncPushMessage.StateSnapshot(revision = 3),
            SyncMessageCodec.decodePush("""{"type":"state.snapshot","revision":3}"""),
        )
    }

    @Test
    fun `decodes state updated frame`() {
        assertEquals(
            SyncPushMessage.StateUpdated(revision = 4, actorDeviceId = "phone-1", hint = "policy"),
            SyncMessageCodec.decodePush(
                """{"type":"state.updated","revision":4,"actorDeviceId":"phone-1","hint":"policy"}"""
            ),
        )
    }

    @Test
    fun `unknown push type and malformed json return null`() {
        assertNull(SyncMessageCodec.decodePush("""{"type":"future-event","x":1}"""))
        assertNull(SyncMessageCodec.decodePush("not json at all"))
        assertNull(SyncMessageCodec.decodePush(""))
    }

    @Test
    fun `push encode decode roundtrip`() {
        for (original in listOf(
            SyncPushMessage.StateSnapshot(revision = 1),
            SyncPushMessage.StateUpdated(revision = 2, actorDeviceId = "d"),
            SyncPushMessage.LinkReceived(LinkPayload(id = "l2", url = "https://x.dev")),
        )) {
            assertEquals(original, SyncMessageCodec.decodePush(SyncMessageCodec.encodePush(original)))
        }
    }

    @Test
    fun `decodes envelope ignoring unknown fields`() {
        val envelope = SyncMessageCodec.decodeEnvelope(
            """
            {"revision":2,"futureField":[1,2],
             "state":{"policy":{"deviceId":"phone","activeMode":"work","future":true,
               "capabilities":[{"id":"yt","state":"BLOCKED"}]}}}
            """.trimIndent()
        )
        requireNotNull(envelope)
        assertEquals(2L, envelope.revision)
        assertEquals("phone", envelope.state.policy.deviceId)
        assertEquals("work", envelope.state.policy.activeMode)
        assertEquals(Availability.BLOCKED, envelope.state.policy.capabilities.single().state)
    }

    @Test
    fun `envelope encode decode roundtrip`() {
        val original = StateEnvelope(
            state = SyncState(
                policy = PolicySnapshot(
                    generatedAtMillis = 5,
                    deviceId = "d",
                    activeMode = "rest",
                    emergencyRemainingMillis = 120000,
                    capabilities = listOf(
                        CapabilityAvailability(id = "dialer", state = Availability.AVAILABLE),
                    ),
                ),
            ),
            revision = 12,
        )
        assertEquals(original, SyncMessageCodec.decodeEnvelope(SyncMessageCodec.encodeEnvelope(original)))
        assertNull(SyncMessageCodec.decodeEnvelope("{broken"))
    }

    @Test
    fun `update encode carries base revision state and hint`() {
        val json = SyncMessageCodec.encodeUpdate(
            StateUpdateRequest(
                baseRevision = 7,
                state = SyncState(),
                hint = "policy",
            )
        )
        assertTrue(json.contains("\"baseRevision\":7"))
        assertTrue(json.contains("\"hint\":\"policy\""))
        assertTrue(json.contains("\"state\""))
    }

    @Test
    fun `credentials roundtrip`() {
        val original = DeviceCredentials(deviceId = "desktop-1", token = "t", pairedAtMillis = 1)
        assertEquals(
            original,
            SyncMessageCodec.decodeCredentials(SyncMessageCodec.encodeCredentials(original)),
        )
        assertNull(SyncMessageCodec.decodeCredentials("nope"))
    }

    @Test
    fun `inbox roundtrip preserves opened marks`() {
        val items = listOf(
            InboxItem(LinkPayload(id = "a", url = "https://a"), receivedAtMillis = 1, isOpened = true),
            InboxItem(LinkPayload(id = "b", url = "https://b"), receivedAtMillis = 2),
        )
        assertEquals(items, SyncMessageCodec.decodeInbox(SyncMessageCodec.encodeInbox(items)))
        assertNull(SyncMessageCodec.decodeInbox("[{}]"))
    }
}
