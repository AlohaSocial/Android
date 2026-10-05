// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import social.aloha.core.model.AuthorizationServerMetadata
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.NodeInfo
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Authentication
import social.aloha.core.network.Decoded
import social.aloha.core.network.Endpoint
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.RequestExecutor
import social.aloha.core.network.dto.AuthorizationServerMetadataDto
import social.aloha.core.network.dto.NodeInfoDirectoryDto
import social.aloha.core.network.dto.NodeInfoDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.request
import social.aloha.core.network.resolve
import social.aloha.core.network.sameOrigin

/** Where a server's Mastodon API was found, and what it said about itself. */
public data class ProbeOutcome(
    val apiBase: HttpUrl,
    val instance: InstanceDescription,
    val nodeInfo: NodeInfo?,
    val authorizationServer: AuthorizationServerMetadata?,
    val winner: ProbeCandidate,
    val attempted: List<ProbeCandidate>,
)

public sealed interface ProbeResult {
    public data class Found(val outcome: ProbeOutcome) : ProbeResult

    /** Something answered, but nothing like a Mastodon API; [attempted] is what was tried, in rank order. */
    public data class NothingAnswered(val attempted: List<ProbeCandidate>) : ProbeResult

    /** No candidate got any HTTP answer: the name did not resolve, the connection failed or timed out. */
    public data class Unreachable(val attempted: List<ProbeCandidate>) : ProbeResult

    /** The server's certificate is trusted by nobody; the person may choose to trust it and probe again. */
    public data class UntrustedCertificate(val error: ApiError.UntrustedCertificate) : ProbeResult
}

/**
 * Finds where a server's Mastodon API lives. Mastodon apps build `https://host/api/v1/…`; Nextcloud
 * Social serves its routes under the app path, and the root is served only where the administrator
 * installed the rewrite rules. A native client can target the app path directly.
 *
 * Candidates, all probed at once, lowest rank winning: the `issuer` of the RFC 8414 document at the
 * domain root (rank 0, accepted only on the typed host), then the domain root, the app path, the
 * pretty app path and, when a path was typed, that path and the app path beneath it. A candidate
 * qualifies when `api/v2/instance` or `api/v1/instance` answers with a non-empty domain, so a
 * Nextcloud login page answering 200 does not. NodeInfo at the domain root is read in parallel as
 * the cross-check of what software this is. Five seconds per request, ten in total.
 */
