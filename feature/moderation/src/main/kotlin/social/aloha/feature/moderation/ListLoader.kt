// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import social.aloha.core.data.Answer
import social.aloha.core.data.moderation.Moderation
import social.aloha.core.model.AdminReport
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError

/**
 * Puts the console's lists into [state] as the server answers them. A 403 means the token lacks the admin
 * scopes, so the console asks for them; anything else that the list could not be loaded.
 */
internal class ListLoader(private val moderation: Moderation, private val state: MutableStateFlow<ModerationState>) {
    suspend fun reports(reader: SignedInAccount) {
        val resolved = state.value.resolvedShown
        land(moderation.reports(reader, resolved)) { now, list ->
            // a reply for the queue no longer shown does not land
            if (now.resolvedShown == resolved) now.copy(reports = list) else now
        }
    }

    suspend fun accounts(reader: SignedInAccount) = state.value.let { asked ->
        land(moderation.accounts(reader, asked.standing, asked.origin, asked.username)) { now, list ->
            // a reply to an older search lands only if nothing newer was asked since
            if (now.standing == asked.standing && now.origin == asked.origin && now.username == asked.username) {
                now.copy(accounts = list)
            } else {
                now
            }
        }
    }

    suspend fun trends(reader: SignedInAccount) {
        land(moderation.trendingTags(reader)) { now, list -> now.copy(tags = list) }
        land(moderation.trendingPosts(reader)) { now, list -> now.copy(posts = list) }
        land(moderation.trendingLinks(reader)) { now, list -> now.copy(links = list) }
    }

    /** The report as the server answered after a change; a refusal leaves the row and says so. */
    fun report(id: String, answer: Answer<AdminReport>) {
        when (answer) {
            is Answer.Got -> state.update { current ->
                current.copy(reports = current.reports?.map { if (it.id == id) answer.value else it })
            }

            is Answer.Missed -> state.update { it.copy(refused = true) }
        }
    }

    private fun <T> land(answer: Answer<List<T>>, put: (ModerationState, List<T>) -> ModerationState) {
        when (answer) {
            is Answer.Got -> state.update { put(it, answer.value) }

            is Answer.Missed -> state.update {
                if (answer.error is ApiError.Forbidden) it.copy(consent = true) else it.copy(failed = true)
            }
        }
    }
}
