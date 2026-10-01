// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.navigation.FiltersKey
import social.aloha.core.ui.SwitchRow
import social.aloha.core.ui.fullDate

/**
 * The reader's filters: what each hides or warns about, where, and until when. One opens to be
 * changed; a new one is made from here.
 */
@Composable
public fun FiltersRoute(
    key: FiltersKey,
    onEdit: (filterId: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<FiltersViewModel, FiltersViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FiltersScreen(state, onEdit, viewModel::onHideStrangers, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FiltersScreen(
    state: FiltersUiState,
    onEdit: (filterId: String?) -> Unit,
    onHideStrangers: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
) {
    val title = stringResource(R.string.filters_title)
    val snackbars = remember { SnackbarHostState() }
    val stale = stringResource(R.string.filters_stale)
    LaunchedEffect(state.failed) { if (state.failed) snackbars.showSnackbar(stale) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.safety_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(AlohaIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.filters_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "strangers") {
                SwitchRow(
                    stringResource(R.string.filters_hide_strangers),
                    state.hideStrangers,
                    onHideStrangers,
                    summary = stringResource(R.string.filters_hide_strangers_summary),
                )
                HorizontalDivider()
            }
            when {
                state.filters.isEmpty() && state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(AlohaSpacing.l), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.filters.isEmpty() -> item(key = "none") {
                    Text(
                        stringResource(R.string.filters_none),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(AlohaSpacing.l),
                    )
                }

                else -> items(state.filters, key = { it.id }) { filter -> FilterRow(filter, now) { onEdit(filter.id) } }
            }
        }
    }
}

/** A filter's name, what it does where, and until when; an expired one says so. */
@Composable
private fun FilterRow(filter: Filter, now: Instant, onOpen: () -> Unit) {
    val hides = filter.filterAction == FilterAction.Hide
    val action = stringResource(if (hides) R.string.filters_hides else R.string.filters_warns)
    val places = filter.context.filter { it != FilterContext.Unknown }.map { stringResource(it.label) }
    val words = pluralStringResource(R.plurals.filters_keywords, filter.keywords.size, filter.keywords.size)
    val until = when {
        filter.isExpired(now) -> stringResource(R.string.filters_expired)
        filter.expiresAt != null -> stringResource(R.string.filters_until, fullDate(filter.expiresAt!!))
        else -> null
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(AlohaIcons.Filtered, contentDescription = null) },
        headlineContent = { Text(filter.title.ifBlank { stringResource(R.string.filters_untitled) }) },
        supportingContent = { Text(listOf(action, places.joinToString(", "), words).joinToString(" · ")) },
        overlineContent = until?.let { { Text(it) } },
    )
}

/** Where a filter applies, as the editor and the list name it. */
internal val FilterContext.label: Int
    get() = when (this) {
        FilterContext.Home -> R.string.filters_context_home
        FilterContext.Notifications -> R.string.filters_context_notifications
        FilterContext.Public -> R.string.filters_context_public
        FilterContext.Thread -> R.string.filters_context_thread
        FilterContext.Account -> R.string.filters_context_account
        FilterContext.Unknown -> R.string.filters_context_other
    }
