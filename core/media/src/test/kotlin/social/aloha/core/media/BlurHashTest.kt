// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlurHashTest {
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private fun encode83(value: Int, length: Int): String =
        (length - 1 downTo 0).joinToString("") { power -> alphabet[value / pow83(power) % 83].toString() }

    private fun pow83(power: Int): Int = (1..power).fold(1) { acc, _ -> acc * 83 }

    /** One component, no detail: the whole image is the one colour. */
    private fun uniform(rgb: Int) = encode83(0, 1) + encode83(0, 1) + encode83(rgb, 4)

    @Test
    fun `a single-component hash decodes to its colour everywhere`() {
        val pixels = BlurHash.decode(uniform(0x3366CC), 8, 4)!!
        assertEquals(32, pixels.size)
        // one step of rounding through linear light and back
        pixels.forEach { pixel ->
            assertEquals(0x33, pixel shr 16 and 0xFF, 1.0)
            assertEquals(0x66, pixel shr 8 and 0xFF, 1.0)
            assertEquals(0xCC, pixel and 0xFF, 1.0)
            assertEquals(0xFF, pixel ushr 24)
        }
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Double) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$actual is not $expected")

    @Test
    fun `the average colour comes from the first component without decoding`() {
        assertEquals(0xFF3366CC.toInt(), BlurHash.averageColour(uniform(0x3366CC)))
    }

    @Test
    fun `a real hash decodes to a varied, opaque image of the asked size`() {
        val pixels = BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 32, 32)
        assertNotNull(pixels)
        assertEquals(32 * 32, pixels!!.size)
        assertTrue(pixels.all { it ushr 24 == 0xFF })
        assertTrue(pixels.toSet().size > 10)
    }

    @Test
    fun `malformed hashes are no image rather than a crash`() {
        for (hash in listOf(
            "",
            "short",
            "LEHV6nWB2yk8pyo0adR*.7kCMdn",
            "LEHV6nWB2yk8pyo0adR*.7kCMdnj!",
            "LEHV6nWB2yk8pyo0adR*.7kCMénj",
        )) {
            assertNull(BlurHash.decode(hash, 4, 4), hash)
        }
        assertNull(BlurHash.decode(uniform(0), 0, 4))
        assertNull(BlurHash.averageColour("abc"))
    }
}
