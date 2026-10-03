// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.AdminLink
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.ModeratorRole
import social.aloha.core.model.Status
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AdminLinkDto
import social.aloha.core.network.dto.AdminReportDto
import social.aloha.core.network.dto.AdminTagDto
import social.aloha.core.network.dto.RoleHolderDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * The reports queue and trend moderation of Mastodon's admin API under `/api/v1/admin/`, which Nextcloud
 * Social serves from its admin controller. Every route takes the bearer token, and the server checks on
 * each one that the Nextcloud user behind it is an administrator; a 403 is the honest answer for anybody
 * else, and the app hides the section then rather than showing rows it cannot load. Instance
 * configuration stays in the web administration. Accounts are in [AdminAccountEndpoints].
 */
public object ModerationEndpoints {
    /** What a trend decision is about. */
    public enum class TrendKind(internal val path: String) { Tags("tags"), Statuses("statuses"), Links("links") }

    /** The queue a moderator works through; unresolved only unless [resolved]. */
    public fun reports(
        resolved: Boolean = false,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<AdminReport>> = listRequest(
        Endpoint(
            "api/v1/admin/reports",
            query = Paging.pageItems(limit, anchor) + QueryItem("resolved", resolved.toString()),
        ),
        AdminReportDto.serializer(),
    ) { it.toDomain() }

    public fun report(id: String): ApiRequest<AdminReport> = reportRequest(Endpoint("api/v1/admin/reports/$id"))

    public fun resolveReport(id: String): ApiRequest<AdminReport> = reportAction(id, "resolve")

    public fun reopenReport(id: String): ApiRequest<AdminReport> = reportAction(id, "reopen")

    public fun assignReportToSelf(id: String): ApiRequest<AdminReport> = reportAction(id, "assign_to_self")

    public fun unassignReport(id: String): ApiRequest<AdminReport> = reportAction(id, "unassign")

    /** What may trend, from the moderator's side of the same readers the public routes use. */
    public fun trendingTags(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<AdminTag>> =
        listRequest(trends("tags", limit), AdminTagDto.serializer()) { it.toDomain() }

    /** What the signed-in person may moderate, from the `role` on `verify_credentials`; nothing without one. */
    public fun role(): ApiRequest<ModeratorRole> =
        request(Endpoint("api/v1/accounts/verify_credentials"), RoleHolderDto.serializer()) { it.toDomain() }

    public fun trendingStatuses(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Status>> =
        listRequest(trends("statuses", limit), StatusDto.serializer()) { it.toDomain() }

    public fun trendingLinks(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<AdminLink>> =
        listRequest(trends("links", limit), AdminLinkDto.serializer()) { it.toDomain() }

    /**
     * Records that a moderator looked. Everything nobody objected to trends already, so approving grants
     * nothing; only [rejectTrend] changes what readers see.
     */
    public fun approveTrend(kind: TrendKind, id: String): ApiRequest<Unit> = trendDecision(kind, id, "approve")

    /** Hides the tag, status or link from the trending routes. */
    public fun rejectTrend(kind: TrendKind, id: String): ApiRequest<Unit> = trendDecision(kind, id, "reject")
}

private fun reportRequest(endpoint: Endpoint) = request(endpoint, AdminReportDto.serializer()) { it.toDomain() }

private fun reportAction(id: String, action: String) =
    reportRequest(Endpoint("api/v1/admin/reports/$id/$action", HttpMethod.POST))

private fun trends(kind: String, limit: Int) =
    Endpoint("api/v1/admin/trends/$kind", query = listOf(Paging.limitItem(limit)))

private fun trendDecision(kind: ModerationEndpoints.TrendKind, id: String, decision: String) =
    unitRequest(Endpoint("api/v1/admin/trends/${kind.path}/$id/$decision", HttpMethod.POST))
