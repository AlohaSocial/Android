// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.reading.ReplyHiding
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.MockServerConfiguration
import social.aloha.core.testing.MockSocialServer
import social.aloha.core.testing.SignedInFixture

private const val ROUTE = "/api/v1/social/feed_preferences"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReadingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockSocialServer(MockServerConfiguration.NextcloudWithRewrite).start()
    private val store = ViewModelStore()

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        fixture.close()
        server.close()
    }

    private fun open(socialName: String = "nextcloud-social"): ReadingViewModel = runBlocking {
        fixture.signIn(server.apiBase, ServerCapabilities.minimal("${server.apiBase}").copy(softwareName = socialName))
        ReadingViewModel(
            ReadingPreferences(InMemoryDataStore(emptyPreferences())),
            fixture.accounts,
            ReplyHiding(fixture.clients, fixture.timelines),
        ).also { store.put("reading", it) }
    }

    private fun ReadingViewModel.settled(check: (ServerReplies) -> Boolean): ServerReplies =
        runBlocking { withTimeout(WAIT_MILLIS) { replies.first(check) } }

    @Test
    fun `an account not on Nextcloud Social asks nothing and shows no switch`() {
        server.pin("GET", ROUTE, 200, """{"hide_replies":true}""")
        val reading = open(socialName = "mastodon")
        assertEquals(ServerReplies(), reading.replies.value)
        assertTrue(server.requests.none { it.url.encodedPath.endsWith(ROUTE) })
    }

    @Test
    fun `the switch shows as the server holds it, and a change taken shows the new state`() {
        server.pin("GET", ROUTE, 200, """{"hide_replies":true}""")
        server.pin("PATCH", ROUTE, 200, """{"hide_replies":false}""")
        val reading = open()
        assertEquals(ServerReplies(hidden = true), reading.settled { it.hidden != null })
        reading.hideReplies(false)
        assertEquals(ServerReplies(hidden = false), reading.settled { it.hidden == false })
    }

    @Test
    fun `a change refused leaves the switch as it was and says so`() {
        server.pin("GET", ROUTE, 200, """{"hide_replies":true}""")
        server.pin("PATCH", ROUTE, 422, """{"error":"hide_replies must be a boolean"}""")
        val reading = open()
        reading.settled { it.hidden != null }
        reading.hideReplies(false)
        assertEquals(ServerReplies(hidden = true, refused = true), reading.settled { it.refused })
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
