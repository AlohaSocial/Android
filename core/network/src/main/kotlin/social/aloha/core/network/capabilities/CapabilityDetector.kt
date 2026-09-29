// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.capabilities

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import social.aloha.core.model.AccessToken
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.NextcloudTheme
import social.aloha.core.model.NodeInfo
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Authentication
import social.aloha.core.network.Endpoint
import social.aloha.core.network.QueryItem
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.RequestExecutor
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.dto.OcsCapabilitiesDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.request
import social.aloha.core.network.resolve
import social.aloha.core.network.unitRequest

/**
 * Works out what one server can do, at sign-in and every 24 hours after. A capability that cannot be
 * determined is absent: every probe tolerates failure, and failure means false.
 *
 * A route counts as present when it answers 2xx, 401 or 403 (it exists, the token lacks a scope) and
 * as absent on 404. `only_video` and `only_news` are Nextcloud Social's own and are trusted only
 * when NodeInfo names Nextcloud Social: Mastodon ignores unknown parameters, so their acceptance
 * proves nothing there. Pixelfed's `/api/v2/config` `features`, which Nextcloud Social serves
 * publicly, is taken at its word where it speaks. `hls_url`, reactions and quotes have nothing
 * announcing them and are latched later, on first sighting in an entity.
 */
public class CapabilityDetector internal constructor(executor: RequestExecutor, private val now: () -> Instant) {
    public constructor(
        http: OkHttpClient,
        rateLimiter: RateLimiter,
        ioDispatcher: CoroutineDispatcher,
        now: () -> Instant,
    ) :
        this(RequestExecutor(http, rateLimiter, ioDispatcher), now)

    private val executor = executor.withCallTimeout(PROBE_SECONDS)

    public suspend fun detect(
        apiBase: HttpUrl,
        token: AccessToken?,
        instance: InstanceDescription,
        nodeInfo: NodeInfo?,
    ): ServerCapabilities {
        val nextcloud = nodeInfo?.isNextcloudSocial == true
        val found = findings(apiBase, token, nextcloud)
        val announcesFourThree = (instance.mastodonApiVersion ?: 0) >= MASTODON_4_3_API ||
            listOf("4.3", "4.4", "4.5").any { instance.version.contains(it) }
        return ServerCapabilities(
            apiBase = apiBase.toString(),
            softwareName = nodeInfo?.softwareName?.lowercase().orEmpty(),
            softwareVersion = nodeInfo?.softwareVersion ?: instance.version,
            mastodonApiVersion = instance.mastodonApiVersion,
            streamingUrl = instance.streamingUrl,
            webPushVapidKey = instance.vapidKey.takeIf { instance.hasWebPush },
            groupedNotifications = announcesFourThree && found.grouped,
            notificationPolicy = found.policy,
            filtersV2 = found.filters,
            editHistory = announcesFourThree,
            translation = instance.translationEnabled,
            translationLanguages = found.languages,
            onlyMediaFilter = found.onlyMedia,
            onlyVideoFilter = found.onlyVideo,
            onlyNewsFilter = found.onlyNews,
            watchPositions = found.watch,
            stories = found.stories,
            collections = found.collections,
            mediaFromNextcloudFiles = found.fromFile,
            preferencesWrite = nextcloud,
            limits = instance.limits,
            theme = found.theme,
            detectedAt = now(),
        )
    }

    private data class Findings(
        val grouped: Boolean,
        val policy: Boolean,
        val filters: Boolean,
        val watch: Boolean,
        val stories: Boolean,
        val collections: Boolean,
        val fromFile: Boolean,
        val onlyMedia: Boolean,
        val onlyVideo: Boolean,
        val onlyNews: Boolean,
        val languages: Map<String, List<String>>,
        val theme: NextcloudTheme?,
    )

