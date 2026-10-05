// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import kotlinx.serialization.Serializable
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.TimelineSource

/**
 * One pinned feed as stored: a flat record whose [type] is a plain name, so a kind of feed a newer
 * version knows is skipped by an older one rather than failing the file, and every account's settings
 * with it.
 */
@Serializable
public data class PinnedFeedRecord(
    val type: String,
    val id: String? = null,
    val title: String? = null,
    val tag: String? = null,
    val any: List<String> = emptyList(),
    val all: List<String> = emptyList(),
    val none: List<String> = emptyList(),
    val localOnly: Boolean = false,
    val name: String? = null,
    val icon: String? = null,
)

/** The feed [this] stores, or null for a kind this version does not know or a record missing its part. */
public fun PinnedFeedRecord.toFeed(): PinnedFeed? {
    val kind = when (type) {
        FOLLOWING -> PinnedFeed.Kind.Following
        THIS_SERVER -> PinnedFeed.Kind.ThisServer
        EVERYONE -> PinnedFeed.Kind.Everyone
        NOTIFIED -> PinnedFeed.Kind.Notified
        BOOKMARKS -> PinnedFeed.Kind.Bookmarks
        FAVOURITES -> PinnedFeed.Kind.Favourites
        LIST -> if (id != null) PinnedFeed.Kind.List(id, title.orEmpty()) else null
        HASHTAG -> tag?.let { PinnedFeed.Kind.Hashtag(TimelineSource.Hashtag(it, any, all, none, localOnly)) }
        REMOTE -> id?.let(PinnedFeed.Kind::Remote)
        else -> null
    } ?: return null
    return PinnedFeed(kind, name, icon)
}

public fun PinnedFeed.toRecord(): PinnedFeedRecord = when (val kind = kind) {
    PinnedFeed.Kind.Following -> PinnedFeedRecord(FOLLOWING)

    PinnedFeed.Kind.ThisServer -> PinnedFeedRecord(THIS_SERVER)

    PinnedFeed.Kind.Everyone -> PinnedFeedRecord(EVERYONE)

    PinnedFeed.Kind.Notified -> PinnedFeedRecord(NOTIFIED)

    PinnedFeed.Kind.Bookmarks -> PinnedFeedRecord(BOOKMARKS)

    PinnedFeed.Kind.Favourites -> PinnedFeedRecord(FAVOURITES)

    is PinnedFeed.Kind.List -> PinnedFeedRecord(LIST, id = kind.id, title = kind.title)

    is PinnedFeed.Kind.Remote -> PinnedFeedRecord(REMOTE, id = kind.domain)

    is PinnedFeed.Kind.Hashtag -> with(kind.tags) {
        PinnedFeedRecord(HASHTAG, tag = name, any = any, all = all, none = none, localOnly = localOnly)
    }
}.copy(name = name, icon = icon)

private const val FOLLOWING = "following"
private const val THIS_SERVER = "server"
private const val EVERYONE = "everyone"
private const val NOTIFIED = "notified"
private const val BOOKMARKS = "bookmarks"
private const val FAVOURITES = "favourites"
private const val LIST = "list"
private const val HASHTAG = "hashtag"
private const val REMOTE = "remote"
