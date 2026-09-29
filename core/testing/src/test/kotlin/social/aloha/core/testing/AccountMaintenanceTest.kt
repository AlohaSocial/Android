// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountMaintenance
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.NewAccount
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.probe.ReprobeGate
import social.aloha.core.network.probe.ServerProbe

@RunWith(RobolectricTestRunner::class)
class AccountMaintenanceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mock = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start()
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AccountsDatabase::class.java,
    ).build()
    private val accounts = AccountRepository(
        database.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
    private val maintenance = AccountMaintenance(
        accounts,
        ServerProbe(http, limiter, Dispatchers.IO),
        CapabilityDetector(http, limiter, Dispatchers.IO) { Instant.now() },
        ReprobeGate(nowMillis = Clock.systemUTC()::millis),
        Clock.systemUTC(),
    )

    @After
    fun close() {
        scope.cancel()
        database.close()
        mock.close()
    }

    private suspend fun signedInAt(apiBase: String, detectedAt: Instant) = accounts.signedIn(
        NewAccount(
            host = mock.origin.host,
            serverAccountId = "6",
            handle = "alice",
            displayName = "alice",
            avatarUrl = null,
            headerUrl = null,
            capabilities = ServerCapabilities.minimal(apiBase).copy(detectedAt = detectedAt),
            profilePending = false,
        ),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    @Test
    fun `stale capabilities are detected again`() = runBlocking {
        signedInAt(mock.apiBase.toString(), Instant.EPOCH)
        maintenance.checkAll()
        val capabilities = accounts.all().single().capabilities
        assertTrue(capabilities.isNextcloudSocial)
        assertTrue(capabilities.detectedAt.isAfter(Instant.EPOCH))
    }

    @Test
    fun `an API base that stopped answering is found again and stored, however fresh`() = runBlocking {
        // the account was added while the rewrite rules were installed; the administrator removed them
        signedInAt(mock.origin.toString(), Instant.now())
        maintenance.checkAll()
        assertEquals(mock.apiBase.toString(), accounts.all().single().apiBase)
    }

    @Test
    fun `fresh capabilities on an answering base are left alone`() = runBlocking {
        signedInAt(mock.apiBase.toString(), Instant.now())
        val before = mock.requests.size
        maintenance.checkAll()
        // only the instance was asked for
        assertEquals(before + 1, mock.requests.size)
    }
}
