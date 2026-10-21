// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors

@RunWith(RobolectricTestRunner::class)
class ProfilePresentationTest {
    private val colors = RichTextColors(Color.Blue, Color.Gray, Color.LightGray)

    private fun followers(count: Int, real: Boolean) = ProfilePresentation.header(
        StatusSamples.bob.copy(followersCount = count),
        RichTextCache(),
        colors,
        isSelf = false,
        realFollowers = real,
    ).followers

    private fun social(version: String) = ServerCapabilities.minimal(
        "https://cloud.example/",
    ).copy(softwareName = "nextcloud-social", softwareVersion = version)

    private fun byAccount(capabilities: ServerCapabilities) = ProfilePresentation.featured(
        emptyList(),
        listOf(FeaturedTag(id = "1", name = "surf", statusesCount = 3)),
        capabilities,
    ) { error("no posts") }!!.byAccount

    @Test
    fun `a server that hid the followers says nothing for 0, and a real count still shows`() {
        assertNull(followers(0, real = false))
        assertEquals(12, followers(12, real = false))
        assertEquals(0, followers(0, real = true))
    }

    @Test
    fun `follower counts are real everywhere but on Nextcloud Social before 0_26_241`() {
        assertFalse(social("0.26.240").realFollowerCounts)
        assertTrue(social("0.26.241").realFollowerCounts)
        assertTrue(ServerCapabilities.minimal("https://mastodon.example/").realFollowerCounts)
    }

    @Test
    fun `a featured hashtag opens the account's posts with it where the server narrows them`() {
        assertFalse(byAccount(social("0.26.236")))
        assertTrue(byAccount(social("0.26.237")))
        assertTrue(byAccount(ServerCapabilities.minimal("https://mastodon.example/").copy(softwareName = "mastodon")))
    }
}
