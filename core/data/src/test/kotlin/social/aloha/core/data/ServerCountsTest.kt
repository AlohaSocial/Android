// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class ServerCountsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val social = MockWebServer().apply { start() }
    private val mastodon = MockWebServer().apply { start() }
    private val reading = ReadingPreferences(InMemoryDataStore(emptyPreferences()))
    private val stored = FailingOnce(InMemoryDataStore(emptyMap<String, AccountSettings>()))
    private val settings = AccountSettingsStore(stored)
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val counts = ServerCounts(fixture.accounts, fixture.clients, reading, settings, scope)

    @After
    fun close() {
        scope.cancel()
        fixture.close()
        social.close()
        mastodon.close()
    }

    private fun answer(code: Int) = social.enqueue(MockResponse.Builder().code(code).body("{}").build())

    private suspend fun signIn() {
        val base = social.url("/")
        fixture.signIn(base, ServerCapabilities.minimal(base.toString()).copy(softwareName = "nextcloud-social"))
        fixture.signIn(mastodon.url("/"), remoteId = "7")
    }

    @Test
    fun `a reader who never touched the switch has it told to a Social server once it is signed in`() = runBlocking {
        answer(200)
        counts.start()
        signIn()
        val request = social.takeRequest(10, TimeUnit.SECONDS)
        assertEquals("PATCH", request?.method)
        assertEquals("/api/v1/social/counts", request?.url?.encodedPath)
        assertEquals("hide=0", request?.body?.utf8())
        assertEquals(0, mastodon.requestCount)
    }

    @Test
    fun `a value the server took is not sent again, a failed one is, and a change is`() = runBlocking {
        signIn()
        answer(500)
        counts.tell()
        answer(200)
        counts.tell()
        counts.tell()
        assertEquals(2, social.requestCount)
        reading.setStyle(reading.style.first().copy(showCounts = false))
        answer(200)
        counts.tell()
        assertEquals(3, social.requestCount)
        repeat(2) { social.takeRequest() }
        assertEquals("hide=1", social.takeRequest().body?.utf8())
    }

    @Test
    fun `storage that fails once does not stop the switch being told`() = runBlocking<Unit> {
        stored.failing = true
        answer(200)
        answer(200)
        counts.start()
        signIn()
        assertEquals("PATCH", social.takeRequest(10, TimeUnit.SECONDS)?.method)
        assertEquals("PATCH", social.takeRequest(10, TimeUnit.SECONDS)?.method)
        // the server has the request before the answer is stored
        val id = fixture.accounts.all().first().id
        withTimeout(10.seconds) { settings.settings(id).first { it.countsHidden == false } }
    }
}

/** Stores in memory, but throws an [IOException] on the first write once [failing] is set. */
private class FailingOnce<T>(private val inner: InMemoryDataStore<T>) : DataStore<T> by inner {
    @Volatile var failing = false

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        if (failing) {
            failing = false
            throw IOException("disk full")
        }
        return inner.updateData(transform)
    }
}
