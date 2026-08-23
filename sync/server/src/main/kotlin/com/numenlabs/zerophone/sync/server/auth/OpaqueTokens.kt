package com.numenlabs.zerophone.sync.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Opaque device tokens: 256 random bits from [SecureRandom], base64url
 * encoded. No embedded claims, no signatures, no expiry — the server is
 * the single source of truth for validity via the stored token hash.
 */
object OpaqueTokens {

    /** 256 bits of entropy before base64url encoding. */
    const val TOKEN_BYTES: Int = 32

    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** SHA-256 of the raw token, hex encoded — the only thing ever persisted. */
object TokenHasher {

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}