    /** Every probe at once; the server's Pixelfed `features` answer wins over a probe where it speaks. */
    private suspend fun findings(apiBase: HttpUrl, token: AccessToken?, nextcloud: Boolean): Findings = coroutineScope {
        val probes = Probes(executor, apiBase, token?.let { "Bearer ${it.value}" }.orEmpty())
        val grouped = async { probes.exists("api/v2/notifications", LIMIT_ONE) }
        val policy = async { probes.exists("api/v2/notifications/policy") }
        val filters = async { probes.exists("api/v2/filters") }
        val watch = async { probes.exists("api/v1/videos/continue", LIMIT_ONE) }
        val stories = async { probes.exists("api/v1/stories/carousel") }
        val collections = async { probes.exists("api/v1/collections") }
        val fromFile = async { probes.exists("api/v1/media/from-file") }
        val features = async { probes.pixelfedFeatures() }
        val languages = async { probes.translationLanguages() }
        val theme = async { theme(ServerCapabilities.minimal(apiBase.toString()).nextcloudRoot) }
        val video = async { nextcloud && probes.acceptsNarrowing("only_video") }
        val news = async { nextcloud && probes.acceptsNarrowing("only_news") }
        val media = async { nextcloud || probes.acceptsNarrowing("only_media") }
        Findings(
            grouped = grouped.await(),
            policy = policy.await(),
            filters = filters.await(),
            watch = watch.await(),
            stories = features.await()?.stories ?: stories.await(),
            collections = features.await()?.collections ?: collections.await(),
            fromFile = fromFile.await(),
            onlyMedia = media.await(),
            onlyVideo = video.await(),
            onlyNews = news.await(),
            languages = languages.await(),
            theme = theme.await(),
        )
    }

    /**
     * The colour a Nextcloud wears, from its public `theming` capability at the Nextcloud root (not
     * the API base). Null on Mastodon and on a Nextcloud with the Theming app disabled.
     */
    public suspend fun theme(nextcloudRoot: String): NextcloudTheme? {
        val root = nextcloudRoot.toHttpUrlOrNull() ?: return null
        val url = root.newBuilder().addPathSegments(
            "ocs/v2.php/cloud/capabilities",
        ).addQueryParameter("format", "json").build()
        val request =
            request(
                Endpoint("ocs/v2.php/cloud/capabilities", authentication = Authentication.NextcloudPublic),
                OcsCapabilitiesDto.serializer(),
            ) {
                it.ocs?.data?.capabilities?.theming?.toDomain()
            }
        return (executor.execute(request, url, authorization = "") as? ApiResult.Success)?.value?.decoded?.value
            ?.takeIf { it.hasColour }
    }

    private companion object {
        const val PROBE_SECONDS = 8L
        const val MASTODON_4_3_API = 3
        val LIMIT_ONE = listOf(QueryItem("limit", "1"))
    }
}

/** The individual probes, each answering true or false and never failing. */
private class Probes(private val executor: RequestExecutor, private val apiBase: HttpUrl, private val bearer: String) {
    suspend fun exists(path: String, query: List<QueryItem> = emptyList()): Boolean {
        val request = unitRequest(Endpoint(path, query = query))
        return when (val result = executor.execute(request, request.endpoint.resolve(apiBase), bearer)) {
            is ApiResult.Success -> true
            is ApiResult.Failure -> result.error.provesRouteExists()
        }
    }

    /** Nextcloud Social answers 422 to a narrowing it does not accept; 2xx means it was taken. */
    suspend fun acceptsNarrowing(parameter: String): Boolean {
        val request = unitRequest(
            Endpoint("api/v1/timelines/public", query = listOf(QueryItem("limit", "1"), QueryItem(parameter, "true"))),
        )
        return executor.execute(request, request.endpoint.resolve(apiBase), bearer) is ApiResult.Success
    }

    suspend fun translationLanguages(): Map<String, List<String>> =
        fetch(InstanceEndpoints.translationLanguages()).orEmpty()

    suspend fun pixelfedFeatures(): PixelfedFeaturesDto.Features? = fetch(
        request(Endpoint("api/v2/config", authentication = Authentication.None), PixelfedFeaturesDto.serializer()) {
            it.features
        },
    )

    private suspend fun <T> fetch(request: ApiRequest<T>): T? = (
        executor.execute(
            request,
            request.endpoint.resolve(apiBase),
            bearer,
        ) as? ApiResult.Success
        )?.value?.decoded?.value
}

/**
 * 401 and 403 prove the route exists; the token only lacked a scope. A 405 on a POST-only route
 * proves it too. Everything else, 404 included, counts as absent.
 */
private fun ApiError.provesRouteExists(): Boolean = when (this) {
    is ApiError.Unauthorised, is ApiError.Forbidden -> true
    is ApiError.Server -> status == METHOD_NOT_ALLOWED
    else -> false
}

private const val METHOD_NOT_ALLOWED = 405

@Serializable
internal data class PixelfedFeaturesDto(val features: Features? = null) {
    @Serializable
    data class Features(
        @Serializable(with = OptionalBoolSerializer::class) val stories: Boolean? = null,
        @Serializable(with = OptionalBoolSerializer::class) val collections: Boolean? = null,
        @SerialName("stories_reactions") @Serializable(with = OptionalBoolSerializer::class)
        val storiesReactions: Boolean? = null,
    )
}
