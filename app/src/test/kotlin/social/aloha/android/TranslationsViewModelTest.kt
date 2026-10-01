// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.translation.TranslationRepository
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.TranslationUi

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranslationsViewModelTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val colors = RichTextColors(
        androidx.compose.ui.graphics.Color.Blue,
        androidx.compose.ui.graphics.Color.Gray,
        androidx.compose.ui.graphics.Color.LightGray,
    )
    private val row = StatusRowMapper(RichTextCache(), colors)
        .map(StatusSamples.linked.copy(language = "de"), "1")

    @Before
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun close() {
        Dispatchers.resetMain()
        fixture.close()
        server.close()
    }

    private fun viewModel(translates: Boolean): TranslationsViewModel = runBlocking {
        val capabilities = ServerCapabilities.minimal(server.url("/").toString()).copy(translation = translates)
        fixture.signIn(server.url("/"), capabilities)
        fixture.accounts.activeAccount.filterNotNull().first()
        TranslationsViewModel(fixture.accounts, TranslationRepository(fixture.clients)).apply {
            readerLanguages = { listOf("en-GB") }
        }
    }

    @Test
    fun `a post in another language is translated into the reader's`() {
        server.enqueue(json(200, """{"content":"<p>Worth reading</p>","provider":"DeepL"}"""))
        val translations = viewModel(translates = true)
        assertTrue(translations.offers(row))
        translations.translate(row)
        server.takeRequest().let { assertEquals("lang=en-GB", it.body?.utf8()) }
        awaitSettled(translations)
        assertEquals(TranslationUi.Done("<p>Worth reading</p>", null, "DeepL"), translations.stateOf(row.statusId))
        translations.showOriginal(row.statusId)
        assertNull(translations.stateOf(row.statusId))
    }

    @Test
    fun `a refusal keeps the server's sentence, a proxy's page does not`() {
        server.enqueue(json(503, """{"error":"no translation provider is configured"}"""))
        server.enqueue(MockResponse.Builder().code(503).body("<html><body>Bad gateway</body></html>").build())
        val translations = viewModel(translates = true)
        translations.translate(row)
        awaitSettled(translations)
        assertEquals(TranslationUi.Failed("no translation provider is configured"), translations.stateOf(row.statusId))
        translations.showOriginal(row.statusId)
        translations.translate(row)
        awaitSettled(translations)
        assertEquals(TranslationUi.Failed(null), translations.stateOf(row.statusId))
    }

    @Test
    fun `nothing is offered by a server that does not translate`() {
        assertFalse(viewModel(translates = false).offers(row))
    }

    private fun awaitSettled(translations: TranslationsViewModel) {
        val until = System.currentTimeMillis() + TIMEOUT
        while (translations.stateOf(row.statusId) == TranslationUi.Working && System.currentTimeMillis() < until) {
            Thread.sleep(POLL)
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val TIMEOUT = 5_000L
        const val POLL = 10L
    }
}
