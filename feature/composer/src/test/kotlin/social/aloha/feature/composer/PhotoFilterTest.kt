// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFilterTest {
    /** [rgb] through the filter's matrix, offsets in 0…1, the way the matrix is defined. */
    private fun PhotoFilter.apply(vararg rgb: Float): FloatArray {
        val m = matrix
        return FloatArray(3) { row ->
            (0..2).sumOf { (m[row * 5 + it] * rgb[it]).toDouble() }.toFloat() + m[row * 5 + 3] + m[row * 5 + 4]
        }
    }

    @Test
    fun `original changes nothing`() {
        assertArrayEquals(floatArrayOf(0.2f, 0.5f, 0.9f), PhotoFilter.Original.apply(0.2f, 0.5f, 0.9f), DELTA)
    }

    @Test
    fun `mono turns red into its luminance, the same in every channel`() {
        val grey = PhotoFilter.Mono.apply(1f, 0f, 0f)
        assertArrayEquals(floatArrayOf(0.2126f, 0.2126f, 0.2126f), grey, DELTA)
    }

    @Test
    fun `steps apply in order, as css applies them left to right`() {
        // noir: grey first, then contrast 1.3 around the middle, then brightness 0.9
        val expected = (0.2126f * 1.3f + (0.5f - 0.65f)) * 0.9f
        assertEquals(expected, PhotoFilter.Noir.apply(1f, 0f, 0f)[0], DELTA)
    }

    @Test
    fun `android's matrix scales only the offsets to 0 to 255`() {
        val m = PhotoFilter.Faded.matrix
        val android = PhotoFilter.Faded.androidMatrix
        assertEquals(m[0], android[0], DELTA)
        assertEquals(m[4] * 255f, android[4], DELTA)
    }

    @Test
    fun `no filter touches alpha`() {
        PhotoFilter.entries.forEach { filter ->
            val m = filter.matrix
            assertArrayEquals(filter.name, floatArrayOf(0f, 0f, 0f, 1f, 0f), m.copyOfRange(15, 20), DELTA)
        }
    }

    private companion object {
        const val DELTA = 0.0005f
    }
}
