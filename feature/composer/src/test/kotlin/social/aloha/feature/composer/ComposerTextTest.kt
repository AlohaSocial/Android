// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerTextTest {
    private fun at(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun `the word at the cursor completes by its prefix`() {
        assertEquals(Completing(CompletionKind.Account, "bo", 3, 6), ComposerText.completing(at("hi @bo")))
        assertEquals(CompletionKind.Hashtag, ComposerText.completing(at("#surf"))?.kind)
        assertEquals("bob@remote.ex", ComposerText.completing(at("@bob@remote.ex"))?.query)
        assertEquals("pa", ComposerText.completing(at("yum :pa"))?.query)
    }

    @Test
    fun `nothing completes mid-word, on a lone prefix, a short emoji or a selection`() {
        assertNull(ComposerText.completing(at("mail@bob")))
        assertNull(ComposerText.completing(at("hi @")))
        assertNull(ComposerText.completing(at("at 10:3")))
        assertNull(ComposerText.completing(at("yum :p")))
        assertNull(ComposerText.completing(TextFieldValue("#surf", TextRange(1, 3))))
    }

    @Test
    fun `the completion follows the cursor, not the end of the text`() {
        assertEquals("bo", ComposerText.completing(at("@bo and more", cursor = 3))?.query)
    }

    @Test
    fun `a completion replaces the word and leaves the cursor after one space`() {
        val value = at("hi @bo")
        val done = ComposerText.complete(value, ComposerText.completing(value)!!, "@bob@remote.example")
        assertEquals("hi @bob@remote.example ", done.text)
        assertEquals(TextRange(done.text.length), done.selection)
        // the space already there is kept, not doubled
        val middle = at("@bo there", cursor = 3)
        assertEquals("@bob there", ComposerText.complete(middle, ComposerText.completing(middle)!!, "@bob").text)
    }

    @Test
    fun `mentions, hashtags and links are what gets coloured`() {
        val text = "@bob see #surf at https://x.test/a and mail@me"
        val coloured = ComposerText.spans(text).map { text.substring(it) }.sorted()
        assertEquals(listOf("#surf", "@bob", "https://x.test/a"), coloured)
    }

    @Test
    fun `what is past the limit starts where the longest fitting beginning ends`() {
        assertEquals(5, ComposerText.overFrom("hello world") { it.length <= 5 })
        assertEquals(0, ComposerText.overFrom("hello") { false })
        // an emoji outside the basic plane is never split
        assertEquals(1, ComposerText.overFrom("a\uD83C\uDF0Ab") { it.length <= 2 })
    }

    @Test
    fun `the language is read from the words, not the mentions, tags and links`() {
        assertEquals("aloha kakou", ComposerText.prose("@bob aloha #surf kakou https://x.test/a"))
    }

    @Test
    fun `the hashtags a post uses are found without their hash`() {
        assertEquals(listOf("Surf", "café"), ComposerText.hashtags("#Surf and #café, not #1 or a#b"))
    }
}
