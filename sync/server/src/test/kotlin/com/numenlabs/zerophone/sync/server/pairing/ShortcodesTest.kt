package com.numenlabs.zerophone.sync.server.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator itself: SecureRandom draws of a fixed length over the
 * unambiguous alphabet — no `0`/`O`/`1`/`I` can ever show up in a code
 * a human is asked to retype.
 */
class ShortcodesTest {

    @Test
    fun `codes are six characters over the unambiguous alphabet`() {
        repeat(64) {
            val code = Shortcodes.generate()
            assertEquals(Shortcodes.CODE_LENGTH, code.length)
            assertTrue("code uses only alphabet glyphs: $code", code.all { it in Shortcodes.ALPHABET })
        }
    }

    @Test
    fun `the alphabet excludes the ambiguous glyphs`() {
        for (ambiguous in listOf('0', 'O', '1', 'I')) {
            assertFalse("$ambiguous must not be in the alphabet", ambiguous in Shortcodes.ALPHABET)
        }
        assertEquals(32, Shortcodes.ALPHABET.length)
        assertEquals(
            "no duplicated glyphs",
            Shortcodes.ALPHABET.length,
            Shortcodes.ALPHABET.toCharArray().distinct().size,
        )
    }
}
