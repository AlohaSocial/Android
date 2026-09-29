// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.probe.CandidateKind
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe

class ProbeAgainstServerShapesTest {
    private var server: MockSocialServer? = null
    private val probe = ServerProbe(OkHttpClient(), RateLimiter(nowMillis = Clock.systemUTC()::millis), Dispatchers.IO)

    @AfterEach
    fun stop() {
        server?.close()
    }

    private suspend fun discover(configuration: MockServerConfiguration): ProbeResult {
        val mock = MockSocialServer(configuration).start().also { server = it }
        val address = ServerAddress.parse(mock.origin.toString(), allowCleartext = true) ?: error("address")
        return probe.discover(address)
    }

    private fun ProbeResult.found() = assertInstanceOf(ProbeResult.Found::class.java, this).outcome

    @Test
    fun `a Nextcloud with the rewrite rules is found through its RFC 8414 issuer, on the app path`() = runTest {
        val outcome = discover(MockServerConfiguration.NextcloudWithRewrite).found()
        assertEquals(CandidateKind.AuthorizationServerIssuer, outcome.winner.kind)
        assertEquals("/index.php/apps/social/", outcome.apiBase.encodedPath)
        assertEquals("nextcloud.local", outcome.instance.domain)
        assertTrue(outcome.nodeInfo?.isNextcloudSocial == true)
    }

    @Test
    fun `a Nextcloud WITHOUT the rewrite rules still resolves, to the app path`() = runTest {
        val outcome = discover(MockServerConfiguration.NextcloudWithoutRewrite).found()
        assertEquals(CandidateKind.AppPath, outcome.winner.kind)
        assertEquals("/index.php/apps/social/", outcome.apiBase.encodedPath)
        assertTrue(outcome.nodeInfo?.isNextcloudSocial == true)
    }

    @Test
    fun `stock Mastodon resolves to the root and is not taken for Nextcloud Social`() = runTest {
        val outcome = discover(MockServerConfiguration.Mastodon).found()
        assertEquals("/", outcome.apiBase.encodedPath)
        assertEquals("mastodon", outcome.nodeInfo?.softwareName)
    }

    @Test
    fun `a core-only server resolves like one without the rewrite rules`() = runTest {
        assertEquals("/index.php/apps/social/", discover(MockServerConfiguration.CoreOnly).found().apiBase.encodedPath)
    }

    @Test
    fun `a rate-limited server is still found once its Retry-After has passed`() = runTest {
        assertEquals("/", discover(MockServerConfiguration.RateLimited).found().apiBase.encodedPath.take(1))
    }

    @Test
    fun `a server that answers nothing useful reports every candidate it tried`() = runTest {
        val nothing = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(404).build()
            }
            start()
        }
        nothing.use {
            val address =
                ServerAddress.parse(it.url("/typed/path").toString(), allowCleartext = true) ?: error("address")
            val result = assertInstanceOf(ProbeResult.NothingAnswered::class.java, probe.discover(address))
            assertEquals(listOf(0, 1, 2, 3, 4, 5), result.attempted.map { candidate -> candidate.rank })
        }
    }

    @Test
    fun `a server nobody answers for is unreachable, not missing its API`() = runTest {
        // port 1 is closed: every candidate fails to connect, so there was no answer to judge
        val address = ServerAddress.parse("http://127.0.0.1:1/", allowCleartext = true) ?: error("address")
        assertInstanceOf(ProbeResult.Unreachable::class.java, probe.discover(address))
    }

    @Test
    fun `a hand-entered API address is probed alone`() = runTest {
        val mock = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start().also { server = it }
        val outcome = probe.discover(mock.origin.resolve("/index.php/apps/social") ?: error("url")).found()
        assertEquals(CandidateKind.Manual, outcome.winner.kind)
        assertEquals(listOf(1), outcome.attempted.map { it.rank })
    }
}
