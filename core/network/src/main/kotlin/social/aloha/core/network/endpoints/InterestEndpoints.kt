// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.io.File
import social.aloha.core.model.HeldPostsPage
import social.aloha.core.model.InterestsState
import social.aloha.core.model.Status
import social.aloha.core.model.SubscriptionEntry
import social.aloha.core.model.SubscriptionFeed
import social.aloha.core.model.WeeklyRecap
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.Part
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.dto.HeldPostsPageDto
import social.aloha.core.network.dto.InterestsStateDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.SubscriptionEntryDto
import social.aloha.core.network.dto.SubscriptionFeedDto
import social.aloha.core.network.dto.TakeoutResultDto
import social.aloha.core.network.dto.WeeklyRecapDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

private fun Boolean.asFlag(): String = if (this) "1" else "0"

/** The reader's hashtag interests on Nextcloud Social. Every change answers with the new state. */
public object InterestEndpoints {
    private fun state(endpoint: Endpoint): ApiRequest<InterestsState> =
        request(endpoint, InterestsStateDto.serializer()) { it.toDomain() }

    public fun state(): ApiRequest<InterestsState> = state(Endpoint("api/v1/interests"))

    public fun add(tag: String): ApiRequest<InterestsState> =
        state(Endpoint("api/v1/interests", HttpMethod.POST, body = Body.Form(listOf(QueryItem("tag", tag)))))

    public fun remove(tag: String): ApiRequest<InterestsState> =
        state(Endpoint("api/v1/interests/$tag", HttpMethod.DELETE))

    public fun move(tag: String, position: Int): ApiRequest<InterestsState> = state(
        Endpoint(
            "api/v1/interests/$tag/move",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("position", position.toString()))),
        ),
    )

    public fun pin(tag: String): ApiRequest<InterestsState> =
        state(Endpoint("api/v1/interests/$tag/pin", HttpMethod.POST))

    public fun unpin(tag: String): ApiRequest<InterestsState> =
        state(Endpoint("api/v1/interests/$tag/unpin", HttpMethod.POST))

    public fun reset(): ApiRequest<InterestsState> = state(Endpoint("api/v1/interests/reset", HttpMethod.POST))

    /**
     * Changes only the settings passed. An empty [languages] list still goes out as a field, since
     * leaving it out reads as "leave the languages alone".
     */
    public fun settings(
        learning: Boolean? = null,
        paused: Boolean? = null,
        languages: List<String>? = null,
        noticeAcknowledged: Boolean? = null,
    ): ApiRequest<InterestsState> {
        val fields = queryOf("learning", learning?.asFlag()) + queryOf("paused", paused?.asFlag()) +
            languages?.let {
                if (it.isEmpty()) listOf(QueryItem("languages", "")) else repeatedQuery("languages", it)
            }.orEmpty() +
            queryOf("noticeAcknowledged", noticeAcknowledged?.asFlag())
        return state(Endpoint("api/v1/interests/settings", HttpMethod.PUT, body = Body.Form(fields)))
    }

    /** Reading signals, batched. The server answers 204 whether or not it is learning. */
    public fun signals(events: List<String>): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/interests/signals", HttpMethod.POST, body = Body.Form(repeatedQuery("events", events))),
    )

    /** "Show fewer like this". */
    public fun fewerLikeThis(statusId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/interests/less/$statusId", HttpMethod.POST))

    public fun undoFewerLikeThis(statusId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/interests/less/$statusId", HttpMethod.DELETE))

    /** The interests feed itself, paged by `max_id` with a `Link` header. */
    public fun timeline(
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> = listRequest(
        Endpoint("api/v1/timelines/interests", query = Paging.pageItems(limit, anchor)),
        StatusDto.serializer(),
    ) {
        it.toDomain()
    }
}

/** Feeds outside the fediverse that the server reads for the viewer; entries link out. */
public object SubscriptionEndpoints {
    private const val DEFAULT_LIMIT = 40

    public fun feeds(): ApiRequest<List<SubscriptionFeed>> = request(
        Endpoint("api/v1/subscriptions"),
        ListOrMemberSerializer(SubscriptionFeedDto.serializer(), "feeds"),
    ) { list ->
        list.map { it.toDomain() }
    }

    /** A feed URL or a YouTube channel link; the server works out which. */
    public fun follow(url: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/subscriptions", HttpMethod.POST, body = Body.Form(listOf(QueryItem("url", url)))))

    public fun unfollow(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/subscriptions/$id", HttpMethod.DELETE))

    /** Newest first. The route sends no `Link` header, so the caller pages by the last entry's id. */
    public fun timeline(limit: Int = DEFAULT_LIMIT, maxId: String? = null): ApiRequest<List<SubscriptionEntry>> =
        request(
            Endpoint(
                "api/v1/subscriptions/timeline",
                query = listOf(Paging.limitItem(limit)) + queryOf("max_id", maxId),
            ),
            ListOrMemberSerializer(SubscriptionEntryDto.serializer(), "items"),
        ) { list -> list.map { it.toDomain() } }

    /** Google Takeout's `subscriptions.csv`; answers with how many channels it subscribed to. */
    public fun importTakeout(csv: File, fileName: String = "subscriptions.csv"): ApiRequest<Int> = request(
        Endpoint(
            "api/v1/subscriptions/takeout",
            HttpMethod.POST,
            body = Body.Multipart(listOf(Part.FileContent("file", csv, fileName, "text/csv"))),
        ),
        TakeoutResultDto.serializer(),
    ) { it.subscribed }
}

/**
 * The reader's own past posts. These routes take a Nextcloud session, not the Social token: a
 * bearer token answers 401, so they go out with the app password.
 */
public object MemoryEndpoints {
    /** Posts of the reader's from this calendar day in earlier years. */
    public fun onThisDay(): ApiRequest<List<Status>> = listRequest(
        Endpoint("api/v1/memories/on_this_day", authentication = Authentication.NextcloudSession),
        StatusDto.serializer(),
    ) { it.toDomain() }

    public fun recap(): ApiRequest<WeeklyRecap> = request(
        Endpoint("api/v1/memories/recap", authentication = Authentication.NextcloudSession),
        WeeklyRecapDto.serializer(),
    ) { it.toDomain() }

    public fun setRecap(enabled: Boolean): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/memories/recap",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("enabled", enabled.asFlag()))),
            authentication = Authentication.NextcloudSession,
        ),
    )
}

/** The reader's own posts waiting for a moderator; a Nextcloud-session route like the memories. */
public object ReviewEndpoints {
    public fun held(): ApiRequest<HeldPostsPage> = request(
        Endpoint("api/v1/review", authentication = Authentication.NextcloudSession),
        HeldPostsPageDto.serializer(),
    ) { it.toDomain() }

    /** Withdraws the post: deleted, and no moderation record is made. */
    public fun withdraw(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/review/$id", HttpMethod.DELETE, authentication = Authentication.NextcloudSession))
}

/** The reader's side of administrators' announcements; the list itself is `SafetyEndpoints.announcements`. */
public object AnnouncementEndpoints {
    public fun dismiss(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/announcements/$id/dismiss", HttpMethod.POST))

    /** The emoji goes in the path, percent-encoded on the way out. */
    public fun react(id: String, emoji: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/announcements/$id/reactions/$emoji", HttpMethod.PUT))

    public fun unreact(id: String, emoji: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/announcements/$id/reactions/$emoji", HttpMethod.DELETE))
}
