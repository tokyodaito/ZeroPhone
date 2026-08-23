package com.numenlabs.zerophone.sync.server.pairing

import java.security.SecureRandom
import java.util.Base64

/** Opaque device ids minted at pairing time — no embedded claims. */
object PairingIds {

    private val random = SecureRandom()

    fun newDeviceId(): String {
        val bytes = ByteArray(9)
        random.nextBytes(bytes)
        return "dev-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
