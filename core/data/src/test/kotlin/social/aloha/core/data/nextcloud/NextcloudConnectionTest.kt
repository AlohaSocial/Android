// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.nextcloud

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore

/**
 * A Nextcloud under a Social server at `/index.php/apps/social/`: [status] answers `status.php` (a 404 when
 * null, as a server that is no Nextcloud does), the Login
 * Flow starts with a poll address on this server, the poll answers 404 [pendingPolls] times before the grant,
 * and every request is remembered with its `Authorization`.
 */
private class Nextcloud(var status: String? = READY, var pendingPolls: Int = 1) : Dispatcher() {
    lateinit var origin: String
    val asked = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    val bodies = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path"
        authorizations += request.headers["Authorization"]
        bodies += request.body?.utf8().orEmpty()
        return when (path) {
            "/status.php" -> status?.let { json(it) } ?: json("{}", 404)

            "/index.php/login/v2" -> json(
                """{"poll":{"token":"t0k3n","endpoint":"$origin/index.php/login/v2/poll"},""" +
                    """"login":"$origin/index.php/login/v2/flow/abc"}""",
            )

            "/index.php/login/v2/poll" ->
                if (pendingPolls-- > 0) {
                    json("[]", 404)
                } else {
                    json("""{"server":"$origin","loginName":"alice","appPassword":"app-password"}""")
                }

            "/ocs/v2.php/cloud/capabilities" ->
                json("""{"ocs":{"data":{"capabilities":{"notifications":{"push":["devices","webpush"]}}}}}""")

            "/ocs/v2.php/core/apppassword" -> json("""{"ocs":{"data":[]}}""")

            "/ocs/v2.php/apps/notifications/api/v2/webpush/vapid" -> json("""{"ocs":{"data":{"vapid":"$VAPID"}}}""")

            "/ocs/v2.php/apps/notifications/api/v2/webpush" -> json("""{"ocs":{"data":[]}}""", 201)

            "/ocs/v2.php/apps/notifications/api/v2/webpush/activate" -> json("""{"ocs":{"data":[]}}""", 202)

            else -> json("{}", 404)
        }
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()

    companion object {
        const val READY = """{"installed":true,"maintenance":false,"version":"36.0.0.0"}"""
        const val MAINTENANCE = """{"installed":true,"maintenance":true,"version":"36.0.0.0"}"""
        const val VAPID = "BA1Hxzyi1RUM1b5wjxsn7nGxAszw2u61m164i3MrAIxHF6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs"
    }
}

