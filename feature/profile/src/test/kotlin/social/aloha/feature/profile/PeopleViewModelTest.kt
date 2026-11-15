// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.navigation.PeopleKey
import social.aloha.core.navigation.PeopleKind
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors

/** Bob, account 7, who hides his followers and following; his followers come back as [followers]. */
private class HidingBob : Dispatcher() {
    private val account = JsonObject(
        NumberedTimeline.homeTemplate().getValue("account").jsonObject +
            mapOf(
                "id" to JsonPrimitive("7"),
                "acct" to JsonPrimitive("bob@remote.example"),
                "hide_collections" to JsonPrimitive(true),
            ),
    )

    @Volatile var followers: JsonArray = JsonArray(emptyList())

    /** Whether reading his account on its own fails. */
    @Volatile var accountFails = false

    val paths: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        paths += path
        return when {
            path.endsWith("/accounts/7/followers") -> json(followers.toString())
            path.endsWith("/accounts/7") && accountFails -> MockResponse.Builder().code(500).body("{}").build()
            path.endsWith("/accounts/7") -> json(account.toString())
            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    fun follower(): JsonObject = JsonObject(account + ("id" to JsonPrimitive("8")) - "hide_collections")

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class PeopleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

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
    private val clients =
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts)
    private val bob = HidingBob()
    private val server = MockWebServer().apply {
        dispatcher = bob
        start()
    }

    @After
    fun close() {
        main.clear()
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    /** Bob's followers as read by the account [readerAccountId], once everything it started is done. */
    private suspend fun followers(readerAccountId: String = "6"): PeopleUiState {
        val apiBase = server.url("/")
        val reader = accounts.signedIn(
            NewAccount(
                apiBase.host,
                readerAccountId,
                "alice",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()).copy(softwareName = "nextcloud-social"),
                profilePending = false,
            ),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        val profiles = ProfileRepository(clients, StatusRepository(cache.statusDao(), clock), FollowedAuthors(clients))
        val viewModel = main.keep(
            PeopleViewModel(PeopleKey(reader.id, "7", PeopleKind.Followers), accounts, profiles, RichTextCache()),
        )
        viewModel.onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
        val job = viewModel.viewModelScope.coroutineContext.job
        withTimeout(10.seconds) { while (job.children.any { it.isActive }) delay(10) }
        return viewModel.uiState.value.also { assertFalse(it.loading) }
    }

    @Test
    fun `an empty list of an account that hides it is marked hidden`() = runBlocking {
        val state = followers()
        assertTrue(state.people.isEmpty())
        assertTrue(state.hidden)
    }

    @Test
    fun `an empty list is not marked hidden when the account cannot be read`() = runBlocking {
        bob.accountFails = true
        assertFalse(followers().hidden)
        assertTrue(bob.paths.any { it.endsWith("/accounts/7") })
    }

    @Test
    fun `the reader's own empty list is never marked hidden`() = runBlocking {
        val state = followers(readerAccountId = "7")
        assertFalse(state.hidden)
        assertTrue(bob.paths.any { it.endsWith("/accounts/7") })
    }

    @Test
    fun `a list with people in it never asks for the account`() = runBlocking {
        bob.followers = JsonArray(listOf(bob.follower()))
        val state = followers()
        assertEquals(1, state.people.size)
        assertFalse(state.hidden)
        assertEquals(listOf("/api/v1/accounts/7/followers"), bob.paths)
    }
}
