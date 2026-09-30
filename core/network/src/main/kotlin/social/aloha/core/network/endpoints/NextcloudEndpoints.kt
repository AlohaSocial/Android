// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.AppPasswordGrant
import social.aloha.core.model.LoginFlowStart
import social.aloha.core.model.NextcloudStatus
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.dto.AppPasswordGrantDto
import social.aloha.core.network.dto.LoginFlowStartDto
import social.aloha.core.network.dto.NextcloudStatusDto
import social.aloha.core.network.dto.OcsCapabilitiesDto
import social.aloha.core.network.dto.OcsVapidDto
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * The Nextcloud under a Social server, asked at its root rather than at the Social API base: whether it is
 * up, Login Flow v2 for an app password, what the signed-in capabilities offer, the notifications app's
 * Web Push registration, and giving the password back.
 */
public object NextcloudEndpoints {
    private const val WEB_PUSH = "ocs/v2.php/apps/notifications/api/v2/webpush"

    // only the Social app's notifications, never Talk's or Files', which their own apps raise
    private const val SOCIAL = "social"

    public fun status(): ApiRequest<NextcloudStatus> =
        request(Endpoint("status.php", authentication = Authentication.None), NextcloudStatusDto.serializer()) {
            it.toDomain()
        }

    /** Answers 404 on a server that is no Nextcloud. */
    public fun loginFlowStart(): ApiRequest<LoginFlowStart> = request(
        Endpoint("index.php/login/v2", HttpMethod.POST, authentication = Authentication.None),
        LoginFlowStartDto.serializer(),
    ) { it.toDomain() }

    /** Asks for the grant at [path], below the root; 404 while the person has not approved yet. */
    public fun loginFlowPoll(path: String, token: String): ApiRequest<AppPasswordGrant> = request(
        Endpoint(
            path,
            HttpMethod.POST,
            body = Body.Form(queryOf("token", token)),
            authentication = Authentication.None,
        ),
        AppPasswordGrantDto.serializer(),
    ) { it.toDomain() }

    /** The push kinds the notifications app offers this person, which only a signed-in request sees. */
    public fun pushTypes(): ApiRequest<List<String>> = request(
        Endpoint(
            "ocs/v2.php/cloud/capabilities",
            query = queryOf("format", "json"),
            authentication = Authentication.NextcloudSession,
        ),
        OcsCapabilitiesDto.serializer(),
    ) { it.ocs?.data?.capabilities?.notifications?.push.orEmpty() }

    /** The notifications app's VAPID key, which a Web Push registration is made for. */
    public fun webPushVapid(): ApiRequest<String> = request(
        Endpoint(
            WEB_PUSH + "/vapid",
            query = queryOf("format", "json"),
            authentication = Authentication.NextcloudSession,
        ),
        OcsVapidDto.serializer(),
    ) { it.ocs?.data?.vapid.orEmpty() }

    /**
     * Registers [endpoint] for the Social app's notifications of this app password's user, encrypted for
     * [uaPublicKey] and [auth]. A new registration answers 201 and waits for its activation push.
     */
    public fun registerWebPush(endpoint: String, uaPublicKey: String, auth: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            WEB_PUSH,
            HttpMethod.POST,
            query = queryOf("format", "json"),
            body = Body.Form(
                queryOf("endpoint", endpoint) + queryOf("uaPublicKey", uaPublicKey) + queryOf("auth", auth) +
                    queryOf("appTypes", SOCIAL),
            ),
            authentication = Authentication.NextcloudSession,
        ),
    )

    /** Confirms the registration with the token its first push carried. */
    public fun activateWebPush(token: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            WEB_PUSH + "/activate",
            HttpMethod.POST,
            query = queryOf("format", "json"),
            body = Body.Form(queryOf("activationToken", token)),
            authentication = Authentication.NextcloudSession,
        ),
    )

    /** Removes this app password's registration. */
    public fun unregisterWebPush(): ApiRequest<Unit> = unitRequest(
        Endpoint(
            WEB_PUSH,
            HttpMethod.DELETE,
            query = queryOf("format", "json"),
            authentication = Authentication.NextcloudSession,
        ),
    )

    /** Revokes the app password the request is made with. */
    public fun revokeAppPassword(): ApiRequest<Unit> = unitRequest(
        Endpoint("ocs/v2.php/core/apppassword", HttpMethod.DELETE, authentication = Authentication.NextcloudSession),
    )
}
