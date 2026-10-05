// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.profile.ProfileFeatured
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.data.search.Searches
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.navigation.AccountKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline
import social.aloha.core.ui.RichTextColors

/**
 * One remote account, Bob, as a Nextcloud Social server serves him: his statuses (numbered), a
 * relationship in which he follows the reader, twelve weeks of highlights, and no `lookup` for his
 * handle, so only a resolving search finds him.
 */
private class Bob : Dispatcher() {
    private val statuses = NumberedTimeline().apply { newest = 30 }
    private val account = JsonObject(
        NumberedTimeline.homeTemplate().getValue("account").jsonObject +
            mapOf(
                "id" to JsonPrimitive("7"),
                "acct" to JsonPrimitive("bob@remote.example"),
                "display_name" to JsonPrimitive("Bob"),
                "note" to JsonPrimitive("<p>Surfs at dawn</p>"),
                "followers_count" to JsonPrimitive(12),
            ),
    )

    @Volatile var following = false

    /** What changed the relationship: method, path and form, in order. */
    val changes: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
    val statusQueries: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/accounts/7/statuses") -> statuses.dispatch(request).also {
                statusQueries +=
                    request.url.query.orEmpty()
            }

            path.endsWith("/accounts/7/follow") -> json(relationship(following = true).also { following = true })

            path.endsWith("/accounts/7/mute") || path.endsWith("/domain_blocks") ||
                ("/lists/" in path && path.endsWith("/accounts"))
            -> {
                changes += "${request.method} $path ${request.body?.utf8().orEmpty()}"
                json(if (path.endsWith("/mute")) relationship(following) else "{}")
            }

            path.endsWith("/accounts/7/lists") -> json("""[{"id":"l1","title":"Surf"}]""")

            path.endsWith("/lists") -> json("""[{"id":"l1","title":"Surf"},{"id":"l2","title":"Friends"}]""")

            path.endsWith("/accounts/relationships") -> json("[${relationship(following)}]")

            path.endsWith(
                "/accounts/7/highlights",
            ) -> json("""{"available":true,"weeks":[0,1,2,1,0,3,2,1,4,5,6,7],"hashtags":[]}""")

            path.endsWith("/accounts/search") -> json(JsonArray(listOf(account)).toString())

            path.endsWith("/accounts/7") -> json(account.toString())

            path.endsWith("/filters") -> json("[]")

            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    private fun relationship(following: Boolean) =
        """{"id":"7","following":$following,"followed_by":true,"requested":false,"blocking":false,"muting":false}"""

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
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
    private val statuses = StatusRepository(cache.statusDao(), clock)
    private val bob = Bob()
    private val server = MockWebServer().apply {
        dispatcher = bob
        start()
    }

    private val store = ViewModelStore()

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        scope.cancel()
        accountsDb.close()
        cache.close()
        server.close()
    }

    private suspend fun open(id: String? = null, acct: String? = null): ProfileViewModel {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val apiBase = server.url("/")
        val capabilities = ServerCapabilities.minimal(apiBase.toString()).copy(softwareName = "nextcloud-social")
        val reader = accounts.signedIn(
            NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        return ProfileViewModel(
            AccountKey(reader.id, id, acct),
            accounts,
            ProfileRepository(clients, statuses, FollowedAuthors(clients)),
            TimelineRepository(cache.timelineDao(), statuses, clients, accounts, clock, Dispatchers.IO),
            FilterRepository(cache.filterDao(), clients),
            StatusInteractions(statuses, clients),
            RichTextCache(),
            clock,
            ProfileFeatured(clients),
            Searches(clients, statuses, AccountSettingsStore(InMemoryDataStore(emptyMap()))),
        ).apply {
            onColors(RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
            store.put("profile-${'$'}id-${'$'}acct", this)
        }
    }

    private suspend fun ProfileViewModel.await(predicate: (ProfileUiState) -> Boolean): ProfileUiState =
        withTimeout(10.seconds) { uiState.first(predicate) }

    @Test
    fun `a profile opens with its header, how the reader relates to it, and its posts`() = runBlocking {
        val state = open(id = "7").await { it.header != null && it.relation != null && it.items.isNotEmpty() }
        val header = state.header!!
        assertEquals("Bob", header.author.plainName)
        assertEquals("Surfs at dawn", header.note.text.trim())
        assertEquals(12, header.followers)
        assertEquals(Relation(followedBy = true), state.relation)
        assertEquals("30", (state.items.first() as ProfileItem.Post).row.statusId)
    }

    @Test
    fun `following shows once the server agrees`() = runBlocking {
        val viewModel = open(id = "7")
        viewModel.await { it.relation != null }
        viewModel.onChange(RelationshipChange.Follow())
        assertTrue(viewModel.await { it.relation?.following == true && !it.changing }.relation!!.following)
    }

    @Test
    fun `a handle the server has not looked up is found by a resolving search`() = runBlocking {
        val state = open(acct = "bob@remote.example").await { it.header != null || it.gone }
        assertEquals("@bob@remote.example", state.header?.author?.handle)
    }

    @Test
    fun `Nextcloud Social's twelve weeks of posting show with their reading`() = runBlocking {
        val state = open(id = "7").await { it.highlights != null }
        assertEquals(32, state.highlights!!.total)
    }

    @Test
    fun `each posts tab reads its own timeline`() = runBlocking {
        val viewModel = open(id = "7")
        viewModel.await { it.items.isNotEmpty() }
        viewModel.onTab(ProfileTab.Replies)
        val state = viewModel.await { it.tab == ProfileTab.Replies && it.items.isNotEmpty() }
        assertEquals("30", (state.items.first() as ProfileItem.Post).row.statusId)
        // posts leave replies out; the replies tab asks without that narrowing
        assertTrue(bob.statusQueries.any { "exclude_replies" in it })
        assertTrue(bob.statusQueries.any { "exclude_replies" !in it })
    }

    @Test
    fun `a profile the server cannot find says so rather than loading for ever`() = runBlocking {
        val state = open(acct = "nobody@nowhere.example").await { !it.loading }
        assertTrue(state.gone)
    }

    @Test
    fun `a mute lasts as long as asked, and their notifications go quiet with it`() = runBlocking {
        val viewModel = open(id = "7")
        viewModel.await { it.relation != null }
        viewModel.onChange(RelationshipChange.Mute(notifications = true, durationSeconds = 86_400))
        viewModel.await { !it.changing }
        withTimeout(10.seconds) { while (bob.changes.isEmpty()) delay(10) }
        assertEquals("POST /api/v1/accounts/7/mute notifications=true&duration=86400", bob.changes.single())
    }

    @Test
    fun `the lists say where the account is, and a tick puts it on another`() = runBlocking {
        val viewModel = open(id = "7")
        viewModel.await { it.relation != null }
        viewModel.onLists()
        val lists = viewModel.await { it.lists != null }.lists!!
        assertEquals(listOf("Surf" to true, "Friends" to false), lists.map { it.list.title to it.member })
        viewModel.onListed("l2", add = true)
        assertTrue(viewModel.await { state -> state.lists?.all { it.member } == true }.lists!!.all { it.member })
        withTimeout(10.seconds) { while (bob.changes.isEmpty()) delay(10) }
        assertEquals("POST /api/v1/lists/l2/accounts account_ids%5B%5D=7", bob.changes.single())
    }

    @Test
    fun `blocking their server blocks the domain of their handle`() = runBlocking {
        val viewModel = open(id = "7")
        // the domain comes from their handle, which arrives with the header, not always before the relation
        viewModel.await { it.relation != null && it.header != null }
        viewModel.onBlockDomain(true)
        withTimeout(10.seconds) { while (bob.changes.isEmpty()) delay(10) }
        assertEquals("POST /api/v1/domain_blocks domain=remote.example", bob.changes.single())
    }
}
