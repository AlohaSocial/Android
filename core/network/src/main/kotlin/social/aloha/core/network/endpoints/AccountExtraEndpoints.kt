// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.io.File
import social.aloha.core.model.Account
import social.aloha.core.model.AccountField
import social.aloha.core.model.AuthorizedApp
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.PortfolioPage
import social.aloha.core.model.PortfolioSettings
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.Part
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.AuthorizedAppDto
import social.aloha.core.network.dto.CollectionDto
import social.aloha.core.network.dto.FeaturedTagDto
import social.aloha.core.network.dto.NamesSerializer
import social.aloha.core.network.dto.PortfolioDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.dto.toPage
import social.aloha.core.network.dto.toSettings
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

private fun Boolean.asFlag(): String = if (this) "1" else "0"

/** A new avatar or header, streamed from disk. */
public data class ProfilePicture(val file: File, val fileName: String, val mimeType: String)

/**
 * What `PATCH /api/v1/accounts/update_credentials` may change. Only the fields that are set are
 * sent, so a save touches nothing it did not mean to.
 */
public data class CredentialsUpdate(
    val displayName: String? = null,
    val note: String? = null,
    val avatar: ProfilePicture? = null,
    val header: ProfilePicture? = null,
    val fields: List<AccountField>? = null,
    val locked: Boolean? = null,
    val discoverable: Boolean? = null,
    val indexable: Boolean? = null,
    val bot: Boolean? = null,
    val privacy: Visibility? = null,
    val sensitive: Boolean? = null,
    val language: String? = null,
) {
    val isEmpty: Boolean get() = parts().isEmpty()

    internal fun parts(): List<Part> = buildList {
        displayName?.let { add(Part.Field("display_name", it)) }
        note?.let { add(Part.Field("note", it)) }
        avatar?.let { add(Part.FileContent("avatar", it.file, it.fileName, it.mimeType)) }
        header?.let { add(Part.FileContent("header", it.file, it.fileName, it.mimeType)) }
        fields?.forEachIndexed { index, field ->
            add(Part.Field("fields_attributes[$index][name]", field.name))
            add(Part.Field("fields_attributes[$index][value]", field.value))
        }
        flags().forEach { (name, value) -> value?.let { add(Part.Field(name, it.toString())) } }
        privacy?.let { add(Part.Field("source[privacy]", it.wire)) }
        language?.let { add(Part.Field("source[language]", it)) }
    }

    private fun flags(): List<Pair<String, Boolean?>> = listOf(
        "locked" to locked,
        "discoverable" to discoverable,
        "indexable" to indexable,
        "bot" to bot,
        "source[sensitive]" to sensitive,
    )
}

public object CredentialEndpoints {
    private fun account(endpoint: Endpoint): ApiRequest<Account> =
        request(endpoint, AccountDto.serializer()) { it.toDomain() }

    /** A form when no picture goes, multipart with one: the pictures, names and fields in one request. */
    public fun update(changes: CredentialsUpdate): ApiRequest<Account> {
        val parts = changes.parts()
        val body = if (parts.any { it is Part.FileContent }) {
            Body.Multipart(parts)
        } else {
            Body.Form(parts.filterIsInstance<Part.Field>().map { QueryItem(it.name, it.value) })
        }
        return account(Endpoint("api/v1/accounts/update_credentials", HttpMethod.PATCH, body = body))
    }

    /** Removes the picture; the server falls back to the initials it draws. */
    public fun deleteAvatar(): ApiRequest<Account> = account(Endpoint("api/v1/profile/avatar", HttpMethod.DELETE))

    public fun deleteHeader(): ApiRequest<Account> = account(Endpoint("api/v1/profile/header", HttpMethod.DELETE))
}

/** The hashtags the reader features on their own profile. */
public object FeaturedTagEndpoints {
    public fun all(): ApiRequest<List<FeaturedTag>> =
        listRequest(Endpoint("api/v1/featured_tags"), FeaturedTagDto.serializer()) { it.toDomain() }

    /** Names of hashtags the reader posts with and has not featured. */
    public fun suggestions(): ApiRequest<List<String>> =
        request(Endpoint("api/v1/featured_tags/suggestions"), NamesSerializer) { it }

    public fun create(name: String): ApiRequest<FeaturedTag> = request(
        Endpoint("api/v1/featured_tags", HttpMethod.POST, body = Body.Form(listOf(QueryItem("name", name)))),
        FeaturedTagDto.serializer(),
    ) { it.toDomain() }

    public fun delete(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/featured_tags/$id", HttpMethod.DELETE))
}

/**
 * The account itself as Nextcloud holds it. These are Nextcloud-session routes: they go out with the
 * app password and `OCS-APIRequest: true`, and are refused on the device without one.
 */
public object SocialAccountEndpoints {
    /**
     * Deletes the reader's Social account and keeps their Nextcloud one: the posts, the follows, and
     * a `Delete` to every server that knew them. It cannot be undone. [handle] has to be the handle
     * being deleted, deliberately not a password, since an account signed in through SSO has none.
     */
    public fun delete(handle: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/account/delete",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("confirm", handle))),
            authentication = Authentication.NextcloudSession,
        ),
    )
}

/** Applications that hold a token for the reader's account; a Nextcloud-session route. */
public object AuthorizedAppEndpoints {
    public fun all(): ApiRequest<List<AuthorizedApp>> = listRequest(
        Endpoint("api/v1/authorized_apps", authentication = Authentication.NextcloudSession),
        AuthorizedAppDto.serializer(),
    ) { it.toDomain() }

    public fun revoke(id: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/authorized_apps/$id", HttpMethod.DELETE, authentication = Authentication.NextcloudSession),
    )
}

/** Pixelfed's portfolio page, served by Nextcloud Social. */
public object PortfolioEndpoints {
    public fun own(): ApiRequest<PortfolioSettings> =
        request(Endpoint("api/v1.1/portfolio"), PortfolioDto.serializer()) { it.toSettings() }

    public fun save(settings: PortfolioSettings): ApiRequest<PortfolioSettings> {
        val fields = listOf(
            QueryItem("active", settings.active.asFlag()),
            QueryItem("title", settings.title),
            QueryItem("intro", settings.intro),
            QueryItem("layout", settings.layout.wire),
            QueryItem("source", settings.source.wire),
            QueryItem("show_captions", settings.showCaptions.asFlag()),
            QueryItem("show_places", settings.showPlaces.asFlag()),
            QueryItem("show_dates", settings.showDates.asFlag()),
            QueryItem("show_avatar", settings.showAvatar.asFlag()),
        ) + queryOf("collection_id", settings.collectionId)
        return request(
            Endpoint("api/v1.1/portfolio", HttpMethod.POST, body = Body.Form(fields)),
            PortfolioDto.serializer(),
        ) {
            it.toSettings()
        }
    }

    /** The published page; anybody with the address may read it. */
    public fun page(handle: String): ApiRequest<PortfolioPage> =
        request(Endpoint("api/v1.1/portfolio/$handle"), PortfolioDto.serializer()) { it.toPage() }

    /** The reader's own albums, for the picture-source picker. */
    public fun ownCollections(): ApiRequest<List<MediaCollection>> =
        listRequest(Endpoint("api/v1.1/collections/self"), CollectionDto.serializer()) { it.toDomain() }
}
