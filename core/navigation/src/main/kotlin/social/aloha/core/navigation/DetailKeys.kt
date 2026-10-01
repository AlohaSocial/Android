// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A post and the conversation around it, as [readerId] (the app's id for the reading account) sees it. */
@Serializable
public data class ThreadKey(val readerId: String, val statusId: String) : NavKey

/**
 * A profile as [readerId] sees it: by the account's [id] on the reader's server, or by its handle where
 * only that is known (a mention the post did not declare) and the server has to look it up.
 */
@Serializable
public data class AccountKey(val readerId: String, val id: String? = null, val acct: String? = null) : NavKey

/** The public timeline of one hashtag, as [readerId] sees it. */
@Serializable
public data class TagKey(val readerId: String, val name: String) : NavKey

/** Who or what a post reached, each a list of its own. */
public enum class StatusListKind { FavouritedBy, BoostedBy, Quotes, Reactions }

/** One of a post's [StatusListKind] lists, as [readerId] sees it. */
@Serializable
public data class StatusListKey(val readerId: String, val statusId: String, val kind: StatusListKind) : NavKey

/** Who follows an account, or whom it follows. */
public enum class PeopleKind { Followers, Following }

/** An account's followers or the accounts it follows, as [readerId] sees them. */
@Serializable
public data class PeopleKey(val readerId: String, val accountId: String, val kind: PeopleKind) : NavKey

/** The settings: its sections, each opened by [SettingsSectionKey]. */
@Serializable
public data object SettingsKey : NavKey

/** One settings section, by the key it registers under. */
@Serializable
public data class SettingsSectionKey(val section: String) : NavKey

/**
 * A new post written as [readerId]; an answer to [replyToId] on that account's server when given,
 * draft [draftId] carried on with, the reader's own post [editId] edited, or their post [redraftId]
 * deleted and written again. What another app shared starts a new post: [sharedText], and the
 * content addresses of [sharedMedia]. With [story] the post starts out as a story.
 */
@Serializable
public data class ComposerKey(
    val readerId: String,
    val replyToId: String? = null,
    val draftId: String? = null,
    val editId: String? = null,
    val redraftId: String? = null,
    val sharedText: String? = null,
    val sharedMedia: List<String> = emptyList(),
    val story: Boolean = false,
) : NavKey

/**
 * A report by [readerId] about account [accountId], known as [handle], from its post [statusId] when
 * it was opened from one.
 */
@Serializable
public data class ReportKey(
    val readerId: String,
    val accountId: String,
    val handle: String,
    val statusId: String? = null,
) : NavKey {
    /** An account on another server, whose moderators can be sent a copy. */
    val remote: Boolean get() = handle.removePrefix("@").contains('@')
}

/** Search, as [readerId]: accounts, hashtags and posts, from anywhere by their address. */
@Serializable
public data class SearchKey(val readerId: String) : NavKey

/** [readerId]'s lists. */
@Serializable
public data class ListsKey(val readerId: String) : NavKey

/** The timeline of [readerId]'s list [listId], called [title]. */
@Serializable
public data class ListKey(val readerId: String, val listId: String, val title: String) : NavKey

/** Who is in [readerId]'s list [listId], called [title]: added and removed here, unless a group's. */
@Serializable
public data class ListMembersKey(val readerId: String, val listId: String, val title: String) : NavKey

/** [readerId]'s own profile, being edited. */
@Serializable
public data class EditProfileKey(val readerId: String) : NavKey

/** The drafts of [readerId], and the posts waiting to be sent. */
@Serializable
public data class DraftsKey(val readerId: String) : NavKey

/** The posts [readerId] has waiting on the server for their time. */
@Serializable
public data class ScheduledPostsKey(val readerId: String) : NavKey

/** What decides which notifications [readerId]'s server holds back. */
@Serializable
public data class NotificationPolicyKey(val readerId: String) : NavKey

/** The senders whose notifications [readerId]'s server holds back, to let through or drop. */
@Serializable
public data class NotificationRequestsKey(val readerId: String) : NavKey

/** The albums of account [ownerId] as [readerId] sees them, or the reader's own without one. */
@Serializable
public data class AlbumsKey(val readerId: String, val ownerId: String? = null) : NavKey

/** One album's posts, as [readerId] sees them; [own] albums can be changed. */
@Serializable
public data class AlbumKey(val readerId: String, val albumId: String, val title: String, val own: Boolean) : NavKey

/** Picks which of [readerId]'s albums the reader's post [statusId] goes into. */
@Serializable
public data class AddToAlbumKey(val readerId: String, val statusId: String) : NavKey

/** Photos' Explore: what is trending with pictures, hashtags and people, as [readerId] sees them. */
@Serializable
public data class PhotoExploreKey(val readerId: String) : NavKey

/** The watch page of post [statusId]'s video, as [readerId] sees it. */
@Serializable
public data class WatchKey(val readerId: String, val statusId: String) : NavKey

/** The media viewer on post [statusId]'s attachments from the [index]th, as [readerId] sees them. */
@Serializable
public data class MediaViewerKey(val readerId: String, val statusId: String, val index: Int)
