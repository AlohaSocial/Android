// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.TagKey
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline

/** A server whose resolving search finds one remote post, under its own id 555. */
private class Searching : Dispatcher() {
    val searches: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (!request.url.encodedPath.endsWith(
                "/api/v2/search",
            )
        ) {
            return MockResponse.Builder().code(404).body("{}").build()
        }
        searches += request.url.queryParameter("q").orEmpty()
        val status =
            JsonObject(NumberedTimeline.homeTemplate() + ("id" to JsonPrimitive("555")) + ("reblog" to JsonNull))
        val body = JsonObject(
            mapOf(
                "accounts" to JsonArray(emptyList()),
                "statuses" to JsonArray(listOf(status)),
                "hashtags" to JsonArray(emptyList()),
            ),
        )
        return MockResponse.Builder().code(
            200,
        ).body(body.toString()).addHeader("content-type", "application/json").build()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LinkOpenerTest {
    private val clock = Clock.systemUTC()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val cache = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
    private val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        clock,
        scope,
    )
    private val searching = Searching()
    private val server = MockWebServer().apply {
        dispatcher = searching
        start()
    }
    private val opener = LinkOpener(
        RemoteLookup(
            ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts),
            StatusRepository(cache.statusDao(), clock),
        ),
    )

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    private suspend fun reader() = server.url("/").let { base ->
        accounts.signedIn(
            NewAccount(
                base.host,
                "6",
                "alice",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(base.toString()),
                profilePending = false,
            ),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
    }

    @Test
    fun `a post on the reader's own server opens by its id, without asking anyone`() = runBlocking {
        val reader = reader()
        val host = server.url("/").host
        assertEquals(ThreadKey(reader.id, "42"), opener.destination(reader, "https://$host/@bob/42"))
        assertTrue(searching.searches.isEmpty())
    }

    @Test
    fun `a post elsewhere is looked up through the reader's server and opens under its id there`() = runBlocking {
        val reader = reader()
        assertEquals(ThreadKey(reader.id, "555"), opener.destination(reader, "https://remote.example/@carol/9"))
        assertEquals(listOf("https://remote.example/@carol/9"), searching.searches)
    }

    @Test
    fun `profiles and hashtags open directly, and an ordinary link is never sent to the server`() = runBlocking {
        val reader = reader()
        assertEquals(
            AccountKey(reader.id, acct = "carol@remote.example"),
            opener.destination(reader, "https://remote.example/@carol"),
        )
        assertEquals(TagKey(reader.id, "surf"), opener.destination(reader, "https://remote.example/tags/surf"))
        assertNull(opener.destination(reader, "https://example.com/articles/aloha"))
        assertTrue(searching.searches.isEmpty())
    }

    @Test
    fun `in a post's text, only a post-shaped web link opens here`() = runBlocking {
        val reader = reader()
        assertNull(opener.destination(reader, "https://remote.example/@carol", fromPost = true))
        assertNull(opener.destination(reader, "https://remote.example/tags/surf", fromPost = true))
        val host = server.url("/").host
        assertEquals(ThreadKey(reader.id, "42"), opener.destination(reader, "https://$host/@bob/42", fromPost = true))
    }

    @Test
    fun `an address handed over with Open in Aloha is asked after whatever its shape`() = runBlocking {
        val reader = reader()
        val address = "https://forum.example/t/aloha/12"
        assertEquals(ThreadKey(reader.id, "555"), opener.destination(reader, address, handedOver = true))
        assertEquals(listOf(address), searching.searches)
        assertNull(opener.destination(reader, "javascript:alert(1)", handedOver = true))
        assertEquals(1, searching.searches.size)
    }
}
