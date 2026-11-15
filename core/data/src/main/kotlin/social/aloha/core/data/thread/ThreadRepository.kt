// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.thread

import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.toAnswer
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Reaction
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.StatusEdit
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.CountRefresh
import social.aloha.core.network.endpoints.CountsEndpoints
import social.aloha.core.network.endpoints.FeedPreferenceEndpoints
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusExtraEndpoints

/**
 * A conversation as the server sent it: the posts above the one asked for, and every reply below.
 * [countsFilling] says Aloha Social is still reading the focused post's counts from its origin
 * (`X-Social-Filling`), [repliesFilling] that it is still reading the replies from where they live
 * (`X-Social-Thread-Filling`); either is read again a few seconds later.
 */
public data class Conversation(
    val focused: Status,
    val ancestors: List<Status>,
    val descendants: List<Status>,
    val countsFilling: Boolean = false,
    val repliesFilling: Boolean = false,
)

/**
 * How Aloha Social orders a thread's replies for its reader, each among the replies to the same post:
 * the reader's Bluesky thread preference. [Random] differs on every read.
 */
public enum class ThreadOrder {
    Oldest,
    Newest,
    MostLikes,
    Random,
    ;

    public companion object {
        /** The server's word for it; anything else, or none, is [Oldest], the server's own default. */
        public fun of(sort: String?): ThreadOrder = when (sort) {
            "newest" -> Newest
            "most-likes" -> MostLikes
            "random" -> Random
            else -> Oldest
        }
    }
}

/**
 * A post and the conversation around it. Every post the server sends is stored with the rest, so an
 * action on one shows in the thread and in each timeline alike; what only this screen needs (the
 * card, the reactions, the edit history) is fetched and not kept. The first few who favourited or
 * boosted a post are kept in memory, so the post opened again shows them at once.
 */
