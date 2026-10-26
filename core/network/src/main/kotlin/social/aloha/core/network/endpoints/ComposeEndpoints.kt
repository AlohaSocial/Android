// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.io.File
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import social.aloha.core.model.Announcement
import social.aloha.core.model.ContinueWatchingItem
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.ScheduledStatus
import social.aloha.core.model.Status
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.Part
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.dto.AnnouncementDto
import social.aloha.core.network.dto.ContinueWatchingEntry
import social.aloha.core.network.dto.MediaAttachmentDto
import social.aloha.core.network.dto.ScheduledStatusDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

public object ComposeEndpoints {
    /** Posts [draft], carrying its idempotency key, which makes a retry after a timeout safe. */
    public fun post(draft: StatusPost): ApiRequest<Status> = request(
        Endpoint(
            "api/v1/statuses",
            HttpMethod.POST,
            body = Body.Form(draft.formItems()),
            idempotencyKey = draft.idempotencyKey,
        ),
        StatusDto.serializer(),
    ) { it.toDomain() }

    /**
     * Posts [draft] at its `scheduledAt`; the server answers with the scheduled post, not a status.
     * The idempotency key makes a retry after a timeout as safe as it is for [post].
     */
    public fun schedule(draft: StatusPost): ApiRequest<ScheduledStatus> = request(
        Endpoint(
            "api/v1/statuses",
            HttpMethod.POST,
            body = Body.Form(draft.formItems()),
            idempotencyKey = draft.idempotencyKey,
        ),
        ScheduledStatusDto.serializer(),
    ) { it.toDomain() }

    /** Edits a known status; it cannot duplicate, so no idempotency key is sent. */
    public fun edit(id: String, draft: StatusPost): ApiRequest<Status> = request(
        // a warning left out is kept as it was; only an empty one takes it off
        Endpoint(
            "api/v1/statuses/$id",
            HttpMethod.PUT,
            body = Body.Form(draft.copy(spoilerText = draft.spoilerText.orEmpty()).formItems()),
        ),
        StatusDto.serializer(),
    ) {
        it.toDomain()
    }

    /** Scheduled statuses; the route sends no `Link` header. */
    public fun scheduled(): ApiRequest<List<ScheduledStatus>> =
        listRequest(Endpoint("api/v1/scheduled_statuses"), ScheduledStatusDto.serializer()) { it.toDomain() }

    public fun deleteScheduled(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/scheduled_statuses/$id", HttpMethod.DELETE))

    /** Moves scheduled post [id] to [at]. */
    public fun reschedule(id: String, at: Instant): ApiRequest<ScheduledStatus> = request(
        Endpoint(
            "api/v1/scheduled_statuses/$id",
            HttpMethod.PUT,
            body = Body.Form(listOf(QueryItem("scheduled_at", at.toString()))),
        ),
        ScheduledStatusDto.serializer(),
    ) { it.toDomain() }
}

public object MediaEndpoints {
    /** Uploads one file; v2 first, v1 only after a 404. The file is streamed from disk. */
    public fun upload(
        file: File,
        fileName: String,
        mimeType: String,
        description: String?,
        v2: Boolean = true,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): ApiRequest<MediaAttachment> {
        val parts = buildList<Part> {
            add(Part.FileContent("file", file, fileName, mimeType, onProgress))
            description?.takeIf { it.isNotEmpty() }?.let { add(Part.Field("description", it)) }
        }
        return attachment(
            Endpoint(if (v2) "api/v2/media" else "api/v1/media", HttpMethod.POST, body = Body.Multipart(parts)),
        )
    }

    /**
     * An uploaded attachment as the server has it now: a video still being processed has no `url`
     * yet, and a post may not attach it until it has.
     */
    public fun media(id: String): ApiRequest<MediaAttachment> = attachment(Endpoint("api/v1/media/$id"))

    public fun updateDescription(id: String, description: String): ApiRequest<MediaAttachment> = attachment(
        Endpoint(
            "api/v1/media/$id",
            HttpMethod.PUT,
            body = Body.Form(listOf(QueryItem("description", description))),
        ),
    )

