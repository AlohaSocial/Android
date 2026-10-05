// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.icu.text.ListFormatter
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusRowUi

/**
 * [items] with each run of [MIN_RUN] or more boosts folded into one [TimelineItem.Boosts]; a run holding
 * a post the reader unfolded, one of [expanded], stays as its posts. Anything else between two boosts,
 * a gap or the caught-up line too, ends a run.
 */
internal fun foldBoosts(items: List<TimelineItem>, expanded: Set<String>): List<TimelineItem> {
    val folded = ArrayList<TimelineItem>(items.size)
    val run = mutableListOf<TimelineItem.Post>()
    fun endRun() {
        if (run.size >= MIN_RUN && run.none { it.key in expanded }) {
            folded += TimelineItem.Boosts(run.map { it.row })
        } else {
            folded += run
        }
        run.clear()
    }
    items.forEach { item ->
        if (item is TimelineItem.Post && item.row.context is StatusRowUi.ContextLine.BoostedBy) {
            run += item
        } else {
            endRun()
            folded += item
        }
    }
    endRun()
    return folded
}

/**
 * Boosts in a row as one row of cards, a swipe apart, under "Bob and 3 others boosted", which a screen
 * reader reads naming everyone; "Show as posts" puts them back in the list. A card too narrow for the
 * action bar leaves it out: a tap opens the post, and a screen reader keeps the actions.
 */
@Composable
internal fun BoostCarousel(boosts: TimelineItem.Boosts, now: Instant, actions: StatusActions, onExpand: () -> Unit) {
    val names = boosts.rows.mapNotNull { (it.context as? StatusRowUi.ContextLine.BoostedBy)?.name }.distinct()
    val summary = if (names.size == 1) {
        pluralStringResource(R.plurals.timeline_boosts_by_one, boosts.rows.size, names.first(), boosts.rows.size)
    } else {
        pluralStringResource(R.plurals.timeline_boosts_summary, names.size - 1, names.first(), names.size - 1)
    }
    val spoken = stringResource(R.string.timeline_boosts_spoken, ListFormatter.getInstance().format(names))
    val tint = LocalAlohaSemanticColors.current.boost
    Column(Modifier.padding(vertical = AlohaSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth().padding(start = AlohaSpacing.m, end = AlohaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Icon(AlohaIcons.Boost, contentDescription = null, tint = tint, modifier = Modifier.size(ICON))
            Text(
                summary,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
            )
            TextButton(onClick = onExpand) { Text(stringResource(R.string.timeline_boosts_expand)) }
        }
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = AlohaSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            items(boosts.rows, key = { it.rowId }) { row ->
                OutlinedCard(Modifier.fillParentMaxWidth(CARD_SHARE)) {
                    StatusCard(row, now, LocalSensitiveMediaPolicy.current, actions, showActions = false)
                }
            }
        }
    }
}

private const val MIN_RUN = 3
private const val CARD_SHARE = 0.85f
private val ICON = 18.dp
