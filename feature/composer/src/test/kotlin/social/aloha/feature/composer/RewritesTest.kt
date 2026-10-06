// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import java.util.Optional
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.intelligence.LanguageModel
import social.aloha.core.intelligence.ModelAvailability
import social.aloha.core.intelligence.RewriteStyle

@RunWith(RobolectricTestRunner::class)
internal class RewritesTest : ComposerTestSetup() {
    private class Model(private val rewrite: (String) -> String) : LanguageModel {
        override suspend fun availability() = ModelAvailability.Available

        override fun prepare() = Unit

        override suspend fun generate(instructions: String, prompt: String) = Drafted.Text(rewrite(prompt))
    }

    private suspend fun rewriting(rewrite: (String) -> String): ComposerViewModel {
        intelligence = Intelligence(context, Optional.of(Model(rewrite)), Optional.empty())
        val viewModel = open()
        viewModel.await { it.ready }
        withTimeout(10.seconds) { viewModel.rewrites.offered.first { it } }
        return viewModel
    }

    @Test
    fun `a rewrite is offered beside the draft and takes its place only on Replace`() = runBlocking {
        val viewModel = rewriting { it.replace("teh", "the") }
        viewModel.type(0, "See teh waves, @bob")
        viewModel.await { it.remaining.isNotEmpty() }
        viewModel.rewrites.start(RewriteStyle.Proofread)
        val proposed = withTimeout(10.seconds) { viewModel.rewrites.state.first { it is Rewrite.Proposed } }
        assertEquals("See the waves, @bob", (proposed as Rewrite.Proposed).proposal)
        assertEquals("See teh waves, @bob", viewModel.segments[0].text)
        viewModel.rewrites.replace()
        assertEquals("See the waves, @bob", viewModel.segments[0].text)
    }

    @Test
    fun `a rewrite longer than the post has room for is turned down`() = runBlocking {
        val viewModel = rewriting { it + " " + "very ".repeat(1_100) }
        viewModel.type(0, "Short and sweet")
        viewModel.await { it.remaining.isNotEmpty() }
        viewModel.rewrites.start(RewriteStyle.Rephrase)
        val declined = withTimeout(10.seconds) { viewModel.rewrites.state.first { it is Rewrite.Declined } }
        assertEquals(Rewrite.Declined(Drafted.TooLong), declined)
        assertEquals("Short and sweet", viewModel.segments[0].text)
    }

    @Test
    fun `a word diff keeps what both share and marks what each has alone`() {
        assertEquals(
            listOf(
                Piece("the quick ", Piece.Kind.Same),
                Piece("red", Piece.Kind.Added),
                Piece("brown", Piece.Kind.Removed),
                Piece(" fox", Piece.Kind.Same),
            ),
            wordDiff("the quick brown fox", "the quick red fox"),
        )
    }
}
