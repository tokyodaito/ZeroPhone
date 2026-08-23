package com.numenlabs.zerophone.sync.server.pairing

import com.numenlabs.zerophone.core.model.DeviceCredentials
import com.numenlabs.zerophone.core.model.PairingClaim
import com.numenlabs.zerophone.core.model.SyncEndpoints
import com.numenlabs.zerophone.sync.server.auth.DeviceTokenStore
import com.numenlabs.zerophone.sync.server.auth.SyncServerJson
import com.numenlabs.zerophone.sync.server.auth.installDeviceTokenAuth
import com.numenlabs.zerophone.sync.server.state.StateStore
import com.numenlabs.zerophone.sync.server.state.WsBroadcaster
import com.numenlabs.zerophone.sync.server.state.installStateSync
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * HTTP flow of shortcode pairing against the full wiring: a code minted
 * out-of-band is exchanged exactly once for device credentials whose
 * token immediately authenticates the protected API.
 */
class PairingFlowTest {

    private val tempDirs = mutableListOf<Path>()
    private val json = Json { ignoreUnknownKeys = true }

    @After
    fun tearDown() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun newDir(): Path =
        Files.createTempDirectory("pairing-flow-test").also { tempDirs.add(it) }

    private fun ApplicationTestBuilder.serverApp(
        tokens: DeviceTokenStore,
        pairing: PairingStore,
    ) {
        application {
            installServerApp(tokens, pairing)
        }
    }

    private fun Application.installServerApp(
        tokens: DeviceTokenStore,
        pairing: PairingStore,
    ) {
        install(WebSockets)
        installDeviceTokenAuth(tokens)
        installStateSync(StateStore(newDir().resolve("state.json")), WsBroadcaster())
        installPairing(pairing, tokens)
    }

    private fun claimBody(code: String, deviceName: String = "work-desktop"): String =
        SyncServerJson.encodeToString(
            PairingClaim.serializer(),
            PairingClaim(code = code, deviceName = deviceName),
        )

    @Test
    fun `claiming a valid code returns credentials that authenticate the API`() = testApplication {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val pairing = PairingStore(dir.resolve(PairingStore.FILE_NAME))
        val code = runBlocking { pairing.issue() }
        serverApp(tokens, pairing)

        val response = client.post(SyncEndpoints.PAIRING_CLAIM) {
            contentType(ContentType.Application.Json)
            setBody(claimBody(code))
        }

        assertTrue(response.status.isSuccess())
        val credentials = json.decodeFromString(DeviceCredentials.serializer(), response.bodyAsText())
        assertEquals("work-desktop", credentials.deviceName)
        assertTrue(credentials.deviceId.isNotBlank())
        assertTrue(credentials.token.isNotBlank())

        val state = client.get(SyncEndpoints.STATE) {
            header(HttpHeaders.Authorization, "Bearer ${credentials.token}")
        }
        assertTrue(
            "the freshly issued token must authenticate the protected API",
            state.status.isSuccess(),
        )
    }

    @Test
    fun `unknown, consumed and malformed claims answer 404, 409 and 400`() = testApplication {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val pairing = PairingStore(dir.resolve(PairingStore.FILE_NAME))
        val code = runBlocking { pairing.issue() }
        serverApp(tokens, pairing)

        assertEquals(
            HttpStatusCode.NotFound,
            client.post(SyncEndpoints.PAIRING_CLAIM) {
                contentType(ContentType.Application.Json)
                setBody(claimBody("000000"))
            }.status,
        )

        val first = client.post(SyncEndpoints.PAIRING_CLAIM) {
            contentType(ContentType.Application.Json)
            setBody(claimBody(code))
        }
        assertTrue(first.status.isSuccess())

        assertEquals(
            "every shortcode is single-use",
            HttpStatusCode.Conflict,
            client.post(SyncEndpoints.PAIRING_CLAIM) {
                contentType(ContentType.Application.Json)
                setBody(claimBody(code))
            }.status,
        )

        assertEquals(
            HttpStatusCode.BadRequest,
            client.post(SyncEndpoints.PAIRING_CLAIM) {
                contentType(ContentType.Application.Json)
                setBody("{not-json")
            }.status,
        )
    }

    @Test
    fun `re-pairing with a fresh code issues a distinct device identity`() = testApplication {
        val dir = newDir()
        val tokens = DeviceTokenStore(dir.resolve("device-tokens.json"))
        val pairing = PairingStore(dir.resolve(PairingStore.FILE_NAME))
        serverApp(tokens, pairing)

        val firstCode = runBlocking { pairing.issue() }
        val first = json.decodeFromString(
            DeviceCredentials.serializer(),
            client.post(SyncEndpoints.PAIRING_CLAIM) {
                contentType(ContentType.Application.Json)
                setBody(claimBody(firstCode))
            }.bodyAsText(),
        )

        val secondCode = runBlocking { pairing.issue() }
        val second = json.decodeFromString(
            DeviceCredentials.serializer(),
            client.post(SyncEndpoints.PAIRING_CLAIM) {
                contentType(ContentType.Application.Json)
                setBody(claimBody(secondCode, "home-desktop"))
            }.bodyAsText(),
        )

        assertNotEquals(first.deviceId, second.deviceId)
        assertEquals("home-desktop", second.deviceName)
    }
}
