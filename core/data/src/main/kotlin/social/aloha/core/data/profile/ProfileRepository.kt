// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.Account
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.Relationship
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Story
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Paginated
import social.aloha.core.network.endpoints.AccountAction
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.CollectionEndpoints
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.ProfileEndpoints
import social.aloha.core.network.endpoints.StoryEndpoints

/** What the reader can change about how they relate to an account. */
public enum class RelationshipChange { Follow, Unfollow, Mute, Unmute, Block, Unblock }

/** One page of accounts, and where the next begins; none at the end. */
public data class AccountPage(val accounts: List<Account>, val next: HttpUrl?)

/**
 * Profiles as the reader's server knows them. Nothing here is stored: a profile is fetched when it
 * opens, and its posts come through the timeline cache like any other timeline's.
 */
@Singleton
public class ProfileRepository @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
) {
    /**
     * The account by its id on the reader's server, or by its handle. A handle the server has not met
     * (a remote account nobody there follows) is looked up with a resolving search, which fetches it.
     */
    public suspend fun resolve(reader: SignedInAccount, id: String?, acct: String?): Answer<Account> {
        if (id != null) return clients.answer(reader, AccountEndpoints.account(id))
        val handle = acct?.removePrefix("@") ?: return Answer.Missed(ApiError.NotFound)
        return when (val found = clients.answer(reader, AccountEndpoints.lookup(handle))) {
            is Answer.Got -> found
            is Answer.Missed -> if (found.error != ApiError.NotFound) found else searched(reader, handle)
        }
    }

    public suspend fun relationship(reader: SignedInAccount, id: String): Relationship? =
        (clients.answer(reader, AccountEndpoints.relationships(listOf(id))) as? Answer.Got)?.value?.firstOrNull()

    /**
     * Unfollowing also withdraws a follow request that is still waiting. Blocking or muting takes what
     * the account posted or was boosted in out of every stored timeline, as the server does.
     */
    public suspend fun change(reader: SignedInAccount, id: String, change: RelationshipChange): Answer<Relationship> =
        clients.answer(
            reader,
            when (change) {
                RelationshipChange.Follow -> AccountEndpoints.follow(id)
                RelationshipChange.Unfollow -> AccountEndpoints.action(id, AccountAction.Unfollow)
                RelationshipChange.Mute -> AccountEndpoints.mute(id, notifications = true)
                RelationshipChange.Unmute -> AccountEndpoints.action(id, AccountAction.Unmute)
                RelationshipChange.Block -> AccountEndpoints.action(id, AccountAction.Block)
                RelationshipChange.Unblock -> AccountEndpoints.action(id, AccountAction.Unblock)
            },
        ).also { answer ->
            val hides = change == RelationshipChange.Block || change == RelationshipChange.Mute
            if (hides && answer is Answer.Got) statuses.removeAuthor(reader.id, id)
        }

    /** Nextcloud Social's twelve weeks of posting, for its own accounts; null elsewhere or when it has none. */
    public suspend fun highlights(reader: SignedInAccount, id: String): ProfileHighlights? {
        if (!reader.capabilities.isNextcloudSocial) return null
        return (clients.answer(reader, ProfileEndpoints.highlights(id)) as? Answer.Got)?.value?.takeIf { it.available }
    }

    public suspend fun stories(reader: SignedInAccount, id: String): Answer<List<Story>> =
        clients.answer(reader, StoryEndpoints.forAccount(id))

    public suspend fun collections(reader: SignedInAccount, id: String): Answer<List<MediaCollection>> =
        clients.answer(reader, CollectionEndpoints.forAccount(id))

    /** Followers or following, a page at a time along the server's `Link` header. */
    public suspend fun people(
        reader: SignedInAccount,
        id: String,
        followers: Boolean,
        next: HttpUrl?,
    ): Answer<AccountPage> {
        val client = clients.forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
        val request = if (followers) AccountEndpoints.followers(id) else AccountEndpoints.following(id)
        val result: ApiResult<Paginated<Account>> =
            next?.let { client.page(it, request, Paging.DEFAULT_LIMIT) } ?: client.page(request, Paging.DEFAULT_LIMIT)
        return when (result) {
            is ApiResult.Success -> Answer.Got(AccountPage(result.value.items, result.value.link.next))
            is ApiResult.Failure -> Answer.Missed(result.error)
        }
    }

    private suspend fun searched(reader: SignedInAccount, handle: String): Answer<Account> =
        when (val found = clients.answer(reader, AccountEndpoints.search(handle, limit = 1, resolve = true))) {
            // only the account asked for: a search also answers with near matches
            is Answer.Got -> found.value.firstOrNull { sameHandle(it.acct, handle, reader.host) }
                ?.let { Answer.Got(it) } ?: Answer.Missed(ApiError.NotFound)

            is Answer.Missed -> found
        }
}

/** Whether [acct], as the reader's server writes it (no domain for its own accounts), is [handle]. */
internal fun sameHandle(acct: String, handle: String, readerHost: String): Boolean {
    val full = if ('@' in acct) acct else "$acct@$readerHost"
    return full.equals(handle, ignoreCase = true) || acct.equals(handle, ignoreCase = true)
}