@RunWith(RobolectricTestRunner::class)
class NextcloudConnectionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.systemUTC()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    private val accounts =
        AccountRepository(
            database.accountDao(),
            vault,
            AppPreferences(InMemoryDataStore(emptyPreferences())),
            clock,
            scope,
        )
    private val clients =
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts)
    private val connection = NextcloudConnection(clients, accounts, database.accountDao(), vault)
    private val nextcloud = Nextcloud()
    private val server = MockWebServer().apply {
        dispatcher = nextcloud
        start()
        nextcloud.origin = url("/").toString().removeSuffix("/")
    }

    @After
    fun close() {
        scope.cancel()
        database.close()
        server.close()
    }

    private suspend fun signIn(): SignedInAccount {
        val apiBase = server.url("/index.php/apps/social/")
        return accounts.signedIn(
            NewAccount(
                apiBase.host,
                "6",
                "alice",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()),
                profilePending = false,
            ),
            AccessToken("social-token", ""),
        )
    }

    @Test
    fun `connecting waits for the approval, keeps the app password, and disconnecting gives it back`() = runBlocking {
        val account = signIn()
        val start = connection.begin(account) as ConnectStart.Opened
        assertEquals("${nextcloud.origin}/index.php/login/v2/flow/abc", start.start.loginUrl)
        assertEquals(ConnectResult.Connected, connection.await(account, start.start))
        val basic = "Basic YWxpY2U6YXBwLXBhc3N3b3Jk"
        assertEquals(basic, accounts.credentials(account.id).nextcloudBasic)
        assertTrue(accounts.byId(account.id)!!.nextcloudConnected)
        // the flow itself never carries a credential
        assertTrue(nextcloud.authorizations.all { it == null })

        assertEquals(Answer.Got(listOf("devices", "webpush")), connection.pushTypes(account))
        assertEquals(basic, nextcloud.authorizations.last())

        connection.disconnect(account)
        assertEquals("DELETE /ocs/v2.php/core/apppassword", nextcloud.asked.last())
        assertEquals(basic, nextcloud.authorizations.last())
        assertNull(accounts.credentials(account.id).nextcloudBasic)
        assertFalse(accounts.byId(account.id)!!.nextcloudConnected)
    }

    @Test
    fun `an approval that comes after signing out gives the app password straight back`() = runBlocking {
        val account = signIn()
        val start = connection.begin(account) as ConnectStart.Opened
        accounts.remove(account.id)
        assertEquals(ConnectResult.Failed, connection.await(account, start.start))
        assertEquals("DELETE /ocs/v2.php/core/apppassword", nextcloud.asked.last())
        assertEquals("Basic YWxpY2U6YXBwLXBhc3N3b3Jk", nextcloud.authorizations.last())
        assertNull(vault.get(VaultKey.AppPassword(account.id)))
    }

    @Test
    fun `a server that is no Nextcloud has nothing to connect, and one in maintenance is tried later`() = runBlocking {
        val account = signIn()
        nextcloud.status = Nextcloud.MAINTENANCE
        assertEquals(ConnectStart.Unavailable, connection.begin(account))
        nextcloud.status = null
        assertEquals(ConnectStart.NotNextcloud, connection.begin(account))
        assertFalse(nextcloud.asked.any { it.endsWith("/login/v2") })
    }

    @Test
    fun `a connected account registers for the Social app's pushes, activates, and disconnecting removes it first`() =
        runBlocking {
            val account = signIn()
            connection.await(account, (connection.begin(account) as ConnectStart.Opened).start)
            assertEquals(Nextcloud.VAPID, connection.webPushVapid(account))
            assertNull(connection.registerWebPush(account, "https://ntfy.example/up1", "pub", "secret"))
            val registered = nextcloud.bodies.last()
            listOf("endpoint=https%3A%2F%2Fntfy.example%2Fup1", "uaPublicKey=pub", "auth=secret", "appTypes=social")
                .forEach { assertTrue(registered, it in registered) }
            assertNull(connection.activateWebPush(account, "t0k"))
            assertEquals("activationToken=t0k", nextcloud.bodies.last())
            connection.disconnect(account)
            val last = nextcloud.asked.takeLast(2)
            assertEquals(
                listOf("DELETE /ocs/v2.php/apps/notifications/api/v2/webpush", "DELETE /ocs/v2.php/core/apppassword"),
                last,
            )
        }

    @Test
    fun `the answer is only asked for on the Nextcloud's own origin`() {
        val root = "https://cloud.example/nextcloud/".toHttpUrl()
        val own = "https://cloud.example/nextcloud/index.php/login/v2/poll"
        assertEquals("index.php/login/v2/poll", NextcloudConnection.pollPath(root, own))
        assertNull(NextcloudConnection.pollPath(root, "http://cloud.example/nextcloud/index.php/login/v2/poll"))
        assertNull(NextcloudConnection.pollPath(root, "https://evil.example/nextcloud/index.php/login/v2/poll"))
        assertNull(NextcloudConnection.pollPath(root, "https://cloud.example/other/index.php/login/v2/poll"))
        assertNull(NextcloudConnection.pollPath(root, "https://cloud.example/nextcloud/"))
    }

    @Test
    fun `a poll address elsewhere fails without asking it`() = runBlocking {
        val account = signIn()
        val start = (connection.begin(account) as ConnectStart.Opened).start
        val foreign = start.copy(pollEndpoint = "https://evil.example/index.php/login/v2/poll")
        assertEquals(ConnectResult.Failed, connection.await(account, foreign))
        assertFalse(nextcloud.asked.any { it.endsWith("/poll") })
    }
}