@Singleton
public class ThreadRepository(
    private val statuses: StatusRepository,
    private val clients: ClientFactory,
    private val followUpPause: suspend () -> Unit,
) {
    @Inject public constructor(statuses: StatusRepository, clients: ClientFactory) :
        this(statuses, clients, { delay(FOLLOW_UP) })

    public fun observe(account: SignedInAccount, statusIds: List<String>): Flow<Map<String, Status>> =
        statuses.observe(account.id, statusIds)

    public suspend fun load(account: SignedInAccount, statusId: String): Answer<Conversation> {
        // the person opened it: its counts are asked of where it lives now, however recently they were
        val opened = StatusEndpoints.status(statusId, CountRefresh.Forced)
        val focused = when (val answer = clients.forAccount(account)?.filled(opened).toAnswer()) {
            is Answer.Got -> answer.value

            is Answer.Missed -> return answer.also {
                if (it.error == ApiError.NotFound) statuses.delete(account.id, statusId)
            }
        }
        statuses.saveAll(account.id, listOf(focused.value))
        return when (val conversation = context(account, focused.value)) {
            is Answer.Got -> Answer.Got(conversation.value.copy(countsFilling = focused.filling))
            is Answer.Missed -> conversation
        }
    }

    /** The conversation around [focused], its posts stored; [focused] is taken as it is and not stored. */
    public suspend fun context(account: SignedInAccount, focused: Status): Answer<Conversation> {
        val read = clients.forAccount(account)?.filled(StatusEndpoints.context(focused.id)).toAnswer()
        return when (read) {
            is Answer.Got -> {
                val context = read.value.value
                val conversation = Conversation(
                    focused,
                    context.ancestors,
                    context.descendants,
                    repliesFilling = read.value.threadFilling,
                )
                statuses.saveAll(account.id, conversation.ancestors + conversation.descendants)
                Answer.Got(conversation)
            }

            is Answer.Missed -> read
        }
    }

    /**
     * What Aloha Social was still filling in for [conversation], read once more [FOLLOW_UP] after it, as
     * its web app does. While the focused post's counts were being read, from 0.26.284 its counts and those
     * of the posts [shown] below it are read through `status_counts`, which counts no view, and stored.
     * While the replies were being read, the conversation is read again and answered; null otherwise.
     */
    public suspend fun followUp(
        account: SignedInAccount,
        conversation: Conversation,
        shown: () -> List<String>,
    ): Conversation? {
        val counts = conversation.countsFilling && account.capabilities.statusCounts
        if (!counts && !conversation.repliesFilling) return null
        followUpPause()
        if (counts) {
            val focused = conversation.focused.id
            val below = shown().dropWhile { it != focused }.drop(1)
            (clients.answer(account, CountsEndpoints.of(listOf(focused) + below)) as? Answer.Got)?.let {
                statuses.saveCounts(account.id, it.value)
            }
        }
        return if (conversation.repliesFilling) (context(account, conversation.focused) as? Answer.Got)?.value else null
    }

    /** The link card, which timelines never ask for: one request per row would be a request storm. */
    public suspend fun card(account: SignedInAccount, statusId: String): Card? =
        (clients.answer(account, StatusEndpoints.card(statusId)) as? Answer.Got)?.value

    /** Reactions, which Aloha Social serves only here: its statuses always carry an empty list. */
    public suspend fun reactions(account: SignedInAccount, statusId: String): Answer<List<Reaction>> =
        clients.answer(account, StatusExtraEndpoints.reactions(statusId))

    /** The server's custom emoji a reader may pick, to react with; `null` when the request failed. */
    public suspend fun reactionEmoji(account: SignedInAccount): List<CustomEmoji>? =
        (clients.answer(account, InstanceEndpoints.customEmojis()) as? Answer.Got)?.value
            ?.filter { it.visibleInPicker }

    public suspend fun history(account: SignedInAccount, statusId: String): Answer<List<StatusEdit>> =
        clients.answer(account, StatusEndpoints.history(statusId))

    public suspend fun favouritedBy(
        account: SignedInAccount,
        statusId: String,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): Answer<List<Account>> = clients.answer(account, StatusEndpoints.favouritedBy(statusId, limit))

    public suspend fun boostedBy(
        account: SignedInAccount,
        statusId: String,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): Answer<List<Account>> = clients.answer(account, StatusEndpoints.rebloggedBy(statusId, limit))

    /**
     * The first [limit] who favourited a post, or with [boosts] who boosted it: those last seen, at
     * once, then the server's answer. Nothing when neither is known.
     */
    public fun people(account: SignedInAccount, statusId: String, boosts: Boolean, limit: Int): Flow<List<Account>> =
        flow {
            val key = "${account.id} $statusId $boosts"
            known[key]?.let { emit(it) }
            val answer = if (boosts) boostedBy(account, statusId, limit) else favouritedBy(account, statusId, limit)
            (answer as? Answer.Got)?.value?.let { people ->
                known.put(key, people)
                emit(people)
            }
        }

    // ponytail: in memory only, the last posts opened; a table in cache.db if they should outlive the process
    private val known = LruCache<String, List<Account>>(KNOWN)
    private val orders = ConcurrentHashMap<String, ThreadOrder>()
    private val orderLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * How [account]'s Aloha Social orders thread replies for it, asked once per account and kept: it is
     * set in a Bluesky app and seldom changes. Null elsewhere, while the server has not answered, and from a
     * Social too old to say.
     */
    public suspend fun threadOrder(account: SignedInAccount): ThreadOrder? {
        if (!account.capabilities.isNextcloudSocial) return null
        return orderLocks.getOrPut(account.id) { Mutex() }.withLock {
            orders[account.id] ?: (clients.answer(account, FeedPreferenceEndpoints.threadSort()) as? Answer.Got)
                ?.value?.let { sort -> ThreadOrder.of(sort).also { orders[account.id] = it } }
        }
    }

    public suspend fun quotes(account: SignedInAccount, statusId: String): Answer<List<Status>> =
        when (val answer = clients.answer(account, StatusExtraEndpoints.quotes(statusId))) {
            is Answer.Got -> answer.also { statuses.saveAll(account.id, it.value) }
            is Answer.Missed -> answer
        }

    private companion object {
        const val KNOWN = 200

        /** How long after a read still being filled in it is read again, as Aloha Social's web app waits. */
        val FOLLOW_UP: Duration = 3.seconds
    }
}
