// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import social.aloha.core.data.timeline.AccountRows
import social.aloha.core.data.timeline.MergedPager
import social.aloha.core.data.timeline.MergedTimeline
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.model.Filter
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.SeenBy
import social.aloha.core.ui.StatusRowUi

/** How one account reads its home timeline, and the colour it is marked in. */
internal data class AccountLook(val settings: AccountSettings, val filters: List<Filter>, val colour: Int?)

/**
 * Every account's home timeline as one list of rows. Each account's stored rows are shaped by its own
 * filters and reading settings first, then merged so a post several accounts see shows once, as the first
 * of them, the account in use where it sees it. A post shown as another account is shown under that
 * server's name, as a post from another server is, so none of its ids is taken for one on the server in use.
 */
internal class MergedRows @Inject constructor(private val builders: Provider<TimelineRowBuilder>) {
    /** What the list shows, and what an action on a row of it needs. */
    data class Built(
        val items: List<TimelineItem>,
        /** Each shown post, by the id it shows under: the account it acts as, and its own id there. */
        val owners: Map<String, Pair<String, String>>,
        /** When each shown post came, by the id it shows under, a boost by when it was boosted. */
        val times: Map<String, Instant>,
        /** The accounts that bring the next page. */
        val behind: List<String>,
        /** The posts waiting behind the pill, already stored, newest first. */
        val held: List<Status>,
    )

    private val each = ConcurrentHashMap<String, TimelineRowBuilder>()

    private fun builder(accountId: String) = each.getOrPut(accountId) { builders.get() }

    fun filters(accountId: String): Flow<List<Filter>> = builder(accountId).filters(accountId)

    suspend fun refreshFilters(account: SignedInAccount) = builder(account.id).refreshFilters(account)

    /** [accounts] shown now, the first in use: what is built for any other is let go. */
    fun keepOnly(accounts: List<SignedInAccount>) {
        each.keys.retainAll(accounts.map { it.id }.toSet())
        each.values.forEach { it.clear() }
    }

    /** The [accounts] shown, the first in use, each one's [stored] rows and how it reads them. */
    data class Input(
        val accounts: List<SignedInAccount>,
        val stored: Map<String, List<TimelineRow>>,
        val looks: Map<String, AccountLook>,
    )

    /**
     * [input]'s rows as one list, as far as [paging] says each account has loaded: what each holds back for
     * the pill, and the accounts with nothing further down.
     */
    fun build(input: Input, colors: RichTextColors, paging: MergedPager.State): Built {
        val (accounts, stored, looks) = input
        val posts = accounts.associate { it.id to shaped(it, input, colors, paging.held[it.id].orEmpty()) }
        val page = MergedTimeline.merge(
            accounts.map { account ->
                val shown = posts[account.id].orEmpty()
                AccountRows.of(account.id, stored[account.id].orEmpty(), account.id in paging.ended) { it.id in shown }
            },
        )
        val byId = accounts.associateBy { it.id }
        val marks = accounts.associate { it.id to SeenBy(it.qualifiedHandle, it.avatarUrl, looks[it.id]?.colour) }
        val owners = HashMap<String, Pair<String, String>>()
        val times = HashMap<String, Instant>()
        val items = page.posts.mapNotNull { post ->
            val row = posts[post.owner]?.get(post.status.id)?.row ?: return@mapNotNull null
            val shown = if (post.owner == accounts.first().id) row else row.heldBy(byId.getValue(post.owner).host)
            owners[shown.statusId] = post.owner to row.statusId
            times[shown.rowId] = post.status.createdAt
            TimelineItem.Post(shown, post.seenBy.mapNotNull(marks::get))
        }.distinctBy { it.key }
        val waiting = accounts.flatMap { account ->
            val ids = paging.held[account.id].orEmpty()
            stored[account.id].orEmpty().filterIsInstance<TimelineRow.Post>().filter { it.id in ids }.map { it.status }
        }.sortedByDescending { it.createdAt }
        return Built(items, owners, times, page.behind, waiting)
    }

    /** [account]'s posts as it shows them, filtered and hidden by its own settings, [held] ones kept back. */
    private fun shaped(
        account: SignedInAccount,
        input: Input,
        colors: RichTextColors,
        held: Set<String>,
    ): Map<String, TimelineItem.Post> {
        val look = input.looks[account.id] ?: AccountLook(AccountSettings(), emptyList(), null)
        val shape = TimelineRowBuilder.Shape(colors, look.settings, look.filters, held, emptySet())
        val stored = input.stored[account.id].orEmpty().let { rows ->
            // only another account's own posts show its rows, and the people they name are its server's
            if (account.id == input.accounts.first().id) {
                rows
            } else {
                rows.map { row ->
                    (row as? TimelineRow.Post)?.let { TimelineRow.Post(it.status.namingFrom(account.host)) }
                        ?: row
                }
            }
        }
        return builder(account.id).build(account, TimelineSource.Home, stored, shape)
            .filterIsInstance<TimelineItem.Post>().associateBy { it.row.rowId }
    }
}

/** [acct], a handle on [host] or elsewhere, in full. */
private fun fullHandle(acct: String, host: String): String = acct.removePrefix("@").let {
    if ('@' in
        it
    ) {
        it
    } else {
        "$it@$host"
    }
}

/**
 * [this] post, as shown by an account on [host] other than the one in use: the people it mentions under that
 * server's name with their handles in full, so a tap on one opens them by handle, never by an id of [host]'s
 * taken for one on the server in use. Shown only, never stored.
 */
internal fun Status.namingFrom(host: String): Status = copy(
    mentions = mentions.map {
        val full = fullHandle(it.acct, host)
        it.copy(id = TimelineSource.Remote.ID_PREFIX + full, acct = full)
    },
    reblog = reblog?.namingFrom(host),
)

/**
 * [this] row as an account other than the one in use holds it, on [host]: its ids, and its author's, under
 * that server's name, as a post read from another server is shown, and its author's handle in full. It is
 * never the reader's own here: what only its own account can do to it is done there.
 */
internal fun StatusRowUi.heldBy(host: String): StatusRowUi {
    val prefix = TimelineSource.Remote.ID_PREFIX + host + ":"
    fun StatusRowUi.AuthorUi.there(): StatusRowUi.AuthorUi {
        val full = fullHandle(handle, host)
        return copy(id = TimelineSource.Remote.ID_PREFIX + full, handle = "@$full")
    }
    return copy(
        rowId = prefix + rowId,
        statusId = prefix + statusId,
        author = author.there(),
        quote = quote?.let { it.copy(statusId = prefix + it.statusId, author = it.author.there()) },
        isOwn = false,
    )
}
