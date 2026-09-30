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
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * The Nextcloud under a Social server, asked at its root rather than at the Social API base: whether it is
 * up, Login Flow v2 for an app password, what the signed-in capabilities offer, and giving the password
 * back.
 */
public object NextcloudEndpoints {
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

    /** Revokes the app password the request is made with. */
    public fun revokeAppPassword(): ApiRequest<Unit> = unitRequest(
        Endpoint("ocs/v2.php/core/apppassword", HttpMethod.DELETE, authentication = Authentication.NextcloudSession),
    )
}
