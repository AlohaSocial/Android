// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What a person typed as their server, reduced to an origin and an optional path hint. Accepts
 * `cloud.example`, `https://cloud.example/`, `cloud.example/nextcloud`, `@alice@cloud.example` and
 * `alice@cloud.example`. HTTPS is assumed when no scheme is typed.
 */
public data class ServerAddress(val origin: HttpUrl, val pathHint: String?) {
    val host: String get() = origin.host

    public companion object {
        /**
         * @param allowCleartext whether a typed `http://` address is accepted; true only in debug
         *   builds, whose network security configuration permits the local dev instance.
         */
        public fun parse(typed: String, allowCleartext: Boolean = false): ServerAddress? {
            val text = stripHandle(typed.trim())?.let { if (it.contains("://")) it else "https://$it" }
            val url = text?.toHttpUrlOrNull()?.takeIf { (it.isHttps || allowCleartext) && it.host.isPlausibleHost() }
                ?: return null
            val origin = HttpUrl.Builder().scheme(
                url.scheme,
            ).host(url.host.lowercase(Locale.ROOT)).port(url.port).build()
            return ServerAddress(origin, pathHint = url.encodedPath.trim('/').takeIf { it.isNotEmpty() })
        }

        private fun String.isPlausibleHost() = contains('.') || equals("localhost", ignoreCase = true)

        /** `@alice@host` and `alice@host` name a host; anything else is returned as it is. */
        private fun stripHandle(text: String): String? = when {
            text.isEmpty() -> null

            text.startsWith("@") -> text.drop(1).split("@", limit = 2).takeIf { parts ->
                parts.size == 2 && parts.all { it.isNotEmpty() }
            }?.get(1)

            !text.contains("://") && text.contains('@') && !text.contains('/') ->
                text.substringAfter('@').takeIf { it.isNotEmpty() }

            else -> text
        }
    }
}

/** Why a candidate API base was tried; the sign-in screen words each kind for a person. */
public enum class CandidateKind {
    /** The `issuer` of the server's RFC 8414 authorization server metadata. */
    AuthorizationServerIssuer,

    /** `https://host/`: Mastodon, or Nextcloud Social with the root rewrite rules. */
    DomainRoot,

    /** `…/index.php/apps/social/`: Nextcloud Social without the rewrite rules. */
    AppPath,

    /** `…/apps/social/`: Nextcloud Social with pretty URLs, without the rewrite rules. */
    PrettyAppPath,

    /** The path the person typed. */
    TypedPath,

    /** Nextcloud Social in the subdirectory the person typed. */
    TypedAppPath,

    /** An API address the person entered by hand. */
    Manual,
}

/** One API base the probe tries; the lowest [rank] that answers wins. */
public data class ProbeCandidate(val rank: Int, val base: HttpUrl, val kind: CandidateKind)

/** The fixed candidates for [address], in rank order, starting at rank 1 (rank 0 is the RFC 8414 issuer). */
public fun candidatesFor(address: ServerAddress): List<ProbeCandidate> {
    val seen = mutableSetOf<HttpUrl>()
    val result = mutableListOf<ProbeCandidate>()
    fun add(suffix: String, kind: CandidateKind) {
        val base = address.origin.newBuilder().apply { if (suffix.isNotEmpty()) addPathSegments(suffix) }.build()
            .asApiBase()
        if (seen.add(base)) result += ProbeCandidate(result.size + 1, base, kind)
    }
    add("", CandidateKind.DomainRoot)
    add("index.php/apps/social", CandidateKind.AppPath)
    add("apps/social", CandidateKind.PrettyAppPath)
    address.pathHint?.let { hint ->
        add(hint, CandidateKind.TypedPath)
        add("$hint/index.php/apps/social", CandidateKind.TypedAppPath)
    }
    return result
}

/** An API base always ends in `/`, so relative endpoint paths compose onto it. */
public fun HttpUrl.asApiBase(): HttpUrl =
    if (encodedPath.endsWith("/")) this else newBuilder().addPathSegment("").build()
