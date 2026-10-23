// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.reading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.MockServerConfiguration
import social.aloha.core.testing.MockSocialServer
import social.aloha.core.testing.SignedInFixture

private const val ROUTE = "/api/v1/social/feed_preferences"

@RunWith(RobolectricTestRunner::class)
class ReplyHidingTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockSocialServer(MockServerConfiguration.NextcloudWithRewrite).start()
    private val hiding = ReplyHiding(fixture.clients, fixture.timelines)
    private val home = TimelineKey.home()
    private val tag = TimelineKey.home(TimelineSource.Hashtag("aloha"))
    private val list = TimelineKey.home(TimelineSource.List("1"))

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun load(reader: SignedInAccount, vararg keys: TimelineKey) = keys.forEach {
        fixture.timelines.refresh(reader, it, RefreshPlan.of(hasFetchedBefore = false, newestRowId = null))
    }

    private suspend fun rows(reader: SignedInAccount, key: TimelineKey) = fixture.timelines.observe(reader, key).first()

    @Test
    fun `a server without the switch answers 404, and the switch is unknown`() = runBlocking {
        val reader = fixture.signIn(server.apiBase)
        assertNull(hiding.hidden(reader))
        assertNull(hiding.hide(reader, true))
    }

    @Test
    fun `the switch reads as the server holds it`() = runBlocking {
        val reader = fixture.signIn(server.apiBase)
        server.pin("GET", ROUTE, 200, """{"hide_replies":true}""")
        assertEquals(true, hiding.hidden(reader))
        server.pin("GET", ROUTE, 200, "{}")
        assertNull(hiding.hidden(reader))
    }

    @Test
    fun `a change taken goes out as a PATCH and drops the feeds it narrows, and no other`() = runBlocking {
        val reader = fixture.signIn(server.apiBase)
        server.pin("GET", "/api/v1/timelines/list/1", "api/timeline-home.json")
        load(reader, home, tag, list)
        listOf(home, tag, list).forEach { assertTrue("$it is empty", rows(reader, it).isNotEmpty()) }
        server.pin("PATCH", ROUTE, 200, """{"hide_replies":true}""")
        assertEquals(true, hiding.hide(reader, true))
        val sent = server.requests.last()
        assertEquals("PATCH", sent.method)
        assertTrue(sent.url.encodedPath.endsWith(ROUTE))
        assertEquals("hide_replies=true", sent.body?.utf8())
        assertTrue(rows(reader, home).isEmpty())
        assertTrue(rows(reader, tag).isEmpty())
        assertNull(fixture.timelines.lastFetched(reader, home))
        assertFalse(rows(reader, list).isEmpty())
        assertNotNull(fixture.timelines.lastFetched(reader, list))
    }

    @Test
    fun `a change refused keeps the feeds`() = runBlocking {
        val reader = fixture.signIn(server.apiBase)
        load(reader, home)
        server.pin("PATCH", ROUTE, 422, """{"error":"hide_replies must be a boolean"}""")
        assertNull(hiding.hide(reader, false))
        assertFalse(rows(reader, home).isEmpty())
    }
}
