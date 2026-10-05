// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.timeline.FilterEvaluator
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterContext
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowCache

/**
 * Turns stored rows into what the timeline shows: filtered, hidden by the reading settings or held
 * behind the pill, and each row built once per status.
 */
internal class TimelineRowBuilder @Inject constructor(
    private val cache: RichTextCache,
    private val filters: FilterRepository,
    private val followed: FollowedAuthors,
    private val clock: Clock,
) {
    /** What decides which stored rows show and how they are drawn. */
    data class Shape(
        val colors: RichTextColors,
        val settings: AccountSettings,
        val filters: List<Filter>,
        val held: Set<String>,
        val loadingGaps: Set<String>,
        /** Whether the reader follows each author asked about, where the reader hides those they don't. */
        val follows: Map<String, Boolean> = emptyMap(),
    )

    private val rows = StatusRowCache(cache)

    fun filters(accountId: String): Flow<List<Filter>> = filters.observe(accountId)

    suspend fun refreshFilters(account: SignedInAccount) {
        filters.refresh(account)
    }

    fun follows(readerId: String): Flow<Map<String, Boolean>> = followed.observe(readerId)

    /**
     * The authors of [stored] posts not yet known to be followed or not, while [source] hides those
     * [account] doesn't follow: these are asked about, and hidden until the answer says otherwise. The
     * reader is never asked about.
     */
    fun unknownAuthors(
        account: SignedInAccount,
        source: TimelineSource,
        stored: List<TimelineRow>,
        shape: Shape,
    ): Set<String> = if (!hidesStrangers(source, shape.settings)) {
        emptySet()
    } else {
        stored.filterIsInstance<TimelineRow.Post>().map { it.status.displayed.account.id }
            .filterTo(mutableSetOf()) { it !in shape.follows && it != account.serverAccountId }
    }

    suspend fun askAbout(account: SignedInAccount, authors: Set<String>) = followed.ask(account, authors)

    @Synchronized
    fun build(
        account: SignedInAccount,
        source: TimelineSource,
        stored: List<TimelineRow>,
        shape: Shape,
    ): List<TimelineItem> {
        rows.use(shape.colors)
        val evaluator = FilterEvaluator(shape.filters, contextOf(source), clock.instant())
        val strangers = hidesStrangers(source, shape.settings)
        return stored.mapNotNull { row ->
            when (row) {
                is TimelineRow.Gap -> TimelineItem.Gap(row.id, loading = row.id in shape.loadingGaps)

                is TimelineRow.Post -> row.status.takeUnless {
                    strangers && stranger(account.serverAccountId, it, shape.follows)
                }?.let { post(account.serverAccountId, it, evaluator, shape) }
            }
        }
    }

    /** The post a row showed, so an action on it acts on what the person saw. */
    fun statusFor(statusId: String): Status? = rows.statusFor(statusId)

    fun clear() = rows.clear()

    private fun post(viewer: String, status: Status, evaluator: FilterEvaluator, shape: Shape): TimelineItem? {
        val decision = if (status.id in shape.held || hiddenBySettings(status, shape.settings)) {
            FilterEvaluator.Decision.Hide
        } else {
            evaluator.decision(status, cache.plainText(status) + " " + status.displayed.spoilerText)
        }
        return when (decision) {
            FilterEvaluator.Decision.Hide -> null

            is FilterEvaluator.Decision.Warn ->
                TimelineItem.Post(rows.rowFor(status, viewer, decision.titles, decision.keywords))

            FilterEvaluator.Decision.Show -> TimelineItem.Post(rows.rowFor(status, viewer, null))
        }
    }

    private fun hiddenBySettings(status: Status, settings: AccountSettings): Boolean {
        val shown = status.displayed
        return (!settings.showBoosts && status.isBoost) ||
            // a reply to oneself continues a thread; only replies to others are hidden
            (!settings.showReplies && shown.inReplyToAccountId != null && shown.inReplyToAccountId != shown.account.id)
    }

    private fun hidesStrangers(source: TimelineSource, settings: AccountSettings) =
        settings.hideStrangers && (source == TimelineSource.Local || source == TimelineSource.Federated)

    // one not yet known to be followed is a stranger until the server says otherwise
    private fun stranger(viewer: String, status: Status, follows: Map<String, Boolean>): Boolean {
        val author = status.displayed.account.id
        return author != viewer && follows[author] != true
    }

    private fun contextOf(source: TimelineSource) = when (source) {
        TimelineSource.Home, is TimelineSource.List -> FilterContext.Home
        is TimelineSource.Account -> FilterContext.Account
        else -> FilterContext.Public
    }
}
