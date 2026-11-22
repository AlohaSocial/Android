// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AccountStatistics
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.ListSection
import social.aloha.core.ui.readingColumn

/**
 * The Statistics tab of [readerId]'s own profile: what they posted, who answered, when and in what
 * language, counted by their own server. [onConnect] leads to connecting the Nextcloud where the page
 * needs it; a saved export, or one that failed, is said through [snackbars].
 */
@Composable
public fun statisticsSection(readerId: String, onConnect: () -> Unit, snackbars: SnackbarHostState): ListSection {
    val viewModel = hiltViewModel<StatisticsViewModel, StatisticsViewModel.Factory>(key = "statistics/$readerId") {
        it.create(readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let { viewModel.onExport(it.toString()) }
    }
    val saved = stringResource(R.string.statistics_exported)
    val failed = stringResource(R.string.statistics_failed)
    LaunchedEffect(state.exported, state.failed) {
        if (state.exported || state.failed) {
            try {
                snackbars.showSnackbar(if (state.exported) saved else failed)
            } finally {
                viewModel.onResultShown()
            }
        }
    }
    val onSave = { saveCsv.launch("social-statistics-${LocalDate.now()}.csv") }
    return ListSection { statisticsRows(state, viewModel, onSave, onConnect) }
}

/** The statistics as rows of a list: what the page waits for, or the numbers under their controls. */
internal fun LazyListScope.statisticsRows(
    state: StatisticsUiState,
    actions: StatisticsActions,
    onSave: () -> Unit,
    onConnect: () -> Unit,
) {
    when (state.status) {
        PageStatus.Loading -> item(key = "statistics-loading") { ListProgress() }

        PageStatus.NeedsConnection -> item(key = "statistics-connect") {
            Notice(stringResource(R.string.nextcloud_page_needs_connection)) {
                Button(onClick = onConnect) { Text(stringResource(R.string.nextcloud_page_connect)) }
            }
        }

        PageStatus.Failed -> item(key = "statistics-failed") {
            Notice(stringResource(R.string.nextcloud_page_failed)) {
                TextButton(onClick = actions::onRetry) { Text(stringResource(R.string.nextcloud_page_retry)) }
            }
        }

        PageStatus.Ready -> item(key = "statistics", contentType = "statistics") {
            Column(
                Modifier.readingColumn().padding(top = AlohaSpacing.s, bottom = AlohaSpacing.l),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
            ) {
                Controls(state, actions, onSave)
                val statistics = state.statistics
                if (statistics == null || (statistics.posts["total"] ?: 0.0) == 0.0) {
                    Empty()
                } else {
                    Sections(statistics, state.handle)
                }
            }
        }
    }
}

/** The window counted over, or the progress of counting again, and the buttons that count again and save a copy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: StatisticsUiState, actions: StatisticsActions, onSave: () -> Unit) {
    if (state.refreshing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        WindowPicker(state.days, actions::onWindow)
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s, Alignment.End),
    ) {
        OutlinedButton(onClick = actions::onCountAgain, enabled = !state.refreshing) {
            Icon(AlohaIcons.Retry, contentDescription = null, Modifier.padding(end = AlohaSpacing.xs))
            Text(stringResource(R.string.statistics_count_again))
        }
        OutlinedButton(onClick = onSave, enabled = !state.exporting) {
            Icon(AlohaIcons.Download, contentDescription = null, Modifier.padding(end = AlohaSpacing.xs))
            Text(stringResource(R.string.statistics_export))
        }
    }
}

/** Why there are no numbers yet, and what to do about it. */
@Composable
private fun Notice(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowPicker(days: Int, onWindow: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m)) {
        StatisticsUiState.WINDOWS.forEachIndexed { index, window ->
            SegmentedButton(
                selected = window == days,
                onClick = { onWindow(window) },
                shape = SegmentedButtonDefaults.itemShape(index, StatisticsUiState.WINDOWS.size),
            ) { Text(windowLabel(window)) }
        }
    }
}

@Composable
private fun windowLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.statistics_window_all)
    YEAR -> stringResource(R.string.statistics_window_year)
    else -> pluralStringResource(R.plurals.statistics_window_days, days, days)
}

@Composable
private fun Empty() {
    StatisticsCard(stringResource(R.string.statistics_empty)) {
        Text(stringResource(R.string.statistics_empty_about), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Sections(statistics: AccountStatistics, handle: String) {
    Hero(statistics, handle)
    Engagement(statistics)
    EngagementSources(statistics)
    Reach(statistics)
    PostsByMonth(statistics)
    EngagementByMonth(statistics)
    Activity(statistics)
    Visibility(statistics)
    Rhythm(statistics)
    if (statistics.hashtags.isNotEmpty()) {
        StatisticsCard(stringResource(R.string.statistics_hashtags)) { CountRows(statistics.hashtags, prefix = "#") }
    }
    if (statistics.languages.isNotEmpty()) {
        StatisticsCard(stringResource(R.string.statistics_languages)) { CountRows(statistics.languages) }
    }
    if (statistics.domains.isNotEmpty()) {
        StatisticsCard(stringResource(R.string.statistics_domains)) { CountRows(statistics.domains) }
    }
    Conversations(statistics)
    Pictures(statistics)
}

private const val CSV = "text/csv"
private const val YEAR = 365
