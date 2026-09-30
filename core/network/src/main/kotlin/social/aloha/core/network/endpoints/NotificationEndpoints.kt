// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.GroupedNotifications
import social.aloha.core.model.MarkerSet
import social.aloha.core.model.Notification
import social.aloha.core.model.NotificationPolicy
import social.aloha.core.model.NotificationRequest
import social.aloha.core.model.UnreadCount
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.GroupedNotificationsDto
import social.aloha.core.network.dto.MarkerSetDto
import social.aloha.core.network.dto.NotificationDto
import social.aloha.core.network.dto.NotificationPolicyDto
import social.aloha.core.network.dto.NotificationRequestDto
import social.aloha.core.network.dto.UnreadCountDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.pagedObjectRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

public object NotificationEndpoints {
    private const val GROUPED_LIMIT = 40

    /** Grouped notifications (Mastodon 4.3); the page is `notification_groups`. */
    public fun grouped(
        limit: Int = GROUPED_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
        types: List<String> = emptyList(),
        excludeTypes: List<String> = emptyList(),
    ): ApiRequest<GroupedNotifications> = pagedObjectRequest(
        Endpoint("api/v2/notifications", query = filtered(limit, anchor, types, excludeTypes)),
        GroupedNotificationsDto.serializer(),
        pageMember = "notification_groups",
    ) { it.toDomain() }

    public fun flat(
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
        types: List<String> = emptyList(),
        excludeTypes: List<String> = emptyList(),
    ): ApiRequest<List<Notification>> = listRequest(
        Endpoint("api/v1/notifications", query = filtered(limit, anchor, types, excludeTypes)),
        NotificationDto.serializer(),
    ) { it.toDomain() }

    private fun filtered(limit: Int, anchor: PageAnchor, types: List<String>, excludeTypes: List<String>) =
        Paging.pageItems(limit, anchor) + repeatedQuery("types", types) + repeatedQuery("exclude_types", excludeTypes)

    public fun unreadCount(grouped: Boolean): ApiRequest<UnreadCount> = request(
        Endpoint(if (grouped) "api/v2/notifications/unread_count" else "api/v1/notifications/unread_count"),
        UnreadCountDto.serializer(),
    ) { it.toDomain() }

    public fun groupAccounts(groupKey: String): ApiRequest<List<Account>> =
        listRequest(Endpoint("api/v2/notifications/$groupKey/accounts"), AccountDto.serializer()) { it.toDomain() }

    public fun dismissGroup(groupKey: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v2/notifications/$groupKey/dismiss", HttpMethod.POST))

    public fun dismiss(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/notifications/$id/dismiss", HttpMethod.POST))

    public fun clear(): ApiRequest<Unit> = unitRequest(Endpoint("api/v1/notifications/clear", HttpMethod.POST))
}

/** What the server holds back: the policy that decides it, and the senders whose notifications wait. */
public object NotificationFilteringEndpoints {
    private const val REQUESTS_LIMIT = 40

    /** The v2 route is where a 4.3 client looks; the v1 spelling is for servers written against 4.2. */
    public fun policy(v2: Boolean): ApiRequest<NotificationPolicy> = request(
        Endpoint(if (v2) "api/v2/notifications/policy" else "api/v1/notifications/policy"),
        NotificationPolicyDto.serializer(),
    ) { it.toDomain() }

    /**
     * Sets the five decisions; the v2 route only, since the v1 one of Mastodon 4.2 took booleans for a
     * different set of senders.
     */
    public fun updatePolicy(policy: NotificationPolicy): ApiRequest<NotificationPolicy> = request(
        Endpoint(
            "api/v2/notifications/policy",
            HttpMethod.PATCH,
            body = Body.Form(
                queryOf("for_not_following", policy.forNotFollowing.wire) +
                    queryOf("for_not_followers", policy.forNotFollowers.wire) +
                    queryOf("for_new_accounts", policy.forNewAccounts.wire) +
                    queryOf("for_private_mentions", policy.forPrivateMentions.wire) +
                    queryOf("for_limited_accounts", policy.forLimitedAccounts.wire),
            ),
        ),
        NotificationPolicyDto.serializer(),
    ) { it.toDomain() }

    public fun requests(): ApiRequest<List<NotificationRequest>> = listRequest(
        Endpoint("api/v1/notifications/requests", query = listOf(Paging.limitItem(REQUESTS_LIMIT, REQUESTS_LIMIT))),
        NotificationRequestDto.serializer(),
    ) { it.toDomain() }

    public fun acceptRequest(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/notifications/requests/$id/accept", HttpMethod.POST))

    public fun dismissRequest(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/notifications/requests/$id/dismiss", HttpMethod.POST))

    /** Accepts every request in [ids] at once (Mastodon 4.3); a server without the route answers 404. */
    public fun acceptRequests(ids: List<String>): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/notifications/requests/accept", HttpMethod.POST, body = Body.Form(repeatedQuery("id", ids))),
    )

    /** Dismisses every request in [ids] at once (Mastodon 4.3); a server without the route answers 404. */
    public fun dismissRequests(ids: List<String>): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/notifications/requests/dismiss", HttpMethod.POST, body = Body.Form(repeatedQuery("id", ids))),
    )
}

public object MarkerEndpoints {
    public fun read(): ApiRequest<MarkerSet> = request(
        Endpoint("api/v1/markers", query = repeatedQuery("timeline", listOf("home", "notifications"))),
        MarkerSetDto.serializer(),
    ) { it.toDomain() }

    /**
     * Moves the read markers. A marker never moves backwards: the server enforces it and so does the
     * caller, so a device that is behind cannot un-read what another has read.
     */
    public fun write(home: String?, notifications: String?): ApiRequest<MarkerSet> = request(
        Endpoint(
            "api/v1/markers",
            HttpMethod.POST,
            body = Body.Form(
                queryOf("home[last_read_id]", home) + queryOf("notifications[last_read_id]", notifications),
            ),
        ),
        MarkerSetDto.serializer(),
    ) { it.toDomain() }
}
