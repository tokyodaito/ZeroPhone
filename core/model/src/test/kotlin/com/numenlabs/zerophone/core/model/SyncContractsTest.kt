package com.numenlabs.zerophone.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncContractsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `snapshot decodes with unknown fields and defaults`() {
        val text = """
            {
              "schemaVersion": 1,
              "generatedAtMillis": 1234567,
              "deviceId": "phone-1",
              "unknownFutureField": true,
              "activeMode": "work",
              "emergencyRemainingMillis": 300000,
              "capabilities": [
                {"id": "com.android.youtube", "kind": "PACKAGE", "state": "BLOCKED"},
                {"id": "call", "kind": "LOGICAL", "label": "Позвонить", "state": "AVAILABLE"},
                {"id": "games", "state": "RESTRICTED", "restrictionReason": "TIME_BUDGET"},
                {"id": "news", "state": "TEMPORARILY_AVAILABLE", "remainingMillis": 60000}
              ]
            }
        """.trimIndent()

        val snapshot = json.decodeFromString<PolicySnapshot>(text)

        assertEquals("phone-1", snapshot.deviceId)
        assertEquals("work", snapshot.activeMode)
        assertEquals(300000L, snapshot.emergencyRemainingMillis)
        assertEquals(4, snapshot.capabilities.size)
        assertEquals(Availability.BLOCKED, snapshot.capabilities[0].state)
        assertEquals(CapabilityKind.PACKAGE, snapshot.capabilities[0].kind)
        assertEquals(Availability.RESTRICTED, snapshot.capabilities[2].state)
        assertEquals("TIME_BUDGET", snapshot.capabilities[2].restrictionReason)
        assertEquals(60000L, snapshot.capabilities[3].remainingMillis)
    }

    @Test
    fun `snapshot roundtrip preserves all fields`() {
        val snapshot = PolicySnapshot(
            deviceId = "d1",
            deviceName = "Pixel",
            activeMode = "focus",
            emergencyRemainingMillis = 0,
            capabilities = listOf(
                CapabilityAvailability(
                    id = "camera",
                    kind = CapabilityKind.LOGICAL,
                    state = Availability.CONTEXTUAL,
                    contextCondition = "not at night",
                )
            )
        )
        val decoded = json.decodeFromString<PolicySnapshot>(
            json.encodeToString(PolicySnapshot.serializer(), snapshot)
        )
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `empty snapshot decodes with safe defaults`() {
        val decoded = json.decodeFromString<PolicySnapshot>("{}")
        assertEquals(EmergencyWindow.NONE_DEADLINE, decoded.emergencyRemainingMillis)
        assertNull(decoded.activeMode)
        assertTrue(decoded.capabilities.isEmpty())
        assertEquals(SyncEndpoints.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `state document defaults to an empty policy`() {
        val state = json.decodeFromString<SyncState>("{}")
        assertEquals(SyncEndpoints.CURRENT_SCHEMA_VERSION, state.schemaVersion)
        assertEquals(PolicySnapshot(), state.policy)
    }

    @Test
    fun `serialized state document never carries a revision`() {
        // Invariant: only the server assigns revisions and it stores them
        // next to the document (StateEnvelope), never inside it. If a
        // "revision" key ever leaks into SyncState, clients start trusting
        // writer-supplied revisions and the conditional PUT breaks.
        val encoded = json.encodeToString(
            SyncState.serializer(),
            SyncState(policy = PolicySnapshot(deviceId = "phone-1", activeMode = "work")),
        )
        val stateObject = json.parseToJsonElement(encoded).let { it as kotlinx.serialization.json.JsonObject }
        assertFalse(stateObject.containsKey("revision"))

        val policyObject = stateObject["policy"] as kotlinx.serialization.json.JsonObject
        assertFalse(policyObject.containsKey("revision"))
    }

    @Test
    fun `state envelope wire shape is state plus revision`() {
        val text = """
            {"state": {"policy": {"deviceId": "phone-1", "activeMode": "work"}}, "revision": 7}
        """.trimIndent()

        val envelope = json.decodeFromString<StateEnvelope>(text)

        assertEquals(7L, envelope.revision)
        assertEquals("phone-1", envelope.state.policy.deviceId)
        assertEquals("work", envelope.state.policy.activeMode)

        val encoded = json.encodeToString(StateEnvelope.serializer(), envelope)
        assertTrue(encoded.contains("\"revision\":7"))
        assertTrue(encoded.contains("\"state\":{"))
    }

    @Test
    fun `conflict envelope is exactly revision and state`() {
        // Golden shape of the 409 Conflict body: the losing writer must be
        // able to rebase on exactly {revision, state} — nothing else.
        val conflict = json.decodeFromString<StateEnvelope>(
            """{"revision": 9, "state": {"policy": {"deviceId": "phone-1"}}}"""
        )

        assertEquals(9L, conflict.revision)
        assertEquals("phone-1", conflict.state.policy.deviceId)

        val keys = json.parseToJsonElement(
            json.encodeToString(StateEnvelope.serializer(), conflict)
        ).let { it as kotlinx.serialization.json.JsonObject }.keys
        assertEquals(setOf("state", "revision"), keys.toSet())
    }

    @Test
    fun `state update request carries baseRevision and optional hint`() {
        val decoded = json.decodeFromString<StateUpdateRequest>(
            """
                {"baseRevision": 7, "state": {"policy": {"deviceId": "d"}}, "hint": "policy"}
            """.trimIndent()
        )
        assertEquals(7L, decoded.baseRevision)
        assertEquals("d", decoded.state.policy.deviceId)
        assertEquals("policy", decoded.hint)

        val withoutHint = json.decodeFromString<StateUpdateRequest>(
            """{"baseRevision": 0, "state": {}}"""
        )
        assertNull(withoutHint.hint)
        assertEquals(SyncState(), withoutHint.state)
    }

    @Test
    fun `state update request roundtrip preserves conditional write`() {
        val request = StateUpdateRequest(
            baseRevision = 12L,
            state = SyncState(policy = PolicySnapshot(deviceId = "phone-1", activeMode = "focus")),
            hint = "policy",
        )
        assertEquals(
            request,
            json.decodeFromString(
                StateUpdateRequest.serializer(),
                json.encodeToString(StateUpdateRequest.serializer(), request),
            ),
        )
    }

    @Test
    fun `state document and envelope roundtrip`() {
        val state = SyncState(
            policy = PolicySnapshot(
                deviceId = "phone-1",
                capabilities = listOf(
                    CapabilityAvailability(id = "com.android.youtube", state = Availability.BLOCKED),
                ),
            ),
        )
        assertEquals(
            state,
            json.decodeFromString(SyncState.serializer(), json.encodeToString(SyncState.serializer(), state)),
        )

        val envelope = StateEnvelope(state = state, revision = 42L)
        assertEquals(
            envelope,
            json.decodeFromString(
                StateEnvelope.serializer(),
                json.encodeToString(StateEnvelope.serializer(), envelope),
            ),
        )
    }

    @Test
    fun `link payload roundtrip`() {
        val payload = LinkPayload(
            id = "l3",
            url = "https://example.com/article",
            title = "Article",
            sourceDeviceId = "phone-1",
            sourceDeviceName = "ZeroPhone",
            sentAtMillis = 1000L,
        )
        assertEquals(
            payload,
            json.decodeFromString(
                LinkPayload.serializer(),
                json.encodeToString(LinkPayload.serializer(), payload),
            ),
        )
    }

    @Test
    fun `push envelope decodes link-received with type discriminator`() {
        val text = """
            {"type": "link.received", "payload": {"id": "l1", "url": "https://example.com", "title": "Example", "sentAtMillis": 42}}
        """.trimIndent()

        val push = json.decodeFromString<SyncPushMessage>(text)

        assertTrue(push is SyncPushMessage.LinkReceived)
        val link = (push as SyncPushMessage.LinkReceived).payload
        assertEquals("l1", link.id)
        assertEquals("https://example.com", link.url)
        assertEquals(42L, link.sentAtMillis)
    }

    @Test
    fun `push wire names are exactly the fixed event discriminators`() {
        // Golden strings: the event names are protocol constants shared
        // with the server and the desktop client — a typo here is a
        // cross-device break, not a compile error.
        fun wireName(message: SyncPushMessage): String =
            json.parseToJsonElement(json.encodeToString(SyncPushMessage.serializer(), message))
                .let { it as kotlinx.serialization.json.JsonObject }["type"]
                .let { it as kotlinx.serialization.json.JsonPrimitive }
                .content

        assertEquals(
            "state.snapshot",
            wireName(SyncPushMessage.StateSnapshot(revision = 3)),
        )
        assertEquals(
            "state.updated",
            wireName(SyncPushMessage.StateUpdated(revision = 8, actorDeviceId = "phone-1")),
        )
        assertEquals(
            "link.received",
            wireName(SyncPushMessage.LinkReceived(LinkPayload(id = "l2", url = "https://x.dev"))),
        )
    }

    @Test
    fun `state-updated carries revision and actorDeviceId with exact field names`() {
        val encoded = json.encodeToString(
            SyncPushMessage.serializer(),
            SyncPushMessage.StateUpdated(revision = 8, actorDeviceId = "phone-1"),
        )
        val fields = json.parseToJsonElement(encoded)
            .let { it as kotlinx.serialization.json.JsonObject }
        assertEquals(
            "phone-1",
            (fields["actorDeviceId"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(8L, (fields["revision"] as kotlinx.serialization.json.JsonPrimitive).content.toLong())
        // The push never contains the state itself — catch-up is a REST pull.
        assertFalse(fields.containsKey("state"))
    }

    @Test
    fun `push envelope decodes state-updated with revision actor and hint`() {
        val push = json.decodeFromString<SyncPushMessage>(
            """{"type": "state.updated", "revision": 8, "actorDeviceId": "phone-1", "hint": "policy"}"""
        )
        assertEquals(
            SyncPushMessage.StateUpdated(revision = 8, actorDeviceId = "phone-1", hint = "policy"),
            push,
        )

        val withoutHint = json.decodeFromString<SyncPushMessage>(
            """{"type": "state.updated", "revision": 9, "actorDeviceId": "desktop-1"}"""
        )
        assertNull((withoutHint as SyncPushMessage.StateUpdated).hint)
    }

    @Test
    fun `push envelope decodes state-snapshot with current revision`() {
        val push = json.decodeFromString<SyncPushMessage>(
            """{"type": "state.snapshot", "revision": 3}"""
        )
        assertEquals(SyncPushMessage.StateSnapshot(revision = 3), push)
    }

    @Test
    fun `push envelope roundtrip preserves variants`() {
        val link = SyncPushMessage.LinkReceived(LinkPayload(id = "l2", url = "https://x.dev"))
        assertEquals(
            link,
            json.decodeFromString(
                SyncPushMessage.serializer(),
                json.encodeToString(SyncPushMessage.serializer(), link),
            ),
        )

        val updated = SyncPushMessage.StateUpdated(revision = 11, actorDeviceId = "phone-1")
        assertEquals(
            updated,
            json.decodeFromString(
                SyncPushMessage.serializer(),
                json.encodeToString(SyncPushMessage.serializer(), updated),
            ),
        )

        val snapshot = SyncPushMessage.StateSnapshot(revision = 11)
        assertEquals(
            snapshot,
            json.decodeFromString(
                SyncPushMessage.serializer(),
                json.encodeToString(SyncPushMessage.serializer(), snapshot),
            ),
        )
    }

    @Test
    fun `credentials and pairing claim roundtrip`() {
        val credentials = DeviceCredentials(
            deviceId = "desktop-1",
            token = "secret-token",
            serverUrl = "https://sync.example.com",
            pairedAtMillis = 777,
        )
        val decoded = json.decodeFromString<DeviceCredentials>(
            json.encodeToString(DeviceCredentials.serializer(), credentials)
        )
        assertEquals(credentials, decoded)

        val claim = PairingClaim(code = "123456", deviceName = "macbook")
        val decodedClaim = json.decodeFromString<PairingClaim>(
            json.encodeToString(PairingClaim.serializer(), claim)
        )
        assertEquals(claim, decodedClaim)
        assertEquals("desktop", decodedClaim.deviceKind)
    }
}
