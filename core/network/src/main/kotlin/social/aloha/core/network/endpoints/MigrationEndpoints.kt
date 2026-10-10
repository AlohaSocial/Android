// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.io.File
import kotlinx.serialization.builtins.serializer
import social.aloha.core.model.AccountStatistics
import social.aloha.core.model.MigrationAnnouncement
import social.aloha.core.model.MigrationListKind
import social.aloha.core.model.MigrationLookup
import social.aloha.core.model.MigrationReport
import social.aloha.core.model.VideoChannel
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.Part
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.dto.MigrationAnnouncementDto
import social.aloha.core.network.dto.MigrationLookupSerializer
import social.aloha.core.network.dto.MigrationReportSerializer
import social.aloha.core.network.dto.StatisticsDto
import social.aloha.core.network.dto.VideoChannelDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.flagQuery
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request

private fun Boolean.asFlag(): String = if (this) "1" else "0"

private const val ZIP = "application/zip"
private const val CSV = "text/csv"

/**
 * The account to keep or take to another server. Nextcloud-session routes, and downloads rather than
 * JSON, so they are for `ApiClient.download`.
 */
public object ExportEndpoints {
    /** The whole account as a Mastodon-style archive: a zip of the actor, its posts and its media. */
    public fun archive(): Endpoint =
        Endpoint("api/v1/migration/export", authentication = Authentication.NextcloudSession)

    /** One of the account's lists as the CSV other servers import, without the whole archive. */
    public fun list(kind: MigrationListKind): Endpoint =
        Endpoint("api/v1/migration/export/${kind.wire}", authentication = Authentication.NextcloudSession)
}

/** Taking the account somewhere else, or bringing one here. */
public object MigrationEndpoints {
    private fun report(endpoint: Endpoint): ApiRequest<MigrationReport> =
        request(endpoint, MigrationReportSerializer) { it }

    private fun aliases(endpoint: Endpoint): ApiRequest<List<String>> =
        request(endpoint, ListOrMemberSerializer(String.serializer(), "aliases")) { it }

    private fun upload(
        path: String,
        file: File,
        fileName: String,
        mimeType: String,
        query: List<QueryItem> = emptyList(),
    ) = Endpoint(
        path,
        HttpMethod.POST,
        query = query,
        body = Body.Multipart(listOf(Part.FileContent("file", file, fileName, mimeType))),
    )

    /** The whole account from another server's archive: profile, posts, media, relationships. */
    public fun importArchive(archive: File, fileName: String): ApiRequest<MigrationReport> =
        report(upload("api/v1/migration/import", archive, fileName, ZIP))

    public fun importList(kind: MigrationListKind, csv: File, fileName: String): ApiRequest<MigrationReport> =
        report(upload("api/v1/migration/${kind.importPath}", csv, fileName, CSV))

    public fun importPosts(archive: File, fileName: String, fetchMedia: Boolean): ApiRequest<MigrationReport> = report(
        upload("api/v1/migration/posts", archive, fileName, ZIP, listOf(QueryItem("fetch_media", fetchMedia.asFlag()))),
    )

    /** One video, by its address on another server. */
    public fun importVideo(url: String, fetchMedia: Boolean): ApiRequest<MigrationReport> = report(
        Endpoint(
            "api/v1/migration/video",
            HttpMethod.POST,
            body = Body.Form(listOf(QueryItem("url", url), QueryItem("fetch_media", fetchMedia.asFlag()))),
        ),
    )

    /** Candidate handles pulled out of an Instagram archive. */
    public fun instagramPeople(archive: File, fileName: String): ApiRequest<List<String>> = request(
        upload("api/v1/migration/people", archive, fileName, ZIP),
        ListOrMemberSerializer(String.serializer(), "handles"),
    ) { it }

    /** Which of [handles] exist somewhere. */
    public fun findPeople(handles: List<String>): ApiRequest<MigrationLookup> = request(
        Endpoint("api/v1/migration/people/find", HttpMethod.POST, body = Body.Form(repeatedQuery("handles", handles))),
        MigrationLookupSerializer,
    ) { it }

    /** What to paste into the old server's "move to" box. */
    public fun announcement(): ApiRequest<MigrationAnnouncement> =
        request(Endpoint("api/v1/migration/announcement"), MigrationAnnouncementDto.serializer()) { it.toDomain() }

    public fun aliases(): ApiRequest<List<String>> = aliases(Endpoint("api/v1/migration/aliases"))

    public fun addAlias(alias: String): ApiRequest<List<String>> = aliases(
        Endpoint("api/v1/migration/aliases", HttpMethod.POST, body = Body.Form(listOf(QueryItem("alias", alias)))),
    )

    /** The alias rides in the query: a DELETE with a body is dropped by some of the proxies a Nextcloud sits behind. */
    public fun removeAlias(alias: String): ApiRequest<List<String>> =
        aliases(Endpoint("api/v1/migration/aliases", HttpMethod.DELETE, query = listOf(QueryItem("alias", alias))))
}

/** The reader's own numbers. A Nextcloud-session route: a bearer token answers 401. */
public object StatisticsEndpoints {
    /** The same numbers over [days] (0 for everything) as a CSV; a download, so `ApiClient.download`. */
    public fun export(days: Int): Endpoint = Endpoint(
        "api/v1/statistics/export",
        query = listOf(QueryItem("days", days.toString())),
        authentication = Authentication.NextcloudSession,
    )

    /** [days] 0 means everything; [fresh] bypasses the server's cache. */
    public fun overview(days: Int, fresh: Boolean = false): ApiRequest<AccountStatistics> = request(
        Endpoint(
            "api/v1/statistics",
            query = listOf(QueryItem("days", days.toString())) + flagQuery("fresh", fresh),
            authentication = Authentication.NextcloudSession,
        ),
        StatisticsDto.serializer(),
    ) { it.toDomain() }
}

/** The reader's video channels, PeerTube's `Group`. A Nextcloud-session route; every answer is `{channels: [...]}`. */
public object ChannelEndpoints {
    private fun channels(endpoint: Endpoint): ApiRequest<List<VideoChannel>> =
        request(endpoint, ListOrMemberSerializer(VideoChannelDto.serializer(), "channels")) { list ->
            list.map { it.toDomain() }
        }

    public fun all(): ApiRequest<List<VideoChannel>> =
        channels(Endpoint("api/v1/channels", authentication = Authentication.NextcloudSession))

    public fun create(handle: String, name: String, description: String): ApiRequest<List<VideoChannel>> = channels(
        Endpoint(
            "api/v1/channels",
            HttpMethod.POST,
            body = Body.Form(
                listOf(QueryItem("handle", handle), QueryItem("name", name), QueryItem("description", description)),
            ),
            authentication = Authentication.NextcloudSession,
        ),
    )

    public fun update(id: String, name: String, description: String): ApiRequest<List<VideoChannel>> = channels(
        Endpoint(
            "api/v1/channels/$id",
            HttpMethod.PUT,
            body = Body.Form(listOf(QueryItem("name", name), QueryItem("description", description))),
            authentication = Authentication.NextcloudSession,
        ),
    )
}
