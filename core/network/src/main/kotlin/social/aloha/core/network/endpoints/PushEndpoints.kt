// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.NotificationKind
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.queryOf
import social.aloha.core.network.unitRequest

/**
 * Mastodon's Web Push subscription, one per token: the server sends every notification the alerts name to
 * [endpoint], encrypted for [p256dh] and [auth].
 */
public object PushEndpoints {
    // every kind a server can push, as the notifications name them
    private val ALERTS = listOf(
        NotificationKind.Mention, NotificationKind.Status, NotificationKind.Reblog, NotificationKind.Follow,
        NotificationKind.FollowRequest, NotificationKind.Favourite, NotificationKind.Poll, NotificationKind.Update,
        NotificationKind.AdminSignUp, NotificationKind.AdminReport,
    ).map { it.wire }

    /**
     * Subscribes, or replaces the token's subscription. `standard` asks for RFC 8291 encryption where
     * the server offers both; a server that only has the older one sends that, and it is never read here.
     */
    public fun subscribe(endpoint: String, p256dh: String, auth: String, policy: String = "all"): ApiRequest<Unit> =
        unitRequest(
            Endpoint(
                "api/v1/push/subscription",
                HttpMethod.POST,
                body = Body.Form(
                    queryOf("subscription[endpoint]", endpoint) +
                        queryOf("subscription[keys][p256dh]", p256dh) +
                        queryOf("subscription[keys][auth]", auth) +
                        queryOf("subscription[standard]", "true") +
                        ALERTS.map { QueryItem("data[alerts][$it]", "true") } +
                        queryOf("data[policy]", policy),
                ),
            ),
        )

    /** Whose notifications the server pushes from now on, the subscription otherwise as it was. */
    public fun policy(policy: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/push/subscription",
            HttpMethod.PUT,
            body = Body.Form(ALERTS.map { QueryItem("data[alerts][$it]", "true") } + queryOf("data[policy]", policy)),
        ),
    )

    public fun unsubscribe(): ApiRequest<Unit> = unitRequest(Endpoint("api/v1/push/subscription", HttpMethod.DELETE))
}
