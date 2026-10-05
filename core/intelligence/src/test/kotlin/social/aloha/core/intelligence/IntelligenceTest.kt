// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Optional
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class IntelligenceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private class Model(private val answer: (String) -> Drafted) : LanguageModel {
        var asked: String? = null

        override suspend fun availability() = ModelAvailability.Available

        override fun prepare() = Unit

        override suspend fun generate(instructions: String, prompt: String): Drafted {
            asked = prompt
            return answer(prompt)
        }
    }

    private class Reader(private val seen: Observations?) : PictureReader {
        override suspend fun observe(file: File) = seen
    }

    private fun intelligence(model: LanguageModel? = null, reader: PictureReader? = null) =
        Intelligence(context, Optional.ofNullable(model), Optional.ofNullable(reader))

    private suspend fun describe(seen: Observations): String =
        (intelligence(reader = Reader(seen)).describe(File("p.jpg")) as Drafted.Text).text

    @Test
    fun `a build without a model or a reader offers nothing`() = runTest {
        val none = intelligence()
        assertFalse(none.offered)
        assertEquals(ModelAvailability.NotEligible, none.availability())
        assertEquals(Drafted.Failed, none.describe(File("p.jpg")))
    }

    @Test
    fun `alt text names what was found, counts people and ends with the mark`() = runTest {
        val text = describe(Observations(subjects = listOf("beach", "sky"), people = 4))
        assertEquals("4 people with beach, sky. (AI generated)", text)
        assertEquals("One person. (AI generated)", describe(Observations(people = 1)))
    }

    @Test
    fun `the draft stays within its length`() = runTest {
        val many = (0 until 80).map { "subject with a rather long name, number $it" }
        val long = describe(Observations(many, 3))
        assertTrue(long.length <= AltText.MAXIMUM + intelligence().altTextMark.length)
        assertTrue(long.endsWith(" (AI generated)"))
    }

    @Test
    fun `nothing recognised is no draft at all`() = runTest {
        assertEquals(Drafted.Failed, intelligence(reader = Reader(Observations())).describe(File("p.jpg")))
    }

    @Test
    fun `a rewrite keeps every mention, hashtag and link, and the model never sees them`() = runTest {
        val model = Model { prompt -> Drafted.Text(prompt.replace("hi", "Hello")) }
        val draft = "hi @alice@example.social #waves"
        val rewritten = intelligence(model).rewrite(draft, RewriteStyle.Rephrase, 500) { true }
        assertEquals(Drafted.Text("Hello @alice@example.social #waves"), rewritten)
        assertFalse(model.asked!!.contains("@alice"))
    }

    @Test
    fun `a rewrite that loses or adds a mention, or does not fit, is turned down`() = runTest {
        val dropping = intelligence(Model { Drafted.Text("Hello there") })
        assertEquals(Drafted.Failed, dropping.rewrite("hi @alice", RewriteStyle.Rephrase, 500) { true })
        val echoing = intelligence(Model { Drafted.Text(it) })
        assertEquals(Drafted.TooLong, echoing.rewrite("hi @alice", RewriteStyle.Rephrase, 500) { false })
        val adding = intelligence(Model { Drafted.Text("$it, cc @carol") })
        assertEquals(Drafted.Failed, adding.rewrite("hi @alice", RewriteStyle.Rephrase, 500) { true })
        val bracketed = intelligence(Model { Drafted.Text(it) })
        assertEquals(Drafted.Failed, bracketed.rewrite("⟦0⟧ hi @alice", RewriteStyle.Rephrase, 500) { true })
        val refusing = intelligence(Model { Drafted.Refused })
        assertEquals(Drafted.Refused, refusing.rewrite("hi", RewriteStyle.Rephrase, 9) { true })
    }

    @Test
    fun `a summary reads a long thread from its start, within what the model is given`() = runTest {
        val model = Model { Drafted.Text("A summary.") }
        val passages = (1..400).map { "@someone: post number $it, with some words in it" }
        intelligence(model).summarise(passages)
        assertTrue(model.asked!!.startsWith("@someone: post number 1,"))
        assertTrue(model.asked!!.length <= 6_000)
    }
}
