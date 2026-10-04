// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AnnualArchetype
import social.aloha.core.model.AnnualReport
import social.aloha.core.ui.readingColumn

/** The reader's years in review; a top post opens its thread through [onOpenPost]. */
@Composable
public fun YearRoute(onOpenPost: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: YearViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    YearScreen(state, onOpenPost, viewModel::show, viewModel::load, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YearScreen(
    state: YearState,
    onOpenPost: (String) -> Unit,
    onYear: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.year_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val report = state.report
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(AlohaSpacing.l))

                report != null -> Report(state, report, onOpenPost, onYear)

                state.trouble != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Message(stringResource(R.string.year_trouble))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.year_retry)) }
                }

                else -> Message(stringResource(R.string.year_none))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(AlohaSpacing.l))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Report(state: YearState, report: AnnualReport, onOpenPost: (String) -> Unit, onYear: (Int) -> Unit) {
    val data = report.data
    Column(
        Modifier.readingColumn().verticalScroll(rememberScrollState()).padding(vertical = AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
    ) {
        if (state.reports.size > 1) {
            FlowRow(
                Modifier.padding(horizontal = AlohaSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) {
                state.reports.forEach {
                    FilterChip(selected = it.year == report.year, onClick = { onYear(it.year) }, label = {
                        Text(it.year.toString())
                    })
                }
            }
        }
        Summary(report)
        if (data.topHashtags.isNotEmpty()) {
            Section(stringResource(R.string.year_hashtags))
            data.topHashtags.take(TOP_TAGS).forEach {
                ListItem(
                    headlineContent = { Text("#${it.name}") },
                    supportingContent = { Text(pluralStringResource(R.plurals.year_tag_uses, it.count, it.count)) },
                )
            }
        }
        val top = state.posts[report.year].orEmpty()
        if (top.isNotEmpty()) {
            Section(stringResource(R.string.year_top_posts))
            top.forEach { post ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onOpenPost(post.statusId) },
                    overlineContent = { Text(stringResource(why(post.why))) },
                    headlineContent = { Text(post.text.ifBlank { stringResource(R.string.year_post_no_text) }) },
                )
            }
        }
    }
}

/** What the year came to: its heading, its archetype, the posts and followers, and the months. */
@Composable
private fun Summary(report: AnnualReport) {
    val data = report.data
    Column(
        Modifier.padding(horizontal = AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        Text(
            stringResource(R.string.year_heading, report.year),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        archetype(data.archetype)?.let { (name, line) ->
            Text(
                stringResource(name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(line), style = MaterialTheme.typography.bodyLarge)
        }
        val busiest = data.busiestMonth?.let { monthName(it.month) }
        val posts = pluralStringResource(R.plurals.year_posts, data.totalStatuses, data.totalStatuses)
        Text(
            if (busiest != null) stringResource(R.string.year_posts_busiest, posts, busiest) else posts,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            pluralStringResource(R.plurals.year_followers, data.totalFollowers, data.totalFollowers),
            style = MaterialTheme.typography.bodyLarge,
        )
        Months(report)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
    )
}

/** A bar a month, as tall as its posts; a screen reader hears the months that had any. */
@Composable
private fun Months(report: AnnualReport) {
    val months = report.data.timeSeries.sortedBy { it.month }
    val most = months.maxOfOrNull { it.statuses }?.takeIf { it > 0 } ?: return
    val spoken = months.filter { it.statuses > 0 }.joinToString(", ") { "${monthName(it.month)} ${it.statuses}" }
    val label = stringResource(R.string.year_chart, spoken)
    Row(
        Modifier.fillMaxWidth().height(CHART).clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight((month.statuses.toFloat() / most).coerceAtLeast(MIN_BAR))
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
}

private fun monthName(month: Int): String =
    Month.of(month.coerceIn(1, MONTHS)).getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun archetype(archetype: AnnualArchetype): Pair<Int, Int>? = when (archetype) {
    AnnualArchetype.Lurker -> R.string.year_lurker to R.string.year_lurker_line
    AnnualArchetype.Booster -> R.string.year_booster to R.string.year_booster_line
    AnnualArchetype.Pollster -> R.string.year_pollster to R.string.year_pollster_line
    AnnualArchetype.Replier -> R.string.year_replier to R.string.year_replier_line
    AnnualArchetype.Oracle -> R.string.year_oracle to R.string.year_oracle_line
    AnnualArchetype.Unknown -> null
}

private fun why(kind: TopKind): Int = when (kind) {
    TopKind.Boosts -> R.string.year_top_boosts
    TopKind.Replies -> R.string.year_top_replies
    TopKind.Favourites -> R.string.year_top_favourites
}

private val CHART = 96.dp
private const val MIN_BAR = 0.02f
private const val MONTHS = 12
private const val TOP_TAGS = 5
