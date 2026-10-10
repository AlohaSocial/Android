// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.nextcloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.network.ApiError
import social.aloha.core.testing.SignedInFixture

/** Answers as Social 0.26.181 gave them on the dev instance, trimmed. */
private const val STATISTICS = """{"account":{"acct":"alice","display_name":"alice","followers":2,"following":2},
"posts":{"total":19,"originals":18,"replies":1,"boosts":0,"with_media":6,"sensitive":2},
"engagement":{"likes":2,"boosts":1,"replies":1},"by_month":{"2026-09":19,"2026-10":0},
"hashtags":[{"name":"beach","count":3}],"window":{"days":90,"counted":19,"capped":false}}"""

private const val CSV = "metric,value\nposts,19\n"

@RunWith(RobolectricTestRunner::class)
class NextcloudExtrasTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val asked = CopyOnWriteArrayList<RecordedRequest>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/statistics/export") -> MockResponse.Builder().body(CSV).build()

                    path.endsWith("/statistics") -> MockResponse.Builder().body(STATISTICS)
                        .addHeader("content-type", "application/json").build()

                    else -> MockResponse.Builder().code(404).body("{}").build()
                }
            }
        }
        start()
    }
    private val extras = NextcloudExtras(fixture.clients)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `statistics and their export go with the app password`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        fixture.connectNextcloud(reader.id, "Basic YWxpY2U6YXBw")
        val statistics = (extras.statistics(reader, days = 90) as Answer.Got).value
        assertEquals(19.0, statistics.posts["total"])
        assertEquals("beach", statistics.hashtags.single().name)
        val csv = ByteArrayOutputStream()
        assertTrue(extras.exportStatistics(reader, days = 90, into = csv) is Answer.Got)
        assertEquals(CSV, csv.toString(Charsets.UTF_8))
        assertEquals(listOf("90", "90"), asked.map { it.url.queryParameter("days") })
        assertTrue(asked.all { it.headers["Authorization"] == "Basic YWxpY2U6YXBw" })
    }

    @Test
    fun `without the Nextcloud connection nothing is sent`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertTrue((extras.statistics(reader, days = 90) as Answer.Missed).error is ApiError.Unauthorised)
        val export = extras.exportAccount(reader, ByteArrayOutputStream())
        assertTrue((export as Answer.Missed).error is ApiError.Unauthorised)
        assertEquals(0, asked.size)
    }
}
