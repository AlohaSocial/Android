// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem

private fun Endpoint.form(): List<QueryItem> = (body as? Body.Form)?.fields.orEmpty()

private fun Endpoint.formValue(name: String): String? = form().firstOrNull { it.name == name }?.value

private fun Endpoint.queryValue(name: String): String? = query.firstOrNull { it.name == name }?.value

class ExtensionEndpointsTest {
    @Test
    fun `Pixelfed names a story with sid, never in the path`() {
        val viewers = StoryEndpoints.viewers("8").endpoint
        assertEquals("api/v1.2/stories/viewers", viewers.path)
        assertEquals("8", viewers.queryValue("sid"))
        assertEquals("api/v1.2/stories/reactions", StoryEndpoints.reactions("8").endpoint.path)
        assertEquals("8", StoryEndpoints.react("8", "🎉").endpoint.formValue("sid"))
        assertEquals("api/v1.2/stories/comment", StoryEndpoints.comment("8", "hi").endpoint.path)
        val expire = StoryEndpoints.selfExpire("8").endpoint
        assertEquals("api/v1.1/stories/self-expire/8", expire.path)
        assertEquals(HttpMethod.POST, expire.method)
    }

    @Test
    fun `a story reaction is clipped to twenty characters, never through an emoji`() {
        assertEquals("🎉".repeat(20), StoryEndpoints.react("1", "🎉".repeat(40)).endpoint.formValue("reaction"))
    }

    @Test
    fun `a story's duration is clamped to what the server keeps`() {
        assertEquals("30", StoryEndpoints.post("m", null, durationSeconds = 90).endpoint.formValue("duration"))
        assertEquals("3", StoryEndpoints.post("m", null, durationSeconds = 1).endpoint.formValue("duration"))
    }

    @Test
    fun `a collection item is removed by path, not by form body`() {
        val endpoint = CollectionEndpoints.removeItem("4", statusId = "91").endpoint
        assertEquals(HttpMethod.DELETE, endpoint.method)
        assertEquals("api/v1/collections/4/items/91", endpoint.path)
        assertEquals(Body.None, endpoint.body)
    }

    @Test
    fun `deleting the Social account goes out on the Nextcloud credential`() {
        val endpoint = SocialAccountEndpoints.delete("@ada@cloud.example").endpoint
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("api/v1/account/delete", endpoint.path)
        assertEquals(Authentication.NextcloudSession, endpoint.authentication)
        assertEquals("@ada@cloud.example", endpoint.formValue("confirm"))
    }

    @Test
    fun `the routes a bearer token cannot reach go out on the Nextcloud credential`() {
        listOf(
            MemoryEndpoints.onThisDay().endpoint,
            MemoryEndpoints.recap().endpoint,
            ReviewEndpoints.held().endpoint,
            StatisticsEndpoints.overview(30).endpoint,
            ChannelEndpoints.all().endpoint,
            AuthorizedAppEndpoints.all().endpoint,
        ).forEach { assertEquals(Authentication.NextcloudSession, it.authentication, it.path) }
        assertEquals(Authentication.Bearer, InterestEndpoints.state().endpoint.authentication)
    }

    @Test
    fun `an empty language list still goes out, and untouched settings do not`() {
        val endpoint = InterestEndpoints.settings(languages = emptyList()).endpoint
        assertEquals(listOf(QueryItem("languages", "")), endpoint.form())
        assertEquals(
            listOf("languages[]", "languages[]"),
            InterestEndpoints.settings(languages = listOf("en", "de")).endpoint.form().map {
                it.name
            },
        )
        assertEquals("0", InterestEndpoints.settings(learning = false).endpoint.formValue("learning"))
    }

    @Test
    fun `the focal point is written with a dot whatever the device's locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("0.50,-1.00", MediaEndpoints.updateFocus("1", 0.5, -3.0).endpoint.formValue("focus"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `an alias is removed through the query, never a DELETE body`() {
        val endpoint = MigrationEndpoints.removeAlias("a@b.example").endpoint
        assertEquals("a@b.example", endpoint.queryValue("alias"))
        assertEquals(Body.None, endpoint.body)
    }

    @Test
    fun `continue watching and GIF pages are capped and never ask for zero`() {
        assertEquals("40", VideoEndpoints.continueWatching(limit = 100).endpoint.queryValue("limit"))
        assertEquals("1", GifEndpoints.library(null, limit = 0).endpoint.queryValue("limit"))
        assertEquals("0", GifEndpoints.library("", offset = -5).endpoint.queryValue("offset"))
        assertTrue(GifEndpoints.library("").endpoint.query.none { it.name == "q" })
    }

    @Test
    fun `list editing sends only a set replies policy`() {
        val endpoint = ListEndpoints.update("1", "Friends", repliesPolicy = null, exclusive = true).endpoint
        assertEquals(HttpMethod.PUT, endpoint.method)
        assertEquals("true", endpoint.formValue("exclusive"))
        assertTrue(endpoint.form().none { it.name == "replies_policy" })
    }

    @Test
    fun `a credentials update sends only the fields that are set`() {
        val update = CredentialsUpdate(displayName = "Ada", locked = true)
        assertEquals(listOf("display_name", "locked"), update.parts().map { it.name })
        assertTrue(CredentialsUpdate().isEmpty)
    }

    @Test
    fun `discover posts name the media kind the server knows`() {
        assertEquals("image", DiscoveryEndpoints.discoverPosts(DiscoverMedia.Images).endpoint.queryValue("media"))
    }

    @Test
    fun `a reaction is sent as the emoji Nextcloud Social reads`() {
        assertEquals("🎉", StatusEndpoints.react("1", "🎉").endpoint.formValue("emoji"))
        assertEquals("🎉", StatusEndpoints.unreact("1", "🎉").endpoint.formValue("emoji"))
    }

    @Test
    fun `a profile save without a picture goes as a form, one with a picture as multipart`() {
        val text = CredentialEndpoints.update(CredentialsUpdate(displayName = "Alice")).endpoint.body
        assertTrue(text is Body.Form)
        val picture = ProfilePicture(java.io.File("a.png"), "a.png", "image/png")
        assertTrue(CredentialEndpoints.update(CredentialsUpdate(avatar = picture)).endpoint.body is Body.Multipart)
    }
}
