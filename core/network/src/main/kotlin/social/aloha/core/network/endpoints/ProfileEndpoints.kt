// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.AccountList
import social.aloha.core.model.FamiliarFollowers
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.Relationship
import social.aloha.core.model.Status
import social.aloha.core.model.TaggedPerson
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.dto.AccountListDto
import social.aloha.core.network.dto.FamiliarFollowersDto
import social.aloha.core.network.dto.FeaturedTagDto
import social.aloha.core.network.dto.ProfileHighlightsDto
import social.aloha.core.network.dto.RelationshipDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.TaggedPersonSerializer
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * What a profile shows beyond the account entity: Nextcloud Social's highlights, featured tags,
 * pinned posts, mutual followers, private notes and Pixelfed's tagged photos. Endorsing and removing
 * a follower are `AccountEndpoints.action`.
 */
public object ProfileEndpoints {
    private const val PINNED_LIMIT = 5
    private const val TAGGED_LIMIT = 40

    /** Twelve weeks of posting, for a local account. */
    public fun highlights(id: String): ApiRequest<ProfileHighlights> =
        request(Endpoint("api/v1/accounts/$id/highlights"), ProfileHighlightsDto.serializer()) { it.toDomain() }

    /** Up to ten hashtags an account features at the top of its profile. */
    public fun featuredTags(id: String): ApiRequest<List<FeaturedTag>> =
        listRequest(Endpoint("api/v1/accounts/$id/featured_tags"), FeaturedTagDto.serializer()) { it.toDomain() }

    /** Up to five pinned posts. */
    public fun pinnedStatuses(id: String): ApiRequest<List<Status>> = listRequest(
        Endpoint(
            "api/v1/accounts/$id/statuses",
            query = listOf(QueryItem("pinned", "true"), Paging.limitItem(PINNED_LIMIT)),
        ),
        StatusDto.serializer(),
    ) { it.toDomain() }

    /** For each account asked about, the people the reader follows who also follow it. */
    public fun familiarFollowers(ids: List<String>): ApiRequest<List<FamiliarFollowers>> = listRequest(
        Endpoint("api/v1/accounts/familiar_followers", query = repeatedQuery("id", ids)),
        FamiliarFollowersDto.serializer(),
    ) { it.toDomain() }

    /** A private note about an account, seen by nobody else. */
    public fun setNote(id: String, comment: String): ApiRequest<Relationship> = request(
        Endpoint("api/v1/accounts/$id/note", HttpMethod.POST, body = Body.Form(listOf(QueryItem("comment", comment)))),
        RelationshipDto.serializer(),
    ) { it.toDomain() }

    /** The reader's lists that contain an account. */
    public fun listsContaining(id: String): ApiRequest<List<AccountList>> =
        listRequest(Endpoint("api/v1/accounts/$id/lists"), AccountListDto.serializer()) { it.toDomain() }

    /** Photos an account is tagged in (Pixelfed). */
    public fun tagged(
        id: String,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> = listRequest(
        Endpoint(
            "api/v1.1/accounts/$id/tagged",
            query = Paging.pageItems(limit.coerceAtMost(TAGGED_LIMIT), anchor),
        ),
        StatusDto.serializer(),
    ) { it.toDomain() }

    /** Tags people in the reader's own photo; handles go without the `@`. Answers with the people now tagged. */
    public fun tagPeople(statusId: String, handles: List<String>): ApiRequest<List<TaggedPerson>> = request(
        Endpoint(
            "api/v1.1/compose/tag",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("status_id", statusId)) + repeatedQuery("accounts", handles)),
        ),
        ListOrMemberSerializer(TaggedPersonSerializer, "tagged_people"),
    ) { it }

    /** Takes the reader's own tag off somebody's photo. */
    public fun untagMe(statusId: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1.1/compose/tag/untagme",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("status_id", statusId))),
        ),
    )
}
