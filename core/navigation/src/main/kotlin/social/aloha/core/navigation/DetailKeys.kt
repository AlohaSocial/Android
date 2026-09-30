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
 * deleted and written again.
 */
@Serializable
public data class ComposerKey(
    val readerId: String,
    val replyToId: String? = null,
    val draftId: String? = null,
    val editId: String? = null,
    val redraftId: String? = null,
) : NavKey

/** [readerId]'s own profile, being edited. */
@Serializable
public data class EditProfileKey(val readerId: String) : NavKey

/** The drafts of [readerId], and the posts waiting to be sent. */
@Serializable
public data class DraftsKey(val readerId: String) : NavKey

/** The posts [readerId] has waiting on the server for their time. */
@Serializable
public data class ScheduledPostsKey(val readerId: String) : NavKey
