// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.PollFrequency
import social.aloha.core.network.ApiError
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/** Answers the unread count with the next of [answers]: a count, or a 429 waiting that many seconds. */
private class Counts(private val answers: MutableList<Answer>) : Dispatcher() {
    sealed interface Answer {
        data class Count(val count: Int) : Answer

        data class Limited(val retryAfterSeconds: Int) : Answer
    }

    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked += request.url.encodedPath
        return when (val answer = answers.removeFirstOrNull() ?: Answer.Count(0)) {
            is Answer.Count -> json(200, """{"count":${answer.count}}""")

            is Answer.Limited -> MockResponse.Builder().code(429)
                .addHeader("Retry-After", answer.retryAfterSeconds.toString()).body("{}").build()
        }
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val answers = mutableListOf<Counts.Answer>()
    private val counts = Counts(answers)
    private val server = MockWebServer().apply {
        dispatcher = counts
        start()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = SyncSettings(
        AccountSettingsStore(InMemoryDataStore(emptyMap())),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
    )
    private val signals = TimelineSignals()
    private val unread = UnreadCounts()
    private val heard = CopyOnWriteArrayList<Int>()
    private val engine = SyncEngine(
        fixture.accounts,
        fixture.clients,
        settings,
        signals,
        unread,
        object : DeviceConditions {
            override val online = MutableStateFlow(true)
            override val metered = false
            override val powerSave = false
        },
        setOf(
            PollListener { _, count ->
                heard += count
                true
            },
        ),
        BackgroundRefresh(context, fixture.accounts, settings),
        scope,
        fixture.clock,
    )

    private companion object {
        // long enough for a collector to run, short enough not to slow the suite
        val QUIET = 200.milliseconds
    }

    @After
    fun close() {
        scope.cancel()
        fixture.close()
        server.close()
    }

    @Test
    fun `the unread count is kept, and listeners hear it only when it moved`() = runBlocking {
        answers += listOf(Counts.Answer.Count(3), Counts.Answer.Count(3), Counts.Answer.Count(5))
        val account = fixture.signIn(server.url("/"))
        repeat(3) { engine.poll(account, PollScope.Full) }
        assertEquals(mapOf(account.id to 5), unread.all.value)
        assertEquals(listOf(3, 5), heard)
        assertEquals(List(3) { "/api/v1/notifications/unread_count" }, counts.asked)
    }


    @Test
    fun `a timeline on screen is told it is due, but not by a poll that asks for notifications alone`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        val due = CopyOnWriteArrayList<String>()
        val listening = launch(start = CoroutineStart.UNDISPATCHED) { signals.timelineDue.collect { due += it } }
        engine.poll(account, PollScope.Full)
        assertNull(withTimeoutOrNull(QUIET) { while (due.isEmpty()) yield() })
        signals.noteShown(account.id, isShown = true)
        engine.poll(account, PollScope.NotificationsOnly)
        engine.refreshInBackground()
        assertNull(withTimeoutOrNull(QUIET) { while (due.isEmpty()) yield() })
        engine.poll(account, PollScope.Full)
        withTimeout(5.seconds) { while (due.isEmpty()) yield() }
        listening.cancel()
        assertEquals(listOf(account.id), due)
    }

    @Test
    fun `an active account is asked again after thirty seconds, and a manual one never on a timer`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        engine.poll(account, PollScope.Full)
        val wait = engine.waitBeforeNext(account)!!
        assertTrue("$wait", wait > 29.seconds && wait <= 30.seconds)
        settings.setPollFrequency(account.id, PollFrequency.Manual)
        assertNull(engine.waitBeforeNext(account))
    }

    @Test
    fun `a 429 with Retry-After waits it out`() = runBlocking {
        answers += Counts.Answer.Limited(retryAfterSeconds = 120)
        val account = fixture.signIn(server.url("/"))
        assertNull(engine.poll(account, PollScope.Full))
        val held = engine.waitBeforeNext(account)!!
        assertTrue("$held", held > 119.seconds && held <= 2.minutes)
    }

    @Test
    fun `a 429 without Retry-After halves the pace`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        engine.poll(account, PollScope.Full)
        engine.failed(account.id, ApiError.RateLimited(retryAfter = null))
        val slowed = engine.waitBeforeNext(account)!!
        assertTrue("$slowed", slowed > 59.seconds && slowed <= 60.seconds)
    }

    @Test
    fun `an account that needs a new sign-in is not asked at all`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        fixture.accounts.markNeedsReauth(account.id)
        assertNull(engine.poll(fixture.accounts.byId(account.id)!!, PollScope.Full))
        engine.refreshInBackground()
        assertEquals(emptyList<String>(), counts.asked)
    }
}