public class ServerProbe internal constructor(
    executor: RequestExecutor,
    private val ioDispatcher: CoroutineDispatcher,
) {
    public constructor(http: OkHttpClient, rateLimiter: RateLimiter, ioDispatcher: CoroutineDispatcher) :
        this(RequestExecutor(http, rateLimiter, ioDispatcher), ioDispatcher)

    private val executor = executor.withCallTimeout(PER_REQUEST_SECONDS)

    /**
     * Where [address]'s API is. A domain that only names accounts, its server elsewhere, answers nothing
     * itself; its host-meta then says which server WebFinger asks, and that server is probed instead.
     */
    public suspend fun discover(address: ServerAddress): ProbeResult {
        val found = race(address.origin, candidates(address))
        val server = if (found is ProbeResult.NothingAnswered) delegatedTo(address.origin) else null
        return server?.let { race(it, candidates(ServerAddress(it, pathHint = null))) }
            ?.takeIf { it is ProbeResult.Found } ?: found
    }

    /** The other server [origin]'s host-meta sends WebFinger to; null for none, or itself. */
    private suspend fun delegatedTo(origin: HttpUrl): HttpUrl? = withContext(ioDispatcher) {
        fetch(hostMetaRequest(), origin.newBuilder().encodedPath(WELL_KNOWN_HOST_META).build(), Tally())
    }?.takeIf { it.host != origin.host }

    /** What the first address that answers says about the server, for a preview while it is typed. */
    public suspend fun preview(address: ServerAddress): InstanceDescription? = withContext(ioDispatcher) {
        candidatesFor(address).firstNotNullOfOrNull { instanceAt(it.base) }
    }

    private fun candidates(address: ServerAddress): List<ProbeCandidate> =
        listOf(ProbeCandidate(0, address.origin, CandidateKind.AuthorizationServerIssuer)) + candidatesFor(address)

    /** Probes one API base a person typed by hand. */
    public suspend fun discover(manualBase: HttpUrl): ProbeResult {
        // only scheme, host, port and path: credentials, a query or a fragment typed into the address
        // would be shown back and sent with every request
        val base = HttpUrl.Builder().scheme(manualBase.scheme).host(manualBase.host).port(manualBase.port)
            .encodedPath(manualBase.encodedPath).build().asApiBase()
        return race(base.newBuilder().encodedPath("/").build(), listOf(ProbeCandidate(1, base, CandidateKind.Manual)))
    }

    // On the I/O dispatcher so the budget is always wall-clock time, whatever dispatcher the caller is on.
    private suspend fun race(origin: HttpUrl, candidates: List<ProbeCandidate>): ProbeResult =
        withContext(ioDispatcher) {
            val tally = Tally()
            val nodeInfo = async { nodeInfo(origin) }
            val winner =
                lowestRankFirst(candidates, TOTAL_BUDGET) { candidate -> attempt(candidate, origin, tally) }
            when {
                winner != null -> {
                    val (candidate, found) = winner
                    ProbeResult.Found(
                        ProbeOutcome(
                            found.base,
                            found.instance,
                            nodeInfo.await(),
                            found.metadata,
                            candidate,
                            candidates,
                        ),
                    )
                }

                tally.untrusted.isNotEmpty() ->
                    ProbeResult.UntrustedCertificate(tally.untrusted.first()).also { nodeInfo.cancel() }

                tally.answered -> ProbeResult.NothingAnswered(candidates).also { nodeInfo.cancel() }

                else -> ProbeResult.Unreachable(candidates).also { nodeInfo.cancel() }
            }
        }

    /** What the candidates of one probe ran into, gathered across the concurrent attempts. */
    private class Tally {
        val untrusted = CopyOnWriteArrayList<ApiError.UntrustedCertificate>()

        /** Whether any request got an HTTP answer at all, whatever it was. */
        @Volatile var answered = false
    }

    private class Found(
        val base: HttpUrl,
        val instance: InstanceDescription,
        val metadata: AuthorizationServerMetadata?,
    )

    private suspend fun attempt(candidate: ProbeCandidate, origin: HttpUrl, tally: Tally): Found? {
        if (candidate.kind != CandidateKind.AuthorizationServerIssuer) {
            return instanceAt(candidate.base, tally)?.let { Found(candidate.base, it, metadata = null) }
        }
        val metadata =
            fetch(authorizationServerRequest(), origin.newBuilder().encodedPath(WELL_KNOWN_OAUTH).build(), tally)
                ?: return null
        val issuer = metadata.issuer.toHttpUrlOrNull()?.takeIf { it.sameOrigin(origin) }
            ?.asApiBase() ?: return null
        return instanceAt(issuer, tally)?.let { Found(issuer, it, metadata) }
    }

    /** The instance at [base], v2 first; null unless it names a domain. */
    public suspend fun instanceAt(base: HttpUrl): InstanceDescription? = instanceAt(base, Tally())

    private suspend fun instanceAt(base: HttpUrl, tally: Tally): InstanceDescription? =
        listOf(InstanceEndpoints.v2(), InstanceEndpoints.v1()).firstNotNullOfOrNull { request ->
            fetch(request, request.endpoint.resolve(base), tally)?.takeIf { it.domain.isNotEmpty() }
        }

    /** NodeInfo from the root directory of [origin], falling back to the conventional paths. */
    public suspend fun nodeInfo(origin: HttpUrl): NodeInfo? {
        val none = Tally()
        val directory =
            fetch(nodeInfoDirectoryRequest(), origin.newBuilder().encodedPath(WELL_KNOWN_NODEINFO).build(), none)
        val candidates = listOfNotNull(directory?.newestHref()?.toHttpUrlOrNull()) +
            listOf("$WELL_KNOWN_NODEINFO/2.1", "$WELL_KNOWN_NODEINFO/2.0").map {
                origin.newBuilder().encodedPath(it).build()
            }
        return candidates.firstNotNullOfOrNull { fetch(nodeInfoRequest(), it, none) }
    }

    private suspend fun <T> fetch(request: ApiRequest<T>, url: HttpUrl, tally: Tally): T? =
        when (val result = executor.execute(request, url, authorization = "")) {
            is ApiResult.Success -> result.value.decoded.value.also { tally.answered = true }

            is ApiResult.Failure -> null.also {
                when (val error = result.error) {
                    is ApiError.UntrustedCertificate -> tally.untrusted.add(error)
                    is ApiError.Transport -> Unit
                    else -> tally.answered = true
                }
            }
        }

    private companion object {
        const val PER_REQUEST_SECONDS = 5L
        val TOTAL_BUDGET = 10.seconds
        const val WELL_KNOWN_OAUTH = "/.well-known/oauth-authorization-server"
        const val WELL_KNOWN_NODEINFO = "/.well-known/nodeinfo"
        const val WELL_KNOWN_HOST_META = "/.well-known/host-meta"

        fun hostMetaRequest() =
            ApiRequest(Endpoint(WELL_KNOWN_HOST_META.drop(1), authentication = Authentication.None)) {
                    _,
                    body,
                ->
                Decoded(
                    webFingerServer(body) ?: throw IllegalArgumentException("no lrdd"),
                    rawCount = null,
                    emptyList(),
                )
            }

        fun authorizationServerRequest() = request(
            Endpoint(WELL_KNOWN_OAUTH.drop(1), authentication = Authentication.None),
            AuthorizationServerMetadataDto.serializer(),
        ) { it.toDomain() ?: throw IllegalArgumentException("no issuer") }

        fun nodeInfoDirectoryRequest() = request(
            Endpoint(WELL_KNOWN_NODEINFO.drop(1), authentication = Authentication.None),
            NodeInfoDirectoryDto.serializer(),
        ) {
            it
        }

        fun nodeInfoRequest() =
            request(Endpoint("nodeinfo", authentication = Authentication.None), NodeInfoDto.serializer()) {
                it.toDomain()
            }
    }
}

/**
 * The server a host-meta document sends WebFinger lookups to: the origin of its `lrdd` link's template;
 * null when it has none, or names one by an address the app would not take typed: anything but https, or
 * a host that is no name, an IP address or `localhost`, so a domain cannot send the app into a network.
 */
internal fun webFingerServer(hostMeta: String): HttpUrl? =
    LINK.findAll(hostMeta).map { it.value }.firstOrNull { LRDD.containsMatchIn(it) }
        ?.let { TEMPLATE.find(it)?.groupValues?.get(1) }
        ?.replace("&amp;", "&")?.substringBefore('?')?.toHttpUrlOrNull()
        ?.takeIf { it.isHttps && '.' in it.host && !IP_LITERAL.matches(it.host) }
        ?.let { HttpUrl.Builder().scheme(it.scheme).host(it.host).port(it.port).build() }

private val IP_LITERAL = Regex("""^[\d.]+$|:""")

private val LINK = Regex("""<Link\b[^>]*>""", RegexOption.IGNORE_CASE)
private val LRDD = Regex("""\brel\s*=\s*["']lrdd["']""", RegexOption.IGNORE_CASE)
private val TEMPLATE = Regex("""\btemplate\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
