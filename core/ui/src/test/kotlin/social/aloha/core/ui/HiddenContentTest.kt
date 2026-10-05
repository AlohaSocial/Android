// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.WarningReveal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HiddenContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an opened warning opens the same one by the same author, or by anyone, as Reading says`() {
        val author = WarningReveals(WarningReveal.SameAuthor).apply { opened("a", "Spoilers") }
        assertTrue(author.isOpen("a", "re: re: SPOILERS "))
        assertFalse(author.isOpen("b", "Spoilers"))

        val anyone = WarningReveals(WarningReveal.Everyone).apply { opened("a", "Spoilers") }
        assertTrue(anyone.isOpen("b", "Re: spoilers"))
        anyone.closed("b", "spoilers")
        assertFalse(anyone.isOpen("a", "Spoilers"))

        val never = WarningReveals(WarningReveal.Never).apply { opened("a", "Spoilers") }
        assertFalse(never.isOpen("a", "Spoilers"))
    }

    @Test
    fun `the words a filter matched are painted wherever they occur, in any case`() {
        val style = SpanStyle(color = Color.Red)
        val painted = highlighted(AnnotatedString("Election news: the ELECTION is near"), listOf("election"), style)
        assertEquals(listOf(0 to 8, 19 to 27), painted.spanStyles.map { it.start to it.end })
        assertEquals(AnnotatedString("nothing here"), highlighted(AnnotatedString("nothing here"), listOf("x"), style))
    }

    @Test
    fun `a tall text is clipped while collapsed, shown whole when open, and a short one is left alone`() {
        var tall by mutableStateOf(false)
        var collapsed by mutableStateOf(true)
        var height by mutableStateOf(400.dp)
        compose.setContent {
            Collapsible(
                collapsed = collapsed,
                tall = tall,
                onTall = { tall = it },
                content = { Box(Modifier.size(100.dp, height)) },
                toggle = { Box(Modifier.size(100.dp, TOGGLE)) },
            )
        }
        // the toggle sits under the clipped text from the first layout on
        compose.onRoot().assertHeightIsEqualTo(145.dp + TOGGLE)
        assertTrue(tall)

        collapsed = false
        compose.onRoot().assertHeightIsEqualTo(400.dp + TOGGLE)

        height = 100.dp
        compose.onRoot().assertHeightIsEqualTo(100.dp)
        assertFalse(tall)
    }

    private companion object {
        val TOGGLE = 20.dp
    }
}
