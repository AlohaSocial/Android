// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.NotificationPolicy
import social.aloha.core.model.NotificationRequest
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.NotificationFilteringEndpoints

/**
 * What the server holds back from the notifications list: the policy that decides it, and the requests
 * inbox of senders whose notifications wait there.
 */
public class NotificationFiltering @Inject constructor(private val clients: ClientFactory) {
    public suspend fun policy(account: SignedInAccount): Answer<NotificationPolicy> =
        clients.answer(account, NotificationFilteringEndpoints.policy(v2 = true))

    public suspend fun setPolicy(account: SignedInAccount, policy: NotificationPolicy): Answer<NotificationPolicy> =
        clients.answer(account, NotificationFilteringEndpoints.updatePolicy(policy))

    public suspend fun requests(account: SignedInAccount): Answer<List<NotificationRequest>> =
        clients.answer(account, NotificationFilteringEndpoints.requests())

    /** Lets [ids]'s notifications through, all at once where the server can, else one by one. */
    public suspend fun accept(account: SignedInAccount, ids: List<String>): ApiError? = decide(
        account,
        ids,
        NotificationFilteringEndpoints::acceptRequests,
        NotificationFilteringEndpoints::acceptRequest,
    )

    /** Drops [ids]'s held notifications, all at once where the server can, else one by one. */
    public suspend fun dismiss(account: SignedInAccount, ids: List<String>): ApiError? = decide(
        account,
        ids,
        NotificationFilteringEndpoints::dismissRequests,
        NotificationFilteringEndpoints::dismissRequest,
    )

    private suspend fun decide(
        account: SignedInAccount,
        ids: List<String>,
        all: (List<String>) -> ApiRequest<Unit>,
        one: (String) -> ApiRequest<Unit>,
    ): ApiError? {
        val bulk = if (ids.size > 1) clients.answer(account, all(ids)) else null
        return when {
            bulk is Answer.Got -> null

            bulk is Answer.Missed && bulk.error !is ApiError.NotFound -> bulk.error

            // every one is asked, even after one failed: the rest still deserve their answer
            else -> ids.mapNotNull { (clients.answer(account, one(it)) as? Answer.Missed)?.error }.firstOrNull()
        }
    }
}
