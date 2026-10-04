// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.thread

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class ReplyNudgesTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply { start() }
    private val app = AppPreferences(InMemoryDataStore(emptyPreferences()))
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val nudges = ReplyNudges(app, settings, fixture.clients, fixture.clock)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun followedBy(follows: Boolean) = server.enqueue(
        MockResponse.Builder().code(200).body("""[{"id":"1","followed_by":$follows}]""")
            .addHeader("content-type", "application/json").build(),
    )

    @Test
    fun `an old post is nudged about, until it is silenced everywhere`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val old = StatusSamples.post().copy(createdAt = fixture.clock.instant() - Duration.ofDays(91))
        assertEquals(ReplyNudge.OldPost, nudges.before(reader, old))
        nudges.silence(reader, ReplyNudge.OldPost, everywhere = true)
        followedBy(true)
        assertNull(nudges.before(reader, old))
    }

    @Test
    fun `a first reply to someone who does not follow the reader is nudged about once`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val post = StatusSamples.post().copy(createdAt = fixture.clock.instant())
        followedBy(false)
        assertEquals(ReplyNudge.Stranger, nudges.before(reader, post))
        nudges.shown(reader, ReplyNudge.Stranger, post.account.id)
        assertNull(nudges.before(reader, post))
    }

    @Test
    fun `someone who follows the reader needs no nudge`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        followedBy(true)
        assertNull(nudges.before(reader, StatusSamples.post().copy(createdAt = fixture.clock.instant())))
    }
}
