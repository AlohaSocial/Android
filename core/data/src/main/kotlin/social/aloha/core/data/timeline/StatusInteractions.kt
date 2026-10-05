// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.StatusSource
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.ComposeEndpoints
import social.aloha.core.network.endpoints.StatusAction
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusExtraEndpoints
import social.aloha.core.network.endpoints.StatusPost

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
    /**
     * Flips [toggle] on [shown], the post shown (a boost's target), from its stored copy where there is
     * one, which a toggle just before may already have changed; null when the server agreed. A boost
     * goes out seen by [visibility] where one is given.
     */
    public suspend fun toggle(
        account: SignedInAccount,
        shown: Status,
        toggle: Toggle,
        visibility: Visibility? = null,
    ): ApiError? {
        val client = clients.forAccount(account) ?: return ApiError.NotFound
        val status = statuses.get(account.id, shown.id) ?: shown
        val (guess, action) = flipped(status, toggle)
        statuses.saveToggled(account.id, guess)
        val seenBy = visibility?.takeIf { toggle == Toggle.Boost }?.wire
        return when (val answer = client.execute(StatusEndpoints.action(status.id, action, seenBy))) {
            // boosting answers with the new boost, which carries the post as the server now has it
            is ApiResult.Success -> null.also { statuses.saveToggled(account.id, answer.value.reblog ?: answer.value) }

            is ApiResult.Failure -> answer.error.also { statuses.saveToggled(account.id, status) }
        }
    }

    /** Reacts to post [statusId] with emoji [name], or takes the reaction back unless [add]. */
    public suspend fun react(account: SignedInAccount, statusId: String, name: String, add: Boolean): ApiError? {
        val request = if (add) StatusEndpoints.react(statusId, name) else StatusEndpoints.unreact(statusId, name)
        return (clients.answer(account, request) as? Answer.Missed)?.error
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

    /** Replaces the reader's own post [id] with [post]; the stored copy follows the server's answer. */
    public suspend fun edit(account: SignedInAccount, id: String, post: StatusPost): Answer<Status> =
        clients.answer(account, ComposeEndpoints.edit(id, post)).also { answer ->
            if (answer is Answer.Got) statuses.save(account.id, answer.value)
        }

    /** Post [id] as its author wrote it, which an edit starts from. */
    public suspend fun source(account: SignedInAccount, id: String): Answer<StatusSource> =
        clients.answer(account, StatusEndpoints.source(id))

    /** Deletes the reader's own [status]; it goes from every timeline once the server confirms. */
    public suspend fun delete(account: SignedInAccount, status: Status): ApiError? =
        (deleted(account, status.id) as? Answer.Missed)?.error

    /** Takes the reader's own [status] off their profile (Nextcloud Social's archive); nothing is deleted. */
    public suspend fun archive(account: SignedInAccount, status: Status): ApiError? =
        (clients.answer(account, StatusExtraEndpoints.archive(status.id)) as? Answer.Missed)?.error

    /**
     * Deletes the reader's own post [id] and answers with it as the server had it, its source text
     * included, which is what writing it again starts from.
     */
    public suspend fun deleted(account: SignedInAccount, id: String): Answer<Status> =
        clients.answer(account, StatusEndpoints.delete(id)).also { answer ->
            if (answer is Answer.Got) statuses.delete(account.id, id)
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
