// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** What a link points at, as far as its shape tells. */
public sealed interface LinkTarget {
    /** A post on [host]; [localId] is its id there when the address carries it. */
    public data class Post(val url: String, val host: String, val localId: String?) : LinkTarget

    /** A profile, as `user@host`. */
    public data class Profile(val url: String, val acct: String) : LinkTarget

    public data class Tag(val name: String) : LinkTarget

    /** Nothing the app opens itself: the browser takes it. */
    public data object Web : LinkTarget
}

/**
 * Turns an address into what it points at, from its shape alone: the paths Mastodon, Nextcloud Social,
 * Pleroma and Misskey give posts, profiles and hashtags, the fediverse's `web+ap://` scheme and the
 * app's own `alohasocial://open?url=`. Anything else, and anything that is not http(s), is [LinkTarget.Web],
 * so an ordinary link never leaves the browser's hands and is never sent to a server to look up.
 */
public object RouteResolver {
    private val handle = Regex("@([A-Za-z0-9_.-]+)(?:@([A-Za-z0-9.-]+))?")
    private val user = Regex("[A-Za-z0-9_.-]+")

    // Mastodon's ids are numbers: a blog's `/@author/some-title` is an article, not a post
    private val number = Regex("[0-9]+")
    private val id = Regex("[A-Za-z0-9]+")

    public fun parse(address: String): LinkTarget {
        val uri = try {
            URI(address.trim())
        } catch (_: URISyntaxException) {
            return LinkTarget.Web
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> web(uri)
            "web+ap" -> https(uri)?.let(::web) ?: LinkTarget.Web
            "alohasocial" -> opened(uri)
            else -> LinkTarget.Web
        }
    }

    /** Where the browser opens [address]: its https form, or null for anything that is not a web address. */
    public fun browsable(address: String): String? {
        val uri = try {
            URI(address.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> uri.toString()
            "web+ap" -> https(uri)?.toString()
            "alohasocial" -> openedUrl(uri)
            else -> null
        }
    }

    /** The https address a `web+ap://` one names; its parts are already encoded, so they are not again. */
    private fun https(uri: URI): URI? = try {
        URI("https://" + uri.rawAuthority + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: ""))
    } catch (_: URISyntaxException) {
        null
    }

    /** `alohasocial://open?url=…`: the address another app asked to open here. */
    private fun opened(uri: URI): LinkTarget = openedUrl(uri)?.let(::parse) ?: LinkTarget.Web

    private fun openedUrl(uri: URI): String? {
        if (uri.host != "open") return null
        return uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("url=") }
            ?.removePrefix("url=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.startsWith("https://") }
    }

    private fun web(uri: URI): LinkTarget {
        val host = uri.host?.lowercase() ?: return LinkTarget.Web
        val url = uri.toString()
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return post(url, host, path) ?: named(url, host, path) ?: LinkTarget.Web
    }

    /** `/@user/123`, `/@user@remote/123`, `/users/user/statuses/123`, `/notice/ID` and `/notes/ID`. */
    private fun post(url: String, host: String, path: List<String>): LinkTarget.Post? = when {
        path.size == PAIR && handle.matches(path[0]) && number.matches(path[1]) ->
            // seen through another server, the id is that server's rather than the post's own
            LinkTarget.Post(url, host, path[1].takeIf { remoteOf(path[0]) == null })

        path.size == CANONICAL && path[0] == "users" && path[2] == "statuses" && number.matches(path[CANONICAL - 1]) ->
            LinkTarget.Post(url, host, path[CANONICAL - 1])

        path.size == PAIR && path[0] in setOf(
            "notice",
            "notes",
        ) && id.matches(path[1]) -> LinkTarget.Post(url, host, null)

        else -> null
    }

    /** `/@user`, `/@user@remote`, `/users/user` and `/tags/name`. */
    private fun named(url: String, host: String, path: List<String>): LinkTarget? = when {
        path.size == 1 && handle.matches(path[0]) -> profile(url, path[0], host)
        path.size == PAIR && path[0] == "users" && user.matches(path[1]) -> LinkTarget.Profile(url, "${path[1]}@$host")
        path.size == PAIR && path[0] == "tags" -> LinkTarget.Tag(path[1])
        else -> null
    }

    private fun profile(url: String, segment: String, host: String): LinkTarget {
        val match = handle.matchEntire(segment) ?: return LinkTarget.Web
        val user = match.groupValues[1]
        return LinkTarget.Profile(url, "$user@${match.groupValues[2].ifEmpty { host }}")
    }

    private fun remoteOf(segment: String): String? = handle.matchEntire(segment)?.groupValues?.get(2)?.ifEmpty { null }

    /** A path of two segments, and Mastodon's canonical four: `users`, user, `statuses`, id. */
    private const val PAIR = 2
    private const val CANONICAL = 4
}
