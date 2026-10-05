// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.CatchUpKey
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.readingWidth
import social.aloha.core.ui.rememberThreadRoutedActions

/** The catch-up page for [key]: each post opens its thread; [onBack] leaves, as being caught up does. */
@Composable
public fun CatchUpRoute(
    key: CatchUpKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<CatchUpViewModel, CatchUpViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    LaunchedEffect(state.done) { if (state.done) back() }
    CatchUpScreen(state, viewModel, rememberThreadRoutedActions(navigation), onBack, modifier)
}

/**
 * What arrived since, one card after another, sorted and filtered as chosen, and at the end the way to
 * say it is read.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun CatchUpScreen(
    state: CatchUpUiState,
    actions: CatchUpActions,
    rowActions: StatusActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.catch_up_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.timeline_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.arrived == 0 && state.loading -> Skeleton(Modifier.fillMaxSize())

                state.arrived == 0 -> EmptyState(stringResource(R.string.catch_up_nothing))

                else -> LazyColumn(Modifier.readingWidth().fillMaxSize()) {
                    item(key = "arrived") {
                        Text(
                            pluralStringResource(R.plurals.catch_up_arrived, state.arrived, state.arrived),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
                        )
                    }
                    stickyHeader(key = "choices") { Choices(state, actions) }
                    if (state.rows.isEmpty()) {
                        item(key = "none") { EmptyState(stringResource(R.string.catch_up_none_chosen)) }
                    }
                    items(state.rows, key = { it.rowId }) { row ->
                        StatusCard(row, state.now, LocalSensitiveMediaPolicy.current, rowActions, showActions = false)
                        PostDivider()
                    }
                    item(key = "done") {
                        Button(
                            onClick = actions::onCaughtUp,
                            modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
                        ) { Text(stringResource(R.string.catch_up_done)) }
                    }
                }
            }
        }
    }
}

/** The order, the kind and the person, as chips over the posts. */
@Composable
private fun Choices(state: CatchUpUiState, actions: CatchUpActions) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(vertical = AlohaSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AlohaSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) {
                items(CatchUpSort.entries, key = { "sort:$it" }) { sort ->
                    FilterChip(
                        selected = state.choice.sort == sort,
                        onClick = { actions.onSort(sort) },
                        label = { Text(stringResource(sort.label)) },
                    )
                }
                item(key = "person") { PersonChip(state, actions) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = AlohaSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            ) {
                items(CatchUpKind.entries, key = { "kind:$it" }) { kind ->
                    FilterChip(
                        selected = state.choice.kind == kind,
                        onClick = { actions.onKind(kind) },
                        label = { Text(stringResource(kind.label)) },
                    )
                }
            }
        }
    }
}

/** Everyone, or one of those who put posts on Home since, picked from a menu. */
@Composable
private fun PersonChip(state: CatchUpUiState, actions: CatchUpActions) {
    var open by remember { mutableStateOf(false) }
    val picked = state.people.firstOrNull { it.id == state.choice.person }
    Box {
        FilterChip(
            selected = picked != null,
            onClick = { open = true },
            label = { Text(picked?.name ?: stringResource(R.string.catch_up_everyone)) },
            trailingIcon = { Icon(AlohaIcons.ExpandMore, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.catch_up_everyone)) },
                onClick = {
                    open = false
                    actions.onPerson(null)
                },
            )
            state.people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.catch_up_person, person.name, person.posts)) },
                    onClick = {
                        open = false
                        actions.onPerson(person.id)
                    },
                )
            }
        }
    }
}

private val CatchUpSort.label: Int
    get() = when (this) {
        CatchUpSort.Time -> R.string.catch_up_sort_time
        CatchUpSort.Replies -> R.string.catch_up_sort_replies
        CatchUpSort.Boosts -> R.string.catch_up_sort_boosts
    }

private val CatchUpKind.label: Int
    get() = when (this) {
        CatchUpKind.All -> R.string.catch_up_kind_all
        CatchUpKind.Posts -> R.string.catch_up_kind_posts
        CatchUpKind.Boosts -> R.string.catch_up_kind_boosts
        CatchUpKind.Replies -> R.string.catch_up_kind_replies
        CatchUpKind.Media -> R.string.catch_up_kind_media
    }
