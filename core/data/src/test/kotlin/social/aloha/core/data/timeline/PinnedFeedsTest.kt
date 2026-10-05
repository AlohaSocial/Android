// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class PinnedFeedsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val feeds = PinnedFeeds(settings)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun account(local: Boolean = true, federated: Boolean = true) = fixture.signIn(
        server.url("/"),
        ServerCapabilities.minimal(server.url("/").toString()).copy(localFeed = local, federatedFeed = federated),
    )

    private val following = PinnedFeed(PinnedFeed.Kind.Following)
    private val local = PinnedFeed(PinnedFeed.Kind.ThisServer)
    private val everyone = PinnedFeed(PinnedFeed.Kind.Everyone)

    @Test
    fun `before anything is pinned Home holds what the server serves, the feed it opened on first`() = runBlocking {
        val reader = account(federated = false)
        assertEquals(listOf(following, local), feeds.feeds(reader).first())
        settings.update(reader.id) { it.copy(homeSource = TimelineSource.Local) }
        assertEquals(listOf(local, following), feeds.feeds(reader).first())
    }

    @Test
    fun `pinned feeds keep their order, once each, and one the server stopped serving is left out`() = runBlocking {
        val reader = account(federated = false)
        val list = PinnedFeed(PinnedFeed.Kind.List("7", "Friends"), name = "Close friends")
        feeds.save(reader, listOf(list, everyone, following, list))
        assertEquals(listOf(list, following), feeds.feeds(reader).first())
    }

    @Test
    fun `a feed renamed keeps its name and icon, and one removed leaves the rest as they were`() = runBlocking {
        val reader = account()
        val renamed = following.copy(name = "Friends first", icon = "news")
        feeds.save(reader, listOf(renamed, local, everyone))
        assertEquals(listOf(renamed, local, everyone), feeds.feeds(reader).first())
        feeds.save(reader, listOf(renamed, everyone))
        assertEquals(listOf(renamed, everyone), feeds.feeds(reader).first())
    }
}
