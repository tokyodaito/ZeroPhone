package com.numenlabs.zerophone.sync.server.pairing

import java.security.SecureRandom

/**
 * Shortcodes for pairing: 6 random characters from [SecureRandom] over
 * an unambiguous alphabet — short enough to read off the phone screen
 * and retype on the desktop, with enough entropy for the single-use,
 * time-limited window in which they are valid. Ambiguous glyphs
 * (`0`/`O`, `1`/`I`) are excluded from [ALPHABET] so a code cannot be
 * misread; uniqueness among the outstanding codes is enforced by
 * [PairingStore] at issue time, not by the generator.
 */
object Shortcodes {

    const val CODE_LENGTH: Int = 6

    /** 32 glyphs without `0`, `O`, `1` and `I`. */
    val ALPHABET: String = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val random = SecureRandom()

    fun generate(): String =
        (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString(separator = "")
}
