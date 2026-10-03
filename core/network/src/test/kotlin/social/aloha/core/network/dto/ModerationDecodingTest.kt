// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.AdminStanding
import social.aloha.core.model.AnnualArchetype
import social.aloha.core.model.AnnualReportState
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.AdminAccountEndpoints
import social.aloha.core.network.endpoints.AnnualReportEndpoints
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.ModerationEndpoints

private fun <T> ApiRequest<T>.decodeValue(body: String): T = decoder.decode(AlohaJson, body).value

class ModerationDecodingTest {
    private val wrapped = """
        {
          "annual_reports": [
            {
              "year": 2025,
              "data": {
                "archetype": "oracle",
                "time_series": [{"month": 1, "statuses": 3, "followers": 1}, {"month": 2, "statuses": 11, "followers": 0}],
                "top_hashtags": [{"name": "nextcloud", "count": 7}],
                "top_statuses": {"by_reblogs": "110", "by_replies": null, "by_favourites": "110"}
              },
              "schema_version": 1,
              "share_url": null,
              "account_id": "42"
            }
          ],
          "accounts": [],
          "statuses": []
        }
    """.trimIndent()

    @Test
    fun `a wrapped report decodes whole, with a null share_url as no URL`() {
        val report = AnnualReportEndpoints.all().decodeValue(wrapped).annualReports.single()
        assertEquals(2025, report.year)
        assertEquals(AnnualArchetype.Oracle, report.data.archetype)
        assertEquals(14, report.data.totalStatuses)
        assertEquals("nextcloud", report.data.topHashtags.first().name)
        assertEquals(listOf("110"), report.data.topStatuses.ids)
        assertNull(report.data.topStatuses.byReplies)
        assertNull(report.shareUrl)
        assertEquals("42", report.accountId)
    }

    @Test
    fun `an unknown archetype and a numeric account id do not fail the report`() {
        val body = """{"annual_reports": [{"year": 2024, "data": {"archetype": "sphinx"}, "account_id": 9}]}"""
        val report = AnnualReportEndpoints.year(2024).decodeValue(body).annualReports.single()
        assertEquals(AnnualArchetype.Unknown, report.data.archetype)
        assertEquals("9", report.accountId)
    }

    @Test
    fun `a report whose data has the wrong shape keeps its year`() {
        val body = """{"annual_reports": [{"year": "2023", "data": [], "top_statuses": 5}]}"""
        val report = AnnualReportEndpoints.all().decodeValue(body).annualReports.single()
        assertEquals(2023, report.year)
        assertEquals(AnnualArchetype.Unknown, report.data.archetype)
    }

    @Test
    fun `the state decodes the four words, an unknown fifth, and absence as ineligible`() {
        val state = AnnualReportEndpoints.state(2025)
        assertEquals(AnnualReportState.Available, state.decodeValue("""{"state":"available"}"""))
        assertEquals(AnnualReportState.Unknown, state.decodeValue("""{"state":"melting"}"""))
        assertEquals(AnnualReportState.Ineligible, state.decodeValue("{}"))
    }

    @Test
    fun `an admin account with an empty domain is local, and lenient booleans decide its standing`() {
        val body = """[{"id": 1, "username": "bob", "domain": "other.example", "silenced": "true", "suspended": 1},
            {"id": "2", "username": "eve", "domain": "", "silenced": true},
            {"id": "3", "username": "ada", "account": []}]"""
        val (bob, eve, ada) = AdminAccountEndpoints.accounts().decodeValue(body)
        assertEquals(AdminStanding.Suspended, bob.standing)
        assertEquals("@bob@other.example", bob.handle)
        assertTrue(eve.isLocal)
        assertEquals(AdminStanding.Silenced, eve.standing)
        assertEquals(AdminStanding.Active, ada.standing)
        assertNull(ada.account)
    }

