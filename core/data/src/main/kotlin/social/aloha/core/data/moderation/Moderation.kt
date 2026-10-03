// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.moderation

import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.model.AdminLink
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.ModeratorRole
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.endpoints.AdminAccountEndpoints
import social.aloha.core.network.endpoints.ModerationEndpoints
import social.aloha.core.network.endpoints.ModerationEndpoints.TrendKind

/**
 * The admin API for a moderator: the reports queue, actions on accounts and the trends waiting for review.
 * The server checks the moderator on every call; [role] only decides whether the app offers any of it.
 */
public class Moderation @Inject constructor(private val clients: ClientFactory) {
    /** What [account] may moderate, nothing where the server reports no role; null when it could not be asked. */
    public suspend fun role(account: SignedInAccount): ModeratorRole? =
        (clients.answer(account, ModerationEndpoints.role()) as? Answer.Got)?.value

    public suspend fun reports(account: SignedInAccount, resolved: Boolean): Answer<List<AdminReport>> =
        clients.answer(account, ModerationEndpoints.reports(resolved = resolved, limit = PAGE))

    /** Takes the report, or gives it back. */
    public suspend fun assign(account: SignedInAccount, id: String, mine: Boolean): Answer<AdminReport> =
        clients.answer(
            account,
            if (mine) ModerationEndpoints.assignReportToSelf(id) else ModerationEndpoints.unassignReport(id),
        )

    public suspend fun resolve(account: SignedInAccount, id: String, resolved: Boolean): Answer<AdminReport> =
        clients.answer(
            account,
            if (resolved) ModerationEndpoints.resolveReport(id) else ModerationEndpoints.reopenReport(id),
        )

    /** Silences or suspends [target]; with [reportId] that report is resolved in the same step. True once taken. */
    public suspend fun act(
        account: SignedInAccount,
        target: String,
        action: AdminAccountAction,
        reportId: String? = null,
    ): Boolean = clients.answer(account, AdminAccountEndpoints.act(target, action, reportId = reportId)) is Answer.Got

    /** Accounts of [standing] and [origin], those whose username starts with [username] when it is not empty. */
    public suspend fun accounts(
        account: SignedInAccount,
        standing: AdminAccountEndpoints.Standing,
        origin: AdminAccountEndpoints.Origin = AdminAccountEndpoints.Origin.Any,
        username: String = "",
    ): Answer<List<AdminAccount>> = clients.answer(
        account,
        AdminAccountEndpoints.accounts(origin, standing, username = username.trim().removePrefix("@"), limit = PAGE),
    )

    public suspend fun unsilence(account: SignedInAccount, id: String): Answer<AdminAccount> =
        clients.answer(account, AdminAccountEndpoints.unsilence(id))

    public suspend fun unsuspend(account: SignedInAccount, id: String): Answer<AdminAccount> =
        clients.answer(account, AdminAccountEndpoints.unsuspend(id))

    public suspend fun unsensitive(account: SignedInAccount, id: String): Answer<AdminAccount> =
        clients.answer(account, AdminAccountEndpoints.unsensitive(id))

    public suspend fun trendingTags(account: SignedInAccount): Answer<List<AdminTag>> =
        clients.answer(account, ModerationEndpoints.trendingTags(PAGE))

    public suspend fun trendingPosts(account: SignedInAccount): Answer<List<Status>> =
        clients.answer(account, ModerationEndpoints.trendingStatuses(PAGE))

    public suspend fun trendingLinks(account: SignedInAccount): Answer<List<AdminLink>> =
        clients.answer(account, ModerationEndpoints.trendingLinks(PAGE))

    /** Lets a tag or post trend, or keeps it off the trending lists. True once taken. */
    public suspend fun review(account: SignedInAccount, kind: TrendKind, id: String, approve: Boolean): Boolean =
        clients.answer(
            account,
            if (approve) ModerationEndpoints.approveTrend(kind, id) else ModerationEndpoints.rejectTrend(kind, id),
        ) is Answer.Got

    private companion object {
        const val PAGE = 40
    }
}
