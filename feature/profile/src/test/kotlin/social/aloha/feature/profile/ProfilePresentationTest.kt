// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.ui.graphics.Color
import java.time.Instant
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
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.OwnProfilePlace
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

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
    ) { error("no posts") }.let { checkNotNull(it) { "a featured hashtag is something featured" } }.byAccount

    @Test
    fun `a server that hid the followers says nothing for 0, and a real count still shows`() {
        assertNull(followers(0, real = false))
        assertEquals(12, followers(12, real = false))
        assertEquals(0, followers(0, real = true))
    }

    @Test
    fun `the profile header draws the whole avatar, a row of people the small one`() {
        val bob = StatusSamples.bob.copy(
            avatar = "https://cloud.example/a.jpeg",
            avatarSmall = "https://cloud.example/a.webp",
        )
        val header = ProfilePresentation.header(bob, RichTextCache(), colors, isSelf = false)
        assertEquals("https://cloud.example/a.jpeg", header.author.avatarUrl)
        assertEquals("https://cloud.example/a.webp", StatusRowMapper(RichTextCache(), colors).author(bob).avatarUrl)
    }

    @Test
    fun `follower counts are real everywhere but on Nextcloud Social before 0_26_241`() {
        assertFalse(social("0.26.240").realFollowerCounts)
        assertTrue(social("0.26.241").realFollowerCounts)
        assertTrue(ServerCapabilities.minimal("https://mastodon.example/").realFollowerCounts)
    }

    @Test
    fun `a profile has the web app's tabs, Tagged on Aloha Social and Collections where the server has them`() {
        val mastodon = ServerCapabilities.minimal("https://mastodon.example/")
        val posts = listOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Photos, ProfileTab.Videos)
        assertEquals(posts, ProfilePresentation.tabs(mastodon))
        assertEquals(posts + ProfileTab.Collections, ProfilePresentation.tabs(mastodon.copy(collections = true)))
        val aloha = social("0.26.250").copy(collections = true)
        assertEquals(posts + ProfileTab.Tagged + ProfileTab.Collections, ProfilePresentation.tabs(aloha))
        assertEquals(ProfileTab.Posts, ProfilePresentation.firstTab())
    }

    /** Bob signed in on a server with [capabilities], as the account [StatusSamples.bob] is there. */
    private fun bobReading(capabilities: ServerCapabilities) = SignedInAccount(
        id = "1",
        host = "cloud.example",
        apiBase = capabilities.apiBase,
        serverAccountId = StatusSamples.bob.id,
        handle = "bob",
        displayName = "Bob",
        avatarUrl = null,
        headerUrl = null,
        capabilities = capabilities,
        needsReauth = false,
        profilePending = false,
        addedAt = Instant.EPOCH,
        nextcloudConnected = true,
    )

    @Test
    fun `the reader's own profile adds Saved and Drafts after its tabs, and Statistics on Aloha Social`() {
        val mastodon = ServerCapabilities.minimal("https://mastodon.example/")
        val own = ProfilePresentation.allTabs(bobReading(mastodon), StatusSamples.bob)
        assertEquals(
            listOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Photos, ProfileTab.Videos) +
                listOf(ProfileTab.Saved, ProfileTab.Drafts),
            own,
        )
        val aloha = bobReading(social("0.26.250"))
        val onSocial = ProfilePresentation.allTabs(aloha, StatusSamples.bob)
        assertEquals(listOf(ProfileTab.Saved, ProfileTab.Drafts, ProfileTab.Statistics), onSocial.takeLast(3))
    }

    @Test
    fun `someone else's profile has none of the reader's own tabs`() {
        val reader = bobReading(social("0.26.250")).copy(serverAccountId = "someone else")
        val tabs = ProfilePresentation.allTabs(reader, StatusSamples.bob)
        assertTrue(tabs.none { it in OWN_TABS })
    }

    @Test
    fun `a profile opened on one of the reader's own places opens on its tab`() {
        assertEquals(ProfileTab.Saved, ProfilePresentation.firstTab(OwnProfilePlace.Favorites))
        assertEquals(ProfileTab.Saved, ProfilePresentation.firstTab(OwnProfilePlace.Bookmarks))
        assertEquals(ProfileTab.Drafts, ProfilePresentation.firstTab(OwnProfilePlace.Drafts))
        assertEquals(ProfileTab.Statistics, ProfilePresentation.firstTab(OwnProfilePlace.Statistics))
    }

    @Test
    fun `a featured hashtag opens the account's posts with it where the server narrows them`() {
        assertFalse(byAccount(social("0.26.236")))
        assertTrue(byAccount(social("0.26.237")))
        assertTrue(byAccount(ServerCapabilities.minimal("https://mastodon.example/").copy(softwareName = "mastodon")))
    }
}
