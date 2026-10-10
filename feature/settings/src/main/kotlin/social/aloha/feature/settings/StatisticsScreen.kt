// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AccountStatistics
import social.aloha.core.navigation.StatisticsKey
import social.aloha.core.ui.readingColumn

/** What the reader posted, who answered, when and in what language, counted by their own server. */
@Composable
public fun StatisticsRoute(
    key: StatisticsKey,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<StatisticsViewModel, StatisticsViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let { viewModel.onExport(it.toString()) }
    }
    StatisticsScreen(
        state,
        viewModel,
        onSave = { saveCsv.launch("social-statistics-${LocalDate.now()}.csv") },
        onConnect,
        onBack,
        modifier,
    )
}

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    actions: StatisticsActions,
    onSave: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val saved = stringResource(R.string.statistics_exported)
    val failed = stringResource(R.string.statistics_failed)
    LaunchedEffect(state.exported, state.failed) {
        if (state.exported || state.failed) {
            actions.onResultShown()
            snackbars.showSnackbar(if (state.exported) saved else failed)
        }
    }
    NextcloudPage(
        stringResource(R.string.statistics_title),
        state.status,
        onBack,
        onConnect,
        actions::onRetry,
        modifier,
        snackbars,
        actions = {
            IconButton(onClick = actions::onCountAgain, enabled = !state.refreshing) {
                Icon(AlohaIcons.Retry, stringResource(R.string.statistics_count_again))
            }
            IconButton(onClick = onSave, enabled = !state.exporting) {
                Icon(AlohaIcons.Download, stringResource(R.string.statistics_export))
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .verticalScroll(rememberScrollState())
                .readingColumn()
                .padding(bottom = AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
        ) {
            if (state.refreshing) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                )
            } else {
                WindowPicker(state.days, actions::onWindow)
            }
            val statistics = state.statistics
            if (statistics == null || (statistics.posts["total"] ?: 0.0) == 0.0) {
                Empty()
            } else {
                Sections(statistics, state.handle)
            }
        }
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
