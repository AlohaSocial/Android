// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.TimelineFilters
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem

private fun Endpoint.form(): List<QueryItem> = (body as? Body.Form)?.fields.orEmpty()

private fun Endpoint.formValue(name: String): String? = form().firstOrNull { it.name == name }?.value

private fun Endpoint.queryValue(name: String): String? = query.firstOrNull { it.name == name }?.value

private fun Endpoint.queryNames(): List<String> = query.map { it.name }

class EndpointsTest {
    private val keywords = listOf(
        KeywordDraft("spoilers", id = "9", wholeWord = true),
        KeywordDraft("ending"),
        KeywordDraft("gone", id = "4", destroy = true),
    )

    private fun draft(
        context: List<FilterContext> = listOf(FilterContext.Home),
        expires: Long? = null,
        keywords: List<KeywordDraft> = emptyList(),
    ) = FilterDraft("Spoilers", context, FilterAction.Hide, expires, keywords)

    @Test
    fun `filter keywords go out in Rails' nested-attributes shape`() {
        val endpoint = FilterEndpoints.create(draft(keywords = keywords)).endpoint
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("api/v2/filters", endpoint.path)
        assertEquals("spoilers", endpoint.formValue("keywords_attributes[0][keyword]"))
        assertEquals("1", endpoint.formValue("keywords_attributes[0][whole_word]"))
        assertEquals("9", endpoint.formValue("keywords_attributes[0][id]"))
        assertEquals("ending", endpoint.formValue("keywords_attributes[1][keyword]"))
        assertNull(endpoint.formValue("keywords_attributes[1][id]"))
        assertEquals("0", endpoint.formValue("keywords_attributes[1][whole_word]"))
    }

    @Test
    fun `a removed filter keyword is sent with _destroy rather than left out`() {
        val endpoint = FilterEndpoints.update("3", draft(keywords = keywords)).endpoint
        assertEquals(HttpMethod.PUT, endpoint.method)
        assertEquals("api/v2/filters/3", endpoint.path)
        assertEquals("1", endpoint.formValue("keywords_attributes[2][_destroy]"))
        assertEquals("4", endpoint.formValue("keywords_attributes[2][id]"))
    }

    @Test
    fun `clearing a filter expiry sends an empty value, not nothing`() {
        assertEquals("", FilterEndpoints.update("3", draft(expires = null)).endpoint.formValue("expires_in"))
        assertEquals("1800", FilterEndpoints.create(draft(expires = 1800)).endpoint.formValue("expires_in"))
        // kept as it is, which may be run out already: not sent, so the server keeps it
        val kept = draft().copy(keepExpiry = true)
        assertEquals(null, FilterEndpoints.update("3", kept).endpoint.formValue("expires_in"))
    }

    @Test
    fun `filter contexts are repeated and unknown ones are dropped`() {
        val contexts = listOf(FilterContext.Home, FilterContext.Notifications, FilterContext.Unknown)
        val endpoint = FilterEndpoints.create(draft(contexts)).endpoint
        val sent = endpoint.form().filter { it.name == "context[]" }.map { it.value }
        assertEquals(listOf("home", "notifications"), sent)
    }

    @Test
    fun `a status's link preview is a detail-screen request`() {
        assertEquals("api/v1/statuses/42/card", StatusEndpoints.card("42").endpoint.path)
    }

    @Test
    fun `the directory sends an order, never local, and clamps its limits`() {
        val active = SearchEndpoints.directory().endpoint
        assertEquals("api/v1/directory", active.path)
        assertEquals("active", active.queryValue("order"))
        assertNull(active.queryValue("local"))
        assertEquals(Authentication.None, active.authentication)
        val new = SearchEndpoints.directory(DirectoryOrder.New, limit = 200, offset = -5).endpoint
        assertEquals("new", new.queryValue("order"))
        assertEquals("50", new.queryValue("limit"))
        assertEquals("0", new.queryValue("offset"))
    }

