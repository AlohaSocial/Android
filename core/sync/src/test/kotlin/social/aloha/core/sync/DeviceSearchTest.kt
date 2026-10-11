// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.Intent
import androidx.appsearch.builtintypes.Thing
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.AppIntents
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples

/** What the index was asked to do, in order. */
private class RecordingIndex : SearchIndex {
    val replaced = CopyOnWriteArrayList<Pair<String, List<Any>>>()
    val removed = CopyOnWriteArrayList<String>()

    override suspend fun replace(namespace: String, documents: List<Any>) {
        replaced += namespace to documents
    }

    override suspend fun remove(namespace: String) {
        removed += namespace
    }

    override suspend fun clear() = Unit
}

/** A server whose reader follows bob and bookmarked one public post and one for followers only. */
private class Followed : Dispatcher() {
    @Volatile var failing = false

    /** Whether the first page of bookmarks holds only private ones, with the public one on the next. */
    @Volatile var privateFirst = false

    /** Runs as the bookmarks are asked for, while a refresh is between its fetch and its write. */
    @Volatile var meanwhile: () -> Unit = {}

    override fun dispatch(request: RecordedRequest): MockResponse = when {
        failing -> MockResponse.Builder().code(503).build()

        request.url.encodedPath.endsWith("/following") -> json(
            """[{"id":"9","username":"bob","acct":"bob@other.social","display_name":"Bob"}]""",
        )

        privateFirst && request.url.encodedPath.endsWith("/bookmarks") && request.url.queryParameter("page") == null ->
            json("[" + status("31", "private", "<p>Only for followers</p>") + "]").newBuilder()
                .addHeader("Link", "<${request.url.newBuilder().addQueryParameter("page", "2").build()}>; rel=\"next\"")
                .build()

        privateFirst && request.url.encodedPath.endsWith("/bookmarks") ->
            json("[" + status("30", "public", "<p>Sunrise paddle</p>") + "]")

        request.url.encodedPath.endsWith("/bookmarks") -> meanwhile().let {
            json(
                "[" + status("30", "public", "<p>Sunrise paddle</p>") + "," +
                    status("31", "private", "<p>Only for followers</p>") + "]",
            )
        }

        else -> MockResponse.Builder().code(404).build()
    }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun status(id: String, visibility: String, content: String) =
        """{"id":"$id","created_at":"2026-09-29T12:00:00.000Z","visibility":"$visibility","content":"$content",""" +
            """"uri":"https://other.social/statuses/$id","url":"https://other.social/@bob/$id",""" +
            """"account":{"id":"9","username":"bob","acct":"bob@other.social","display_name":"Bob"}}"""
}

@RunWith(RobolectricTestRunner::class)
class DeviceSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val server = Followed()
    private val web = MockWebServer().apply {
        dispatcher = server
        start()
    }
    private val index = RecordingIndex()
    private val shown = fixture.deviceSearch
    private val search = DeviceSearch(
        context,
        index,
        fixture.accounts,
        fixture.profiles,
        fixture.saved,
        fixture.searches,
        shown,
    )

    @After
    fun close() {
        fixture.close()
        web.close()
    }

    private fun Thing.opens(): Intent = Intent.parseUri(potentialActions.single().uri, Intent.URI_INTENT_SCHEME)

    @Test
    fun `an account's entries are itself, whom it follows, its public bookmarks and its searches`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        fixture.searches.remember(reader, "surfing")
        search.refresh(reader)
        val (namespace, all) = index.replaced.single()
        val documents = all.filterIsInstance<Thing>()
        assertEquals(DeviceSearch.namespace(reader.id), namespace)
        val people = documents.filter { it.id == "self" || it.id.startsWith("follow:") }
        assertEquals(listOf("self", "follow:9"), people.map { it.id })
        assertEquals(listOf("@bob@other.social"), people.last().alternateNames)
        val things = documents.filter { it.id.startsWith("bookmark:") || it.id.startsWith("search:") }
        assertEquals(listOf("bookmark:30", "search:surfing"), things.map { it.id })
        assertEquals("Sunrise paddle", things.first().name)
    }

    @Test
    fun `each entry opens as the account it belongs to, a post, a profile or a search`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        val documents = DeviceSearch.documents(
            context,
            reader,
            Gathered(listOf(StatusSamples.bob), listOf(StatusSamples.post().copy(id = "30")), listOf("surfing")),
        )
        val post = documents.single { it.id == "bookmark:30" }.opens()
        assertEquals(AppIntents.ACTION_OPEN, post.action)
        assertEquals(reader.id, post.getStringExtra(AppIntents.EXTRA_ACCOUNT))
        assertEquals("30", post.getStringExtra(AppIntents.EXTRA_STATUS))
        assertEquals(context.packageName, post.component?.packageName ?: post.`package`)
        val profile = documents.single { it.id == "follow:2" }.opens()
        assertEquals(StatusSamples.bob.id, profile.getStringExtra(AppIntents.EXTRA_PROFILE))
        val searched = documents.single { it.id == "search:surfing" }.opens()
        assertEquals(AppIntents.ACTION_SEARCH, searched.action)
        assertEquals("surfing", searched.getStringExtra(AppIntents.EXTRA_QUERY))
    }

    @Test
    fun `a post only some may read is never in it, and a content warning stands in for what it hides`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        val post = StatusSamples.post()
        val documents = DeviceSearch.documents(
            context,
            reader,
            Gathered(
                following = emptyList(),
                bookmarks = listOf(
                    post.copy(id = "40", visibility = Visibility.Private),
                    post.copy(id = "41", visibility = Visibility.Direct),
                    StatusSamples.spoiler,
                    StatusSamples.boost,
                ),
                recent = emptyList(),
            ),
        )
        val kept = documents.filter { it.id.startsWith("bookmark:") || it.id.startsWith("search:") }
        assertEquals(listOf("bookmark:13", "bookmark:10"), kept.map { it.id })
        assertEquals("Spoilers for the finale", kept.first().name)
    }

    @Test
    fun `with the app lock on, nothing is indexed, so what it hides is not found from the launcher`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        fixture.appLock.setEnabled(true)
        search.refresh(reader)
        assertTrue(index.replaced.isEmpty())
    }

    @Test
    fun `an account that signs out while its refresh fetches is not written back`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        server.meanwhile = { runBlocking { fixture.accounts.remove(reader.id) } }
        search.refresh(reader)
        assertTrue(index.replaced.isEmpty())
    }

    @Test
    fun `the setting turned off while a refresh fetches keeps it from writing`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        server.meanwhile = { runBlocking { shown.setShown(false) } }
        search.refresh(reader)
        assertTrue(index.replaced.isEmpty())
    }

    @Test
    fun `bookmarks that may not be indexed are paged past, not counted`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        server.privateFirst = true
        search.refresh(reader)
        val kept = index.replaced.single().second.filterIsInstance<Thing>().filter { it.id.startsWith("bookmark:") }
        assertEquals(listOf("bookmark:30"), kept.map { it.id })
    }

    @Test
    fun `what could not be fetched leaves the index as it was`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        server.failing = true
        search.refresh(reader)
        assertTrue(index.replaced.isEmpty())
    }

    @Test
    fun `turned off, nothing is indexed, and an account signing out takes its entries with it`() = runBlocking {
        val reader = fixture.signIn(web.url("/"))
        shown.setShown(false)
        search.refresh(reader)
        assertTrue(index.replaced.isEmpty())
        search.forget(reader.id)
        assertEquals(listOf(DeviceSearch.namespace(reader.id)), index.removed)
    }
}
