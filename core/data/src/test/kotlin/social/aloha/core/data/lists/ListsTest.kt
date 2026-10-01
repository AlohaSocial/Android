// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.lists

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.testing.SignedInFixture

/** List 1 holds accounts 1 and 2 on its first page, and 3 on the one its `Link` header points at. */
private class TwoPages : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        val second = url.queryParameter("max_id") != null
        val body = if (second) "[${account("3")}]" else "[${account("1")},${account("2")}]"
        return MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json")
            .apply {
                if (!second) {
                    addHeader(
                        "Link",
                        "<${url.newBuilder().addQueryParameter("max_id", "2").build()}>; rel=\"next\"",
                    )
                }
            }
            .build()
    }

    private fun account(id: String) = """{"id":"$id","username":"a$id","acct":"a$id"}"""
}

@RunWith(RobolectricTestRunner::class)
class ListsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val server = MockWebServer().apply {
        dispatcher = TwoPages()
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `everyone in a list is read, along the server's pages`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val members = (Lists(fixture.clients).members(reader, "1") as Answer.Got).value
        assertEquals(listOf("1", "2", "3"), members.map { it.id })
    }
}
