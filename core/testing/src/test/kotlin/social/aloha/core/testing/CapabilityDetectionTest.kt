// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe

class CapabilityDetectionTest {
    private var server: MockSocialServer? = null
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
    private val detectedAt = Instant.parse("2026-09-29T12:00:00Z")

    @AfterEach
    fun stop() {
        server?.close()
    }

    private suspend fun detect(configuration: MockServerConfiguration): ServerCapabilities {
        val mock = MockSocialServer(configuration).start().also { server = it }
        val address = ServerAddress.parse(mock.origin.toString(), allowCleartext = true) ?: error("address")
        val outcome = assertInstanceOf(
            ProbeResult.Found::class.java,
            ServerProbe(http, limiter, Dispatchers.IO).discover(address),
        ).outcome
        return CapabilityDetector(http, limiter, Dispatchers.IO) { detectedAt }
            .detect(outcome.apiBase, AccessToken(MockCredentials.ACCESS_TOKEN, ""), outcome.instance, outcome.nodeInfo)
    }

    @Test
    fun `Nextcloud Social's extensions are found and its transport gaps honoured`() = runTest {
        val capabilities = detect(MockServerConfiguration.NextcloudWithoutRewrite)
        assertTrue(capabilities.isNextcloudSocial)
        assertTrue(capabilities.onlyVideoFilter && capabilities.onlyNewsFilter && capabilities.onlyMediaFilter)
        assertTrue(capabilities.stories && capabilities.collections && capabilities.watchPositions)
        assertTrue(capabilities.groupedNotifications && capabilities.filtersV2 && capabilities.editHistory)
        assertTrue(capabilities.preferencesWrite)
        assertEquals(null, capabilities.streamingUrl)
        assertEquals(null, capabilities.webPushVapidKey)
        assertEquals(detectedAt, capabilities.detectedAt)
    }

    @Test
    fun `the Nextcloud theme is read from the Nextcloud root above the app path`() = runTest {
        val theme = detect(MockServerConfiguration.NextcloudWithoutRewrite).theme
        assertTrue(theme?.hasColour == true)
    }

    @Test
    fun `Mastodon gets none of the extensions and News is hidden`() = runTest {
        val capabilities = detect(MockServerConfiguration.Mastodon)
        assertFalse(capabilities.isNextcloudSocial)
        assertFalse(capabilities.onlyVideoFilter || capabilities.onlyNewsFilter)
        assertFalse(capabilities.stories || capabilities.collections || capabilities.watchPositions)
        assertFalse(capabilities.supports(social.aloha.core.model.FeedMode.News))
    }

    @Test
    fun `a server that 404s the extension routes is treated as not having them`() = runTest {
        val capabilities = detect(MockServerConfiguration.CoreOnly)
        assertFalse(capabilities.stories || capabilities.collections || capabilities.watchPositions)
        assertFalse(capabilities.groupedNotifications)
    }
}
