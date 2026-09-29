// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import java.net.URI
import java.net.URISyntaxException
import social.aloha.core.model.Mention
import social.aloha.core.model.StatusTag

/**
 * Classifies an `<a>` from its `class` attribute plus the status's own mentions and tags, which is what
 * makes a tap route in-app. Anything that is not a mention or hashtag is a web link, and only when it is
 * an absolute `http` or `https` address.
 */
internal class LinkResolver(private val mentions: List<Mention>, private val tags: List<StatusTag>) {
    fun resolve(attributes: Map<String, String>): RichText.Link? {
        val href = attributes["href"].orEmpty()
        val classes = attributes["class"].orEmpty().split(' ').toSet()
        val uri = parse(href)
        // Mastodon marks a hashtag `mention hashtag`, so the hashtag is asked about first
        return (if ("hashtag" in classes || attributes["rel"] == "tag") hashtag(href, uri) else null)
            ?: (if ("mention" in classes || "u-url" in classes) mention(href, uri) else null)
            ?: uri?.takeIf {
                it.scheme.equals("https", true) || it.scheme.equals("http", true)
            }?.let { RichText.Link.Web(href) }
    }

    /** By the mention's own URL first, then by the handle the path spells, declared or not. */
    private fun mention(href: String, uri: URI?): RichText.Link.Mention? {
        val byUrl = mentions.firstOrNull { it.url == href }
        val handle = if (byUrl == null) uri?.let(::handleOf) else null
        val declared =
            byUrl
                ?: handle?.let { h -> mentions.firstOrNull { it.acct.equals(h, true) || it.username.equals(h, true) } }
        return (declared?.acct ?: handle)?.let { RichText.Link.Mention(declared?.id, it, href) }
    }

    private fun hashtag(href: String, uri: URI?): RichText.Link.Hashtag? = uri?.lastSegment()?.let { name ->
        RichText.Link.Hashtag(tags.firstOrNull { it.name.equals(name, true) }?.name ?: name, href)
    }

    /** `https://host/@bob` or `https://host/users/bob` names `bob@host`; a handle with its own domain keeps it. */
    private fun handleOf(uri: URI): String? = uri.lastSegment()?.removePrefix("@")?.takeIf { it.isNotEmpty() }
        ?.let { handle -> if ('@' in handle || uri.host == null) handle else "$handle@${uri.host}" }

    private fun URI.lastSegment(): String? = path?.split('/')?.lastOrNull { it.isNotEmpty() }

    private fun parse(href: String): URI? = try {
        URI(href).takeIf { it.isAbsolute }
    } catch (_: URISyntaxException) {
        null
    }
}
