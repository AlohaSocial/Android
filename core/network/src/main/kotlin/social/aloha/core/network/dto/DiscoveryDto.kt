// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AuthorizationServerMetadata
import social.aloha.core.model.NodeInfo
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer

@Serializable
internal data class NodeInfoDto(
    val version: String = "",
    val software: SoftwareDto = SoftwareDto(),
    @Serializable(with = LossyListSerializer::class) val protocols: List<String> = emptyList(),
    @Serializable(with = LenientBoolSerializer::class) val openRegistrations: Boolean = false,
) {
    @Serializable
    data class SoftwareDto(
        val name: String = "",
        val version: String = "",
        @Serializable(with = LenientUrlSerializer::class) val repository: String? = null,
        @Serializable(with = LenientUrlSerializer::class) val homepage: String? = null,
    )
}

internal fun NodeInfoDto.toDomain(): NodeInfo = NodeInfo(
    version = version,
    softwareName = software.name,
    softwareVersion = software.version,
    repository = software.repository,
    homepage = software.homepage,
    protocols = protocols,
    openRegistrations = openRegistrations,
)

/** `/.well-known/nodeinfo`: where the NodeInfo documents are. */
@Serializable
internal data class NodeInfoDirectoryDto(
    @Serializable(with = LossyListSerializer::class) val links: List<LinkDto> = emptyList(),
) {
    @Serializable
    data class LinkDto(val rel: String = "", @Serializable(with = LenientUrlSerializer::class) val href: String? = null)

    /** The href of the newest schema version listed, since 2.1 carries the repository and homepage. */
    fun newestHref(): String? = links.filter { it.href != null && it.rel.contains("nodeinfo") }
        .maxByOrNull { it.rel.substringAfterLast('/') }
        ?.href
}

@Serializable
internal data class AuthorizationServerMetadataDto(
    @Serializable(with = LenientUrlSerializer::class) val issuer: String? = null,
    @SerialName("authorization_endpoint") @Serializable(with = LenientUrlSerializer::class)
    val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint") @Serializable(with = LenientUrlSerializer::class) val tokenEndpoint: String? = null,
    @SerialName("revocation_endpoint") @Serializable(with = LenientUrlSerializer::class)
    val revocationEndpoint: String? = null,
    @SerialName("userinfo_endpoint") @Serializable(with = LenientUrlSerializer::class)
    val userinfoEndpoint: String? = null,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethods: List<String>? = null,
    @SerialName("scopes_supported") val scopes: List<String>? = null,
)

/** Null when the document names no issuer, which makes it useless for discovery. */
internal fun AuthorizationServerMetadataDto.toDomain(): AuthorizationServerMetadata? = issuer?.let {
    AuthorizationServerMetadata(
        issuer = it,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        revocationEndpoint = revocationEndpoint,
        userinfoEndpoint = userinfoEndpoint,
        codeChallengeMethods = codeChallengeMethods,
        scopes = scopes,
    )
}