    /**
     * Attaches a file the viewer already has in Nextcloud, so it never travels to the phone and back.
     * A traversal or a folder answers 422. A Nextcloud-session route: reading the reader's files is
     * more than a bearer token is granted, which Social answers with 403 since 0.26.215.
     */
    public fun fromNextcloudFile(path: String, description: String?): ApiRequest<MediaAttachment> = attachment(
        Endpoint(
            "api/v1/media/from-file",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("path", path)) + queryOf("description", description)),
            authentication = Authentication.NextcloudSession,
        ),
    )

    /**
     * The focal point as Mastodon defines it: `x,y` in −1…1 with the origin in the centre, what a
     * cropped preview keeps in frame. Formatted without the device locale, which could write `0,50`.
     */
    public fun updateFocus(id: String, x: Double, y: Double, description: String? = null): ApiRequest<MediaAttachment> {
        val focus = String.format(Locale.ROOT, "%.2f,%.2f", x.coerceIn(-1.0, 1.0), y.coerceIn(-1.0, 1.0))
        val fields = listOf(QueryItem("focus", focus)) + queryOf("description", description)
        return attachment(Endpoint("api/v1/media/$id", HttpMethod.PUT, body = Body.Form(fields)))
    }

    /** Attaches a picture from the instance's own GIF library, the way an upload would have. */
    public fun fromGif(slug: String, description: String?): ApiRequest<MediaAttachment> = attachment(
        Endpoint(
            "api/v1/media/from-gif",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("slug", slug)) + queryOf("description", description)),
        ),
    )

    private fun attachment(endpoint: Endpoint): ApiRequest<MediaAttachment> =
        request(endpoint, MediaAttachmentDto.serializer()) { it.toDomain() }
}

/** Nextcloud Social's watch positions: never federated, never shown to anybody else. */
public object VideoEndpoints {
    /**
     * Whether the server holds the place now: Nextcloud Social says `stored: false` where it could not write
     * it, and answered `[]` before it said either.
     */
    public fun reportWatched(statusId: String, positionSeconds: Double, durationSeconds: Double): ApiRequest<Boolean> =
        request(
            Endpoint(
                "api/v1/statuses/$statusId/watched",
                HttpMethod.POST,
                body = Body.Form(
                    listOf(
                        QueryItem("position", positionSeconds.toInt().toString()),
                        QueryItem("duration", durationSeconds.toInt().toString()),
                    ),
                ),
            ),
            JsonElement.serializer(),
        ) { (it as? JsonObject)?.get("stored")?.jsonPrimitive?.booleanOrNull != false }

    public fun forgetWatched(statusId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/statuses/$statusId/watched", HttpMethod.DELETE))

    /** Counts a view of [statusId], once the reader has played a few seconds of it; Nextcloud Social's own. */
    public fun countView(statusId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/statuses/$statusId/view", HttpMethod.POST))

    /** Where the reader got to in the videos they have not finished; an entry that names no post is dropped. */
    public fun continueWatching(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<ContinueWatchingItem>> = request(
        Endpoint("api/v1/videos/continue", query = listOf(Paging.limitItem(limit, CONTINUE_WATCHING_LIMIT))),
        LossyListSerializer(ContinueWatchingEntry),
    ) { items -> items.mapNotNull { it.toDomain() } }

    private const val CONTINUE_WATCHING_LIMIT = 40
}

public object SafetyEndpoints {
    /** Reports an account, optionally with statuses and the rules they break. */
    public fun report(report: ReportDraft): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/reports",
            HttpMethod.POST,
            body = Body.Form(
                listOf(
                    QueryItem("account_id", report.accountId),
                    QueryItem("comment", report.comment),
                    QueryItem("forward", report.forward.toString()),
                    QueryItem("category", report.category),
                ) + repeatedQuery("status_ids", report.statusIds) + repeatedQuery("rule_ids", report.ruleIds),
            ),
        ),
    )

    public fun announcements(): ApiRequest<List<Announcement>> =
        listRequest(Endpoint("api/v1/announcements"), AnnouncementDto.serializer()) { it.toDomain() }
}

public data class ReportDraft(
    val accountId: String,
    val comment: String,
    val category: String,
    val statusIds: List<String> = emptyList(),
    val ruleIds: List<String> = emptyList(),
    val forward: Boolean = false,
)
