// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class LikelyLabelsTest {
    @Test
    fun `only labels the classifier is fairly sure of are kept, surest first, at most six`() {
        val labels = (0 until 10).map { "label$it" }
        val scores = floatArrayOf(0.1f, 0.9f, 0.36f, 0.34f, 0.5f, 0.6f, 0.7f, 0.8f, 0.95f, 0.4f)
        assertEquals(listOf("label8", "label1", "label7", "label6", "label5", "label4"), likely(scores, labels))
    }

    @Test
    fun `nothing sure enough is no subject at all`() {
        assertEquals(emptyList<String>(), likely(floatArrayOf(0.2f, 0.1f), listOf("a", "b")))
    }
}
