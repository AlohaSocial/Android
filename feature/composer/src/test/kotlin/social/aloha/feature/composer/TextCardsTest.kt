// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.app.Application
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class TextCardsTest {
    @Test
    fun `a card is square, on its background, at any size`() {
        val card = CardRenderer.render("Aloha", 0xFF00605A.toInt(), 360)
        assertEquals(360, card.width)
        assertEquals(360, card.height)
        // a corner is background: the words stay inside the margin
        assertEquals(0xFF00605A.toInt(), card.getPixel(2, 2))
    }

    @Test
    fun `long words shrink to fit rather than run off the card`() {
        val card = CardRenderer.render("x".repeat(120), Color.BLACK, 360)
        // nothing drawn in the top and bottom margins
        assertEquals(Color.BLACK, card.getPixel(180, 4))
        assertEquals(Color.BLACK, card.getPixel(180, 355))
    }

    @Test
    fun `only a short plain single post can be a card`() {
        assertTrue(TextCards.fits("Aloha 🌺", segments = 1, attached = 0))
        assertTrue(TextCards.fits("é".repeat(120), segments = 1, attached = 0))
        assertFalse(TextCards.fits("é".repeat(121), segments = 1, attached = 0))
        assertFalse(TextCards.fits("   ", segments = 1, attached = 0))
        assertFalse(TextCards.fits("Aloha", segments = 2, attached = 0))
        assertFalse(TextCards.fits("Aloha", segments = 1, attached = 1))
        // a die rolled when posting cannot be on a card drawn before
        assertFalse(TextCards.fits("/flip for it", segments = 1, attached = 0))
    }
}