    @Test
    fun `direct messages start from mutuals`() {
        assertEquals("api/v1.1/direct/compose/mutuals", TimelineEndpoints.directMessageMutuals().endpoint.path)
    }

    @Test
    fun `conversations can be emptied and cleared`() {
        val readAll = TimelineEndpoints.markAllConversationsRead().endpoint
        assertEquals(HttpMethod.POST, readAll.method)
        assertEquals("api/v1/conversations/read_all", readAll.path)
        val delete = TimelineEndpoints.deleteConversation("5").endpoint
        assertEquals(HttpMethod.DELETE, delete.method)
        assertEquals("api/v1/conversations/5", delete.path)
    }

    @Test
    fun `anchors produce the right cursor parameter`() {
        assertTrue(PageAnchor.Cold.queryItems().isEmpty())
        assertEquals("max_id", PageAnchor.OlderThan("41").queryItems().single().name)
        assertEquals("min_id", PageAnchor.NewerThan("60").queryItems().single().name)
        assertEquals("since_id", PageAnchor.ImmediatelyAfter("7").queryItems().single().name)
    }

    @Test
    fun `limit is clamped to the server's cap and never zero`() {
        assertEquals("50", TimelineEndpoints.timeline(TimelineSource.Home, limit = 500).endpoint.queryValue("limit"))
        assertEquals("1", TimelineEndpoints.timeline(TimelineSource.Home, limit = 0).endpoint.queryValue("limit"))
        assertEquals("1", BlockEndpoints.blocks(limit = 0).endpoint.queryValue("limit"))
    }

    @Test
    fun `list members are paged by the header and never with limit`() {
        assertNull(ListEndpoints.accounts("3").endpoint.queryValue("limit"))
    }

    @Test
    fun `only_video wins over only_media since every video is media`() {
        val filters = TimelineFilters(onlyMedia = true, onlyVideo = true)
        val names = TimelineEndpoints.timeline(TimelineSource.Home, filters).endpoint.queryNames()
        assertTrue("only_video" in names)
        assertFalse("only_media" in names)
    }

    @Test
    fun `a hashtag feed asks for its other tags and this server alone where it says so`() {
        val tag = TimelineSource.Hashtag(
            "surf",
            any = listOf("waves"),
            all = listOf("hawaii"),
            none = listOf("ads"),
            localOnly = true,
        )
        val endpoint = TimelineEndpoints.timeline(tag).endpoint
        assertEquals("api/v1/timelines/tag/surf", endpoint.path)
        assertEquals("waves", endpoint.queryValue("any[]"))
        assertEquals("hawaii", endpoint.queryValue("all[]"))
        assertEquals("ads", endpoint.queryValue("none[]"))
        assertEquals("true", endpoint.queryValue("local"))
        assertFalse("local" in TimelineEndpoints.timeline(TimelineSource.Hashtag("surf")).endpoint.queryNames())
    }

    @Test
    fun `one account's posts are searched by its id`() {
        val endpoint = SearchEndpoints.search("waves", type = "statuses", accountId = "7").endpoint
        assertEquals("7", endpoint.queryValue("account_id"))
        assertEquals("statuses", endpoint.queryValue("type"))
        assertEquals(null, SearchEndpoints.search("waves").endpoint.queryValue("account_id"))
    }

    @Test
    fun `another server's feed is its own public posts`() {
        val endpoint = TimelineEndpoints.timeline(TimelineSource.Remote("other.example")).endpoint
        assertEquals("api/v1/timelines/public/", endpoint.path)
        assertEquals("true", endpoint.queryValue("local"))
    }

    @Test
    fun `the notified feed reads the notifications of posts`() {
        val endpoint = TimelineEndpoints.timeline(TimelineSource.Notified).endpoint
        assertEquals("api/v1/notifications", endpoint.path)
        assertEquals("status", endpoint.queryValue("types[]"))
    }

