// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.Serializable
import social.aloha.core.model.AppPasswordGrant
import social.aloha.core.model.LoginFlowStart
import social.aloha.core.model.NextcloudStatus
import social.aloha.core.network.decoding.LenientTextSerializer

@Serializable
internal data class NextcloudStatusDto(
    val installed: Boolean = false,
    val maintenance: Boolean = false,
    @Serializable(with = LenientTextSerializer::class) val version: String? = null,
) {
    fun toDomain() = NextcloudStatus(installed, maintenance, version.orEmpty())
}

@Serializable
internal data class LoginFlowStartDto(val poll: PollDto, val login: String) {
    @Serializable
    data class PollDto(val token: String, val endpoint: String)

    fun toDomain() = LoginFlowStart(login, poll.token, poll.endpoint)
}

@Serializable
internal data class AppPasswordGrantDto(val server: String, val loginName: String, val appPassword: String) {
    fun toDomain() = AppPasswordGrant(server, loginName, appPassword)
}
