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
