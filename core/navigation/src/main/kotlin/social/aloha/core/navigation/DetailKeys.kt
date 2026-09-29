// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A post and the conversation around it. */
@Serializable
public data class ThreadKey(val statusId: String) : NavKey

/**
 * A profile, by the account's id on the reading account's server, or by its handle where only that is
 * known (a mention the post did not declare) and the server has to look it up.
 */
@Serializable
public data class AccountKey(val accountId: String? = null, val acct: String? = null) : NavKey

/** The public timeline of one hashtag. */
@Serializable
public data class TagKey(val name: String) : NavKey