    @Test
    fun `a false flag is omitted rather than sent as false`() {
        val names = TimelineEndpoints.timeline(TimelineSource.Home).endpoint.queryNames()
        assertFalse("only_media" in names)
        assertFalse("local" in names)
    }

    @Test
    fun `local narrows the public timeline`() {
        val endpoint = TimelineEndpoints.timeline(TimelineSource.Local).endpoint
        assertEquals("api/v1/timelines/public/", endpoint.path)
        assertEquals("true", endpoint.queryValue("local"))
        assertNull(TimelineEndpoints.timeline(TimelineSource.Federated).endpoint.queryValue("local"))
    }

    @Test
    fun `a list timeline has a route of its own`() {
        assertEquals("api/v1/timelines/list/3", TimelineEndpoints.timeline(TimelineSource.List("3")).endpoint.path)
    }

    @Test
    fun `an account timeline excludes replies unless asked`() {
        val source = TimelineSource.Account("7", includeReplies = false, onlyMedia = true)
        val endpoint = TimelineEndpoints.timeline(source).endpoint
        assertEquals("api/v1/accounts/7/statuses", endpoint.path)
        assertEquals("true", endpoint.queryValue("exclude_replies"))
        assertEquals("true", endpoint.queryValue("only_media"))
    }

    @Test
    fun `hashtags are normalised into the path`() {
        val tagTimeline = TimelineEndpoints.timeline(TimelineSource.Hashtag("#NextCloud")).endpoint
        assertEquals("api/v1/timelines/tag/nextcloud", tagTimeline.path)
        assertEquals("api/v1/tags/swift/follow", TagEndpoints.follow("#Swift").endpoint.path)
    }

    @Test
    fun `a post carries its idempotency key and an edit does not`() {
        val post = StatusPost(text = "Hello", idempotencyKey = "key-1")
        assertEquals("key-1", ComposeEndpoints.post(post).endpoint.idempotencyKey)
        assertNull(ComposeEndpoints.edit("9", post).endpoint.idempotencyKey)
    }

    @Test
    fun `a poll is dropped when media is attached`() {
        val withMedia = StatusPost(text = "x", mediaIds = listOf("m1"), pollOptions = listOf("a", "b"))
        val names = withMedia.formItems().map { it.name }
        assertTrue("media_ids[]" in names)
        assertFalse(names.any { it.startsWith("poll") })
        val poll = StatusPost(text = "x", pollOptions = listOf("a", "b")).formItems()
        assertEquals(listOf("a", "b"), poll.filter { it.name == "poll[options][]" }.map { it.value })
        assertEquals("86400", poll.first { it.name == "poll[expires_in]" }.value)
    }

    @Test
    fun `a quote goes out under both names the server reads`() {
        val items = StatusPost(text = "x", quotedId = "12").formItems()
        assertEquals("12", items.first { it.name == "quoted_id" }.value)
        assertEquals("12", items.first { it.name == "quote_id" }.value)
    }

    @Test
    fun `instance routes send no credential and preferences do`() {
        assertEquals(Authentication.None, InstanceEndpoints.v2().endpoint.authentication)
        assertEquals(Authentication.None, InstanceEndpoints.peers().endpoint.authentication)
        assertEquals(Authentication.Bearer, InstanceEndpoints.preferences().endpoint.authentication)
    }

    @Test
    fun `markers are written only for the timelines named`() {
        val endpoint = MarkerEndpoints.write(home = "5", notifications = null).endpoint
        assertEquals(listOf(QueryItem("home[last_read_id]", "5")), endpoint.form())
    }

    @Test
    fun `grouped notifications ask for their types and exclusions`() {
        val request = NotificationEndpoints.grouped(types = listOf("mention"), excludeTypes = listOf("follow"))
        val endpoint = request.endpoint
        assertEquals("api/v2/notifications", endpoint.path)
        assertEquals("mention", endpoint.queryValue("types[]"))
        assertEquals("follow", endpoint.queryValue("exclude_types[]"))
    }
}
