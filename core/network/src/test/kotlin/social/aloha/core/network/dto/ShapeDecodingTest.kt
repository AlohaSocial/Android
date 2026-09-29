// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.MigrationEntry
import social.aloha.core.model.MigrationLine
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.AuthorizedAppEndpoints
import social.aloha.core.network.endpoints.ChannelEndpoints
import social.aloha.core.network.endpoints.DiscoveryEndpoints
import social.aloha.core.network.endpoints.GifEndpoints
import social.aloha.core.network.endpoints.MigrationEndpoints
import social.aloha.core.network.endpoints.ProfileEndpoints
import social.aloha.core.network.endpoints.SafetyEndpoints
import social.aloha.core.network.endpoints.StatisticsEndpoints
import social.aloha.core.network.endpoints.SubscriptionEndpoints
import social.aloha.core.network.endpoints.TeamEndpoints

private fun <T> ApiRequest<T>.value(body: String): T = decoder.decode(AlohaJson, body).value

private const val ACCOUNT = """{"id": "7", "username": "bob", "acct": "bob"}"""

class ShapeDecodingTest {
    @Test
    fun `directory search reads the documented object and a bare array`() {
        val documented = """{"accounts": [$ACCOUNT], "sources": [{"host": "a.example", "accounts": 3}]}"""
        val results = DiscoveryEndpoints.directorySearch("bob").value(documented)
        assertEquals("bob", results.accounts.single().acct)
        assertEquals(3, results.sources.single().count)
        assertEquals(1, DiscoveryEndpoints.directorySearch("bob").value("[$ACCOUNT]").accounts.size)
    }

    @Test
    fun `directory hashtags arrive as hashtags or tags`() {
        assertEquals(
            "aloha",
            DiscoveryEndpoints.directoryHashtags().value("""{"tags": [{"name": "aloha"}]}""").hashtags.single().name,
        )
    }

    @Test
    fun `a follow graph suggestion is a wrapper or a bare account`() {
        val body = """{"suggestions": [{"account": $ACCOUNT, "followers": 4, "through": [$ACCOUNT]}, $ACCOUNT, "junk"],
            "asked": 10, "needs": 5}"""
        val graph = DiscoveryEndpoints.followGraph().value(body)
        assertEquals(listOf(4, 0), graph.suggestions.map { it.count })
        assertEquals(1, graph.suggestions.first().via.size)
        assertEquals(10, graph.asked)
    }

    @Test
    fun `with no flag, enough follows makes the follow graph worthwhile`() {
        assertTrue(DiscoveryEndpoints.followGraphStatus().value("""{"following": 12, "needs": 10}""").isWorthwhile)
        assertFalse(DiscoveryEndpoints.followGraphStatus().value("""{"following": 2, "needs": 10}""").isWorthwhile)
        assertFalse(DiscoveryEndpoints.followGraphStatus().value("""{"eligible": false, "needs": 0}""").isWorthwhile)
    }

    @Test
    fun `discover categories normalise their hashtags and fall back to the name as id`() {
        val category = DiscoveryEndpoints.categories().value(
            """{"categories": [{"name": "Nature", "tags": ["#Trees", " "]}]}""",
        )
            .single()
        assertEquals("nature", category.id)
        assertEquals(listOf("trees"), category.hashtags)
    }

    @Test
    fun `a tagged person is an account-shaped object or a bare handle`() {
        val body = """{"tagged_people": ["ada", {"id": 9, "username": "grace", "display_name": "Grace"}]}"""
        val people = ProfileEndpoints.tagPeople("1", listOf("ada")).value(body)
        assertEquals(listOf("ada", "grace"), people.map { it.acct })
        assertEquals("9", people[1].id)
    }

    @Test
    fun `a migration lookup reads both shapes and lists each handle once`() {
        val results = """{"results": [{"handle": "ada@x.example", "found": true}, {"acct": "", "found": true}]}"""
        assertEquals(
            listOf(MigrationEntry("ada@x.example", true)),
            MigrationEndpoints.findPeople(emptyList()).value(results).entries,
        )
        val split = """{"found": ["ada@x.example", $ACCOUNT], "missing": ["ada@x.example", "eve@y.example"]}"""
        val entries = MigrationEndpoints.findPeople(emptyList()).value(split).entries
        assertEquals(listOf("ada@x.example", "bob", "eve@y.example"), entries.map { it.handle })
        assertEquals(listOf(true, true, false), entries.map { it.found })
    }

    @Test
    fun `a migration report keeps every key as it came`() {
        val report = MigrationEndpoints.importVideo(
            "u",
            false,
        ).value("""{"skipped": 2, "done": true, "failed": ["a", "b"], "log": ["one"]}""")
        assertEquals(
            listOf(MigrationLine("done", "yes"), MigrationLine("failed", "2"), MigrationLine("skipped", "2")),
            report.lines,
        )
        assertEquals(listOf("one"), report.log)
    }

    @Test
    fun `statistics read numbers sent as strings and booleans`() {
        val body = """{"posts": {"total": "12", "public": true, "odd": {}}, "by_month": {"2026-02": 1, "2026-01": 3},
            "window": {"days": "30", "capped": 1}, "languages": [{"name": "en", "count": "5"}]}"""
        val statistics = StatisticsEndpoints.overview(30).value(body)
        assertEquals(mapOf("total" to 12.0, "public" to 1.0), statistics.posts.values)
        assertEquals(listOf("2026-01", "2026-02"), statistics.byMonth.sorted.map { it.first })
        assertEquals(30, statistics.window?.days)
        assertEquals(5, statistics.languages.single().count)
    }

    @Test
    fun `an authorised app's scopes arrive as an array or one string`() {
        val body = """[{"id": 1, "name": "A", "scopes": "read write"}, {"id": 2, "scopes": ["push"]}]"""
        assertEquals(
            listOf(listOf("read", "write"), listOf("push")),
            AuthorizedAppEndpoints.all().value(body).map {
                it.scopes
            },
        )
    }

    @Test
    fun `wrapped lists unwrap and a bare one reads as it is`() {
        assertEquals(1, TeamEndpoints.all().value("""{"teams": [{"acct": "team", "display_name": "Team"}]}""").size)
        assertEquals(1, ChannelEndpoints.all().value("""{"channels": [{"id": 1, "handle": "c"}]}""").size)
        assertEquals(
            "site.example",
            SubscriptionEndpoints.feeds().value("""{"feeds": [{"id": 1, "link": "https://site.example"}]}""")
                .single().displayTitle,
        )
        assertEquals(1, SubscriptionEndpoints.timeline().value("""[{"id": 1, "title": "Entry"}]""").size)
        assertEquals(listOf("a@b.example"), MigrationEndpoints.aliases().value("""{"aliases": ["a@b.example"]}"""))
    }

    @Test
    fun `the GIF library counts its page when the server sends no total`() {
        val library = GifEndpoints.library("cat").value("""{"gifs": [{"slug": "noto-1f63a", "name": "smiley cat"}]}""")
        assertEquals(1, library.total)
        assertEquals("smiley cat", library.gifs.single().title)
    }

    @Test
    fun `announcements carry their reactions`() {
        val body = """[{"id": 1, "content": "Hi", "all_day": true,
            "reactions": [{"name": "🎉", "count": "3", "me": 1}]}]"""
        val announcement = SafetyEndpoints.announcements().value(body).single()
        assertTrue(announcement.allDay)
        assertEquals(3, announcement.reactions.single().count)
        assertTrue(announcement.reactions.single().me)
    }
}