    @Test
    fun `a report carries who, what and whether anybody has taken it`() {
        val report = ModerationEndpoints.report("7").decodeValue(
            """
            {
              "id": "7", "action_taken": false, "action_taken_at": null, "category": "spam",
              "comment": "posting the same link everywhere", "forwarded": true,
              "created_at": "2026-01-02T03:04:05.000Z", "updated_at": "2026-01-02T03:04:05.000Z",
              "account": {"id": "1", "username": "ada", "acct": "ada"},
              "target_account": {"id": "2", "username": "bob", "acct": "bob@other.example"},
              "assigned_account": null, "action_taken_by_account": null, "statuses": [{"broken": true}], "rules": []
            }
            """.trimIndent(),
        )
        assertEquals("7", report.id)
        assertFalse(report.actionTaken)
        assertTrue(report.forwarded)
        assertFalse(report.isAssigned)
        assertEquals("bob@other.example", report.targetAccount?.acct)
        assertNotNull(report.createdAt)
        assertTrue(report.statuses.isEmpty())
    }

    @Test
    fun `weekly activity decodes the strings the server actually sends`() {
        val week = InstanceEndpoints.activity()
            .decodeValue("""[{"week":"1767139200","statuses":"41","logins":"0","registrations":"0"}]""").single()
        assertEquals(1_767_139_200L, week.week)
        assertEquals(41, week.statuses)
        assertEquals(0, week.registrations)
    }

    @Test
    fun `a published domain block keys off its digest and defaults to suspend`() {
        val (block, bare) = InstanceEndpoints.domainBlocks()
            .decodeValue(
                """[{"domain":"bad.example","digest":"abc","severity":"silence","comment":""},""" +
                    """{"domain":"x.example"}]""",
            )
        assertEquals("abc", block.id)
        assertEquals("silence", block.severity)
        assertEquals("suspend", bare.severity)
        assertEquals("x.example", bare.id)
    }

    @Test
    fun `a Mastodon role grants what its permissions say, and no role grants nothing`() {
        val reports = ModerationEndpoints.role().decodeValue("""{"id":"1","role":{"id":"2","permissions":"16"}}""")
        assertTrue(reports.reports)
        assertFalse(reports.accounts)
        val admin = ModerationEndpoints.role().decodeValue("""{"id":"1","role":{"id":"3","permissions":1}}""")
        assertTrue(admin.reports && admin.accounts && admin.trends)
        val everyone = ModerationEndpoints.role().decodeValue("""{"id":"1","role":{"permissions":"65536"}}""")
        assertFalse(everyone.any)
        // Nextcloud Social before 0.26.108: `role` is null
        assertFalse(ModerationEndpoints.role().decodeValue("""{"id":"1","role":null}""").any)
    }

    @Test
    fun `the roles Nextcloud Social reports, as it reports them`() {
        val admin = ModerationEndpoints.role().decodeValue(
            """{"id":"1","role":{"id":"3","name":"Admin","color":"","permissions":"8388607","highlighted":true}}""",
        )
        assertTrue(admin.reports && admin.accounts && admin.trends)
        val moderator = ModerationEndpoints.role().decodeValue(
            """{"id":"1","role":{"id":"1","name":"Moderator","permissions":"591288","highlighted":true}}""",
        )
        assertTrue(moderator.reports && moderator.accounts && moderator.trends)
        val everyone = ModerationEndpoints.role().decodeValue(
            """{"id":"1","role":{"id":"-99","name":"","permissions":"65536","highlighted":false}}""",
        )
        assertFalse(everyone.any)
    }

    @Test
    fun `an admin link carries its id, or on Nextcloud Social its address`() {
        val links = ModerationEndpoints.trendingLinks().decodeValue(
            """[{"id":"12","url":"https://news.example/a","title":"A","provider_name":"News",""" +
                """"requires_review":true},""" +
                """{"url":"https://blog.example/b","title":"B"}]""",
        )
        assertEquals("12", links[0].id)
        assertTrue(links[0].requiresReview)
        assertEquals("https://blog.example/b", links[1].id)
    }

    @Test
    fun `an admin tag carries the id the review routes take, and today's uses`() {
        val tags = ModerationEndpoints.trendingTags().decodeValue(
            """[{"id":"37","name":"Aloha","requires_review":true,"trendable":false,""" +
                """"history":[{"day":"1790000000","uses":"12","accounts":"5"}]},{"name":"nextcloud"}]""",
        )
        assertEquals("37", tags[0].id)
        assertEquals(12, tags[0].uses)
        assertEquals(5, tags[0].accounts)
        assertTrue(tags[0].requiresReview)
        assertFalse(tags[0].trendable)
        assertEquals("nextcloud", tags[1].id)
    }
}
