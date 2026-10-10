// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.WeeklyRecap
import social.aloha.core.navigation.LookingBackKey
import social.aloha.core.ui.SwitchRow
import social.aloha.core.ui.readingColumn

/** What the reader posted on this day in other years, and how this week compares with the last. */
@Composable
public fun LookingBackRoute(
    key: LookingBackKey,
    onOpenPost: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<LookingBackViewModel, LookingBackViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LookingBackScreen(state, viewModel, onOpenPost, onConnect, onBack, modifier)
}

@Composable
internal fun LookingBackScreen(
    state: LookingBackUiState,
    actions: LookingBackActions,
    onOpenPost: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val refused = stringResource(R.string.looking_back_change_failed)
    LaunchedEffect(state.changeFailed) {
        if (state.changeFailed) {
            actions.onChangeFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    NextcloudPage(
        stringResource(R.string.looking_back_title),
        state.status,
        onBack,
        onConnect,
        actions::onRetry,
        modifier,
        snackbars,
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().readingColumn()) {
            item(key = "week") { Heading(stringResource(R.string.looking_back_week)) }
            state.recap?.takeIf { it.enabled }?.let { recap -> item(key = "recap") { RecapSummary(recap) } }
            item(key = "switch") {
                SwitchRow(
                    stringResource(R.string.looking_back_recap),
                    state.recap?.enabled == true,
                    actions::onRecap,
                    stringResource(R.string.looking_back_recap_about),
                )
            }
            item(key = "day") { Heading(stringResource(R.string.looking_back_day)) }
            if (state.memories.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.looking_back_day_none),
                        Modifier.padding(horizontal = AlohaSpacing.l, vertical = AlohaSpacing.s),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.memories, key = { it.statusId }) { memory -> MemoryRow(memory) { onOpenPost(memory.statusId) } }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        Modifier.padding(start = AlohaSpacing.l, end = AlohaSpacing.l, top = AlohaSpacing.l).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** The recap's sentence: this week's posts, and how that stands against last week's. */
@Composable
private fun RecapSummary(recap: WeeklyRecap) {
    val headline = if (recap.thisWeek == 0) {
        stringResource(R.string.looking_back_week_none)
    } else {
        pluralStringResource(R.plurals.looking_back_week_posts, recap.thisWeek, recap.thisWeek)
    }
    val comparison = when {
        recap.thisWeek == 0 && recap.lastWeek == 0 -> stringResource(R.string.looking_back_week_quiet)

        recap.thisWeek > recap.lastWeek ->
            pluralStringResource(R.plurals.looking_back_week_up, recap.lastWeek, recap.lastWeek)

        recap.thisWeek < recap.lastWeek ->
            pluralStringResource(R.plurals.looking_back_week_down, recap.lastWeek, recap.lastWeek)

        else -> stringResource(R.string.looking_back_week_same)
    }
    ListItem(headlineContent = { Text(headline) }, supportingContent = { Text(comparison) })
}

@Composable
private fun MemoryRow(memory: Memory, onOpen: () -> Unit) {
    val years = memory.yearsAgo
    val text = memory.text.ifBlank {
        pluralStringResource(R.plurals.looking_back_attachments, memory.attachments, memory.attachments)
    }
    ListItem(
        overlineContent = { Text(pluralStringResource(R.plurals.looking_back_years_ago, years, years)) },
        headlineContent = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.looking_back_open), onClick = onOpen),
    )
}
