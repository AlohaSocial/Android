// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.CharacterCount
import social.aloha.core.model.CollectionDraft
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.Status
import social.aloha.core.model.Story
import social.aloha.core.model.StoryCarousel
import social.aloha.core.model.StoryReaction
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.CollectionDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.StoryCarouselSerializer
import social.aloha.core.network.dto.StoryDto
import social.aloha.core.network.dto.StoryReactionDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

private fun stories(endpoint: Endpoint): ApiRequest<List<Story>> =
    listRequest(endpoint, StoryDto.serializer()) { it.toDomain() }

private fun carousel(endpoint: Endpoint): ApiRequest<StoryCarousel> =
    request(endpoint, StoryCarouselSerializer) { it.toDomain() }

/**
 * Stories, Pixelfed's feature as Nextcloud Social serves it. Past the Mastodon-ish `api/v1/stories`
 * routes, Pixelfed names a story in a `sid` parameter rather than in the path: its v1.1 and v1.2
 * routes are used verbatim, since a path-style `/api/v1/stories/{id}/…` route does not exist and 404s.
 */
public object StoryEndpoints {
    private const val MAXIMUM_REACTION = 20
    private const val MAXIMUM_CAPTION = 500
    private const val MINIMUM_DURATION = 3
    private const val MAXIMUM_DURATION = 30

    /** The rail, as a flat array on Nextcloud Social. */
    public fun carousel(): ApiRequest<StoryCarousel> = carousel(Endpoint("api/v1/stories/carousel"))

    /** Pixelfed's v1.2 rail: `{self, nodes}`, one node per account. */
    public fun carouselV2(): ApiRequest<StoryCarousel> = carousel(Endpoint("api/v1.2/stories/carousel"))

    public fun own(): ApiRequest<List<Story>> = stories(Endpoint("api/v1/stories/self"))

    public fun forAccount(id: String): ApiRequest<List<Story>> = stories(Endpoint("api/v1/accounts/$id/stories"))

    /** Posts an uploaded attachment as a story. The server clamps the duration to 3–30 s; so does this. */
    public fun post(mediaId: String, caption: String?, durationSeconds: Int): ApiRequest<Unit> {
        val fields = listOf(
            QueryItem("media_id", mediaId),
            QueryItem("duration", durationSeconds.coerceIn(MINIMUM_DURATION, MAXIMUM_DURATION).toString()),
        ) + queryOf("caption", caption?.let { CharacterCount.prefix(it, MAXIMUM_CAPTION) })
        return unitRequest(Endpoint("api/v1/stories", HttpMethod.POST, body = Body.Form(fields)))
    }

    public fun markSeen(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/stories/$id/seen", HttpMethod.POST))

    public fun delete(id: String): ApiRequest<Unit> = unitRequest(Endpoint("api/v1/stories/$id", HttpMethod.DELETE))

    /** Who watched one of the viewer's own stories. */
    public fun viewers(id: String): ApiRequest<List<Account>> = listRequest(
        Endpoint("api/v1.2/stories/viewers", query = listOf(QueryItem("sid", id))),
        AccountDto.serializer(),
    ) { it.toDomain() }

    /** An emoji, at most 20 characters, sent to the poster and nobody else. */
    public fun react(id: String, reaction: String): ApiRequest<Unit> = storyForm(
        "api/v1.2/stories/react",
        QueryItem("sid", id),
        QueryItem("reaction", CharacterCount.prefix(reaction, MAXIMUM_REACTION)),
    )

    /** A reply, delivered to the poster as a direct message rather than federated as a post. */
    public fun comment(id: String, caption: String): ApiRequest<Unit> = storyForm(
        "api/v1.2/stories/comment",
        QueryItem("sid", id),
        QueryItem("caption", CharacterCount.prefix(caption, MAXIMUM_CAPTION)),
    )

    /** Reactions and replies to one of the viewer's own stories, sent bare or as `{reactions: [...]}`. */
    public fun reactions(id: String): ApiRequest<List<StoryReaction>> = request(
        Endpoint("api/v1.2/stories/reactions", query = listOf(QueryItem("sid", id))),
        ListOrMemberSerializer(StoryReactionDto.serializer(), "reactions"),
    ) { list -> list.mapNotNull { it.toDomain() } }

    /** Ends one of the viewer's own stories before its day is up; the route Pixelfed's own app calls. */
    public fun selfExpire(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1.1/stories/self-expire/$id", HttpMethod.POST))

    /** People the writer may name in a story's caption. */
    public fun mentionAutocomplete(query: String): ApiRequest<List<Account>> = listRequest(
        Endpoint("api/v1.2/stories/mention-autocomplete", query = listOf(QueryItem("q", query))),
        AccountDto.serializer(),
    ) { it.toDomain() }

    private fun storyForm(path: String, vararg fields: QueryItem): ApiRequest<Unit> =
        unitRequest(Endpoint(path, HttpMethod.POST, body = Body.Form(fields.toList())))
}

/** Pixelfed albums, read and written where the server supports them. */
public object CollectionEndpoints {
    private fun collections(endpoint: Endpoint): ApiRequest<List<MediaCollection>> =
        listRequest(endpoint, CollectionDto.serializer()) { it.toDomain() }

    private fun collection(endpoint: Endpoint): ApiRequest<MediaCollection> =
        request(endpoint, CollectionDto.serializer()) { it.toDomain() }

    public fun all(): ApiRequest<List<MediaCollection>> = collections(Endpoint("api/v1/collections"))

    public fun forAccount(id: String): ApiRequest<List<MediaCollection>> =
        collections(Endpoint("api/v1/accounts/$id/collections"))

    public fun collection(id: String): ApiRequest<MediaCollection> = collection(Endpoint("api/v1/collections/$id"))

    public fun items(id: String): ApiRequest<List<Status>> =
        listRequest(Endpoint("api/v1/collections/$id/items"), StatusDto.serializer()) { it.toDomain() }

    public fun create(draft: CollectionDraft): ApiRequest<MediaCollection> =
        collection(Endpoint("api/v1/collections", HttpMethod.POST, body = draft.form()))

    public fun update(id: String, draft: CollectionDraft): ApiRequest<MediaCollection> =
        collection(Endpoint("api/v1/collections/$id", HttpMethod.PUT, body = draft.form()))

    public fun delete(id: String): ApiRequest<Unit> = unitRequest(Endpoint("api/v1/collections/$id", HttpMethod.DELETE))

    public fun addItem(id: String, statusId: String): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/collections/$id/items",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("status_id", statusId))),
        ),
    )

    /**
     * The status goes in the path: the server's route is `/items/{status_id}`, so a DELETE to
     * `/items` carrying `status_id` in a body matches nothing and 404s.
     */
    public fun removeItem(id: String, statusId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/collections/$id/items/$statusId", HttpMethod.DELETE))

    private fun CollectionDraft.form(): Body = Body.Form(
        listOf(QueryItem("title", title), QueryItem("description", description), QueryItem("visibility", visibility)),
    )
}
