// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.InstanceActivityWeek
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.InstanceDocument
import social.aloha.core.model.InstanceRule
import social.aloha.core.model.Preferences
import social.aloha.core.model.PublicDomainBlock
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.CustomEmojiDto
import social.aloha.core.network.dto.InstanceActivityWeekDto
import social.aloha.core.network.dto.InstanceDocumentDto
import social.aloha.core.network.dto.InstanceRuleDto
import social.aloha.core.network.dto.InstanceV1Dto
import social.aloha.core.network.dto.InstanceV2Dto
import social.aloha.core.network.dto.PreferencesDto
import social.aloha.core.network.dto.PublicDomainBlockDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * The server's description of itself. Every route here is public: it is what the sign-in screen shows
 * before anybody has signed in, so no credential is sent.
 */
public object InstanceEndpoints {
    /** v2 first; v1 after a 404. */
    public fun v2(): ApiRequest<InstanceDescription> =
        request(public("api/v2/instance"), InstanceV2Dto.serializer()) { it.toDomain() }

    public fun v1(): ApiRequest<InstanceDescription> =
        request(public("api/v1/instance/"), InstanceV1Dto.serializer()) { it.toDomain() }

    public fun rules(): ApiRequest<List<InstanceRule>> =
        listRequest(public("api/v1/instance/rules"), InstanceRuleDto.serializer()) { it.toDomain() }

    public fun extendedDescription(): ApiRequest<InstanceDocument> = document("api/v1/instance/extended_description")

    /** 404 where the administrator has published none; the row is then hidden, not shown broken. */
    public fun privacyPolicy(): ApiRequest<InstanceDocument> = document("api/v1/instance/privacy_policy")

    public fun termsOfService(): ApiRequest<InstanceDocument> = document("api/v1/instance/terms_of_service")

    /** Which languages each source language can be translated into; `{}` without a provider. */
    public fun translationLanguages(): ApiRequest<Map<String, List<String>>> = request(
        public("api/v1/instance/translation_languages"),
        MapSerializer(String.serializer(), ListSerializer(String.serializer())),
    ) { it }

    public fun customEmojis(): ApiRequest<List<CustomEmoji>> =
        listRequest(public("api/v1/custom_emojis"), CustomEmojiDto.serializer()) { it.toDomain() }

    /** Every instance this one has heard of, as bare hostnames. */
    public fun peers(): ApiRequest<List<String>> = listRequest(public("api/v1/instance/peers"), String.serializer()) {
        it
    }

    /** The instance's weekly activity, newest week first. */
    public fun activity(): ApiRequest<List<InstanceActivityWeek>> =
        listRequest(public("api/v1/instance/activity"), InstanceActivityWeekDto.serializer()) { it.toDomain() }

    /** The servers this one refuses, where the administrator publishes the list; empty otherwise, not an error. */
    public fun domainBlocks(): ApiRequest<List<PublicDomainBlock>> =
        listRequest(public("api/v1/instance/domain_blocks"), PublicDomainBlockDto.serializer()) { it.toDomain() }

    /** The account's preferences; this one needs the viewer. */
    public fun preferences(): ApiRequest<Preferences> =
        request(Endpoint("api/v1/preferences"), PreferencesDto.serializer()) { it.toDomain() }

    /**
     * Sets how sensitive media is shown (Nextcloud extension). [policy] is a
     * [social.aloha.core.model.SensitiveMediaPolicy] wire value, or `""` to follow the instance again.
     */
    public fun setExpandMedia(policy: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/preferences", HttpMethod.PUT, body = Body.Form(listOf(QueryItem("expandMedia", policy)))),
    )
}

private fun public(path: String): Endpoint = Endpoint(path, authentication = Authentication.None)

private fun document(path: String): ApiRequest<InstanceDocument> =
    request(public(path), InstanceDocumentDto.serializer()) { it.toDomain() }
