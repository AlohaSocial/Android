// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import social.aloha.core.model.AccountStatistics
import social.aloha.core.model.ConversationPartner
import social.aloha.core.model.NumberMap
import social.aloha.core.ui.R as UiR

// The keys are those Nextcloud Social sends; a number the server did not send is left out, never shown as 0.

@Composable
internal fun Hero(statistics: AccountStatistics, handle: String) {
    StatisticsCard(statistics.account?.acct?.let { "@$it" } ?: "@$handle") {
        Tiles {
            Tile(whole(statistics.posts["total"] ?: 0.0), stringResource(R.string.statistics_posts))
            statistics.account?.let { account ->
                Tile(whole(account.followers.toDouble()), stringResource(R.string.statistics_followers))
                Tile(whole(account.following.toDouble()), stringResource(R.string.statistics_following))
            }
        }
        statistics.window?.takeIf { it.capped }?.let { window ->
            Text(
                pluralStringResource(R.plurals.statistics_capped, window.counted, window.counted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Engagement(statistics: AccountStatistics) {
    val engagement = statistics.engagement
    val rates = statistics.rates
    StatisticsCard(stringResource(R.string.statistics_engagement)) {
        Tiles {
            engagement["likes"]?.let {
                Tile(whole(it), stringResource(R.string.statistics_favourites))
            }
            engagement["boosts"]?.let { Tile(whole(it), stringResource(R.string.statistics_boosts)) }
            engagement["replies"]?.let { Tile(whole(it), stringResource(R.string.statistics_replies)) }
            rates["per_post"]?.let { Tile(oneDecimal(it), stringResource(R.string.statistics_per_post)) }
            rates["per_follower"]?.let { Tile(percent(it), stringResource(R.string.statistics_per_follower)) }
            rates["silent"]?.let { Tile(whole(it), stringResource(R.string.statistics_silent)) }
        }
    }
}

@Composable
internal fun PostsByMonth(statistics: AccountStatistics) {
    val months = statistics.byMonth.sorted.map { it.first }.takeIf { it.isNotEmpty() } ?: return
    val title = stringResource(R.string.statistics_posts_by_month)
    StatisticsCard(title) {
        MonthBars(
            months,
            listOf(MaterialTheme.colorScheme.primary to statistics.byMonth.values),
            spoken(title, statistics.byMonth),
        )
    }
}

@Composable
internal fun EngagementByMonth(statistics: AccountStatistics) {
    val months = statistics.engagementByMonth.sorted.map { it.first }.takeIf { it.isNotEmpty() } ?: return
    val title = stringResource(R.string.statistics_engagement_by_month)
    StatisticsCard(title) {
        MonthBars(
            months,
            listOf(MaterialTheme.colorScheme.tertiary to statistics.engagementByMonth.values),
            spoken(title, statistics.engagementByMonth),
        )
    }
}

@Composable
internal fun Activity(statistics: AccountStatistics) {
    val activity = statistics.activity ?: return
    val months = (activity.originals.values.keys + activity.replies.values.keys + activity.boosts.values.keys)
        .sorted().takeIf { it.isNotEmpty() } ?: return
    val posts = stringResource(R.string.statistics_series_posts)
    val replies = stringResource(R.string.statistics_series_replies)
    val boosts = stringResource(R.string.statistics_series_boosts)
    val colours = MaterialTheme.colorScheme
    val title = stringResource(R.string.statistics_activity)
    StatisticsCard(title) {
        MonthBars(
            months,
            listOf(
                colours.primary to activity.originals.values,
                colours.tertiary to activity.replies.values,
                colours.secondary to activity.boosts.values,
            ),
            listOf(posts to activity.originals, replies to activity.replies, boosts to activity.boosts)
                .joinToString(". ") { (name, values) -> spoken(name, values) },
        )
        Legend(listOf(colours.primary to posts, colours.tertiary to replies, colours.secondary to boosts))
    }
}

@Composable
internal fun Visibility(statistics: AccountStatistics) {
    val rows = statistics.visibility.sorted.filter { it.second > 0 }.takeIf { it.isNotEmpty() } ?: return
    val total = rows.sumOf { it.second }
    StatisticsCard(stringResource(R.string.statistics_visibility)) {
        rows.forEach { (key, count) ->
            ValueRow(visibilityName(key), "${whole(count)} · ${percent(count / total * PERCENT)}")
        }
    }
}

@Composable
internal fun Rhythm(statistics: AccountStatistics) {
    val rhythm = statistics.consistency
    if (rhythm.values.isEmpty()) return
    StatisticsCard(stringResource(R.string.statistics_rhythm)) {
        Tiles {
            rhythm["active_days"]?.let { Tile(whole(it), stringResource(R.string.statistics_active_days)) }
            rhythm["streak"]?.let {
                Tile(whole(it), stringResource(R.string.statistics_streak))
            }
            rhythm["longest_gap"]?.let { Tile(whole(it), stringResource(R.string.statistics_gap)) }
        }
    }
}

@Composable
internal fun Conversations(statistics: AccountStatistics) {
    val partners = statistics.partners ?: return
    if (partners.inbound.isEmpty() && partners.outbound.isEmpty()) return
    StatisticsCard(stringResource(R.string.statistics_conversations)) {
        Partners(stringResource(R.string.statistics_replies_to_you), partners.inbound)
        Partners(stringResource(R.string.statistics_you_reply_to), partners.outbound)
    }
}

@Composable
private fun Partners(title: String, rows: List<ConversationPartner>) {
    if (rows.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    rows.take(PARTNERS).forEach { row ->
        ValueRow(
            if (row.account.startsWith("@")) row.account else "@${row.account}",
            pluralStringResource(R.plurals.statistics_partner_replies, row.replies, row.replies),
        )
    }
}

@Composable
internal fun Pictures(statistics: AccountStatistics) {
    val media = statistics.media
    if (media.values.isEmpty()) return
    StatisticsCard(stringResource(R.string.statistics_pictures)) {
        Tiles {
            media["images"]?.let {
                Tile(whole(it), stringResource(R.string.statistics_with_pictures))
            }
            media["described"]?.let { Tile(whole(it), stringResource(R.string.statistics_described)) }
            media["described_share"]?.let { Tile(percent(it), stringResource(R.string.statistics_described_share)) }
        }
    }
}

@Composable
private fun visibilityName(key: String): String = when (key) {
    "public" -> stringResource(R.string.statistics_visibility_public)
    "unlisted" -> stringResource(UiR.string.status_visibility_unlisted)
    "followers", "private" -> stringResource(UiR.string.status_visibility_private)
    "direct" -> stringResource(UiR.string.status_visibility_direct)
    else -> key
}

/** [name], then each month that had any, as a screen reader hears a chart. */
private fun spoken(name: String, values: NumberMap): String = name + ": " +
    values.sorted.filter { it.second > 0 }.joinToString(", ") { "${monthLabel(it.first)} ${whole(it.second)}" }

private const val PARTNERS = 8
private const val PERCENT = 100.0
