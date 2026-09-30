// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.ClientFactory
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.StatusAction
import social.aloha.core.network.endpoints.StatusEndpoints

/** A toggle a person can flip on a post. */
public enum class Toggle { Favourite, Boost, Bookmark, Pin, MuteConversation }

/**
 * What a person does to a post. A toggle shows at once: the stored copy changes first, the server is
 * asked after, and its answer replaces the guess, or the original comes back if it refused. Nothing
 * here is retried by itself, so a tap never lands twice.
 */
@Singleton
public class StatusInteractions @Inject constructor(
    private val statuses: StatusRepository,
    private val clients: ClientFactory,
) {
    /** Flips [toggle] on [status], the post shown (a boost's target); null when the server agreed. */
    public suspend fun toggle(account: SignedInAccount, status: Status, toggle: Toggle): ApiError? {
        val client = clients.forAccount(account) ?: return ApiError.NotFound
        val (guess, action) = flipped(status, toggle)
        statuses.save(account.id, guess)
        return when (val answer = client.execute(StatusEndpoints.action(status.id, action))) {
            // boosting answers with the new boost, which carries the post as the server now has it
            is ApiResult.Success -> null.also { statuses.save(account.id, answer.value.reblog ?: answer.value) }

            is ApiResult.Failure -> answer.error.also { statuses.save(account.id, status) }
        }
    }

    /** Votes [choices] in [status]'s poll; the poll the server answers with replaces the stored one. */
    public suspend fun vote(account: SignedInAccount, status: Status, choices: List<Int>): ApiError? {
        val poll = status.poll ?: return ApiError.NotFound
        val client = clients.forAccount(account) ?: return ApiError.NotFound
        return when (val answer = client.execute(StatusEndpoints.vote(poll.id, choices))) {
            is ApiResult.Success -> null.also { statuses.save(account.id, status.copy(poll = answer.value)) }
            is ApiResult.Failure -> answer.error
        }
    }

    /** Deletes the reader's own [status]; it goes from every timeline once the server confirms. */
    public suspend fun delete(account: SignedInAccount, status: Status): ApiError? {
        val client = clients.forAccount(account) ?: return ApiError.NotFound
        return when (val answer = client.execute(StatusEndpoints.delete(status.id))) {
            is ApiResult.Success -> null.also { statuses.delete(account.id, status.id) }
            is ApiResult.Failure -> answer.error
        }
    }

    internal companion object {
        /** The post as it will be once the server agrees, and the route that asks it to. */
        fun flipped(status: Status, toggle: Toggle): Pair<Status, StatusAction> = when (toggle) {
            Toggle.Favourite -> status.copy(
                favourited = !status.favourited,
                favouritesCount = counted(status.favouritesCount, !status.favourited),
            ) to
                if (status.favourited) StatusAction.Unfavourite else StatusAction.Favourite

            Toggle.Boost -> status.copy(
                reblogged = !status.reblogged,
                reblogsCount = counted(status.reblogsCount, !status.reblogged),
            ) to
                if (status.reblogged) StatusAction.Unreblog else StatusAction.Reblog

            Toggle.Bookmark -> status.copy(bookmarked = !status.bookmarked) to
                if (status.bookmarked) StatusAction.Unbookmark else StatusAction.Bookmark

            Toggle.Pin -> status.copy(pinned = !status.pinned) to
                if (status.pinned) StatusAction.Unpin else StatusAction.Pin

            Toggle.MuteConversation -> status.copy(muted = !status.muted) to
                if (status.muted) StatusAction.Unmute else StatusAction.Mute
        }

        private fun counted(count: Int, on: Boolean) = if (on) count + 1 else maxOf(0, count - 1)
    }
}
