// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.util.concurrent.TimeUnit
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.navigation.FilterEditKey
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.fullDate

/** One filter, new or changed; closes once it is saved or deleted. */
@Composable
public fun FilterEditRoute(key: FilterEditKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<FilterEditViewModel, FilterEditViewModel.Factory>(key = key.toString()) {
        it.create(key)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    FilterEditScreen(state, viewModel, onDone, modifier)
}

/** What the filter editor asks for. */
internal interface FilterEditActions {
    fun onTitle(title: String)

    fun onAddKeyword(keyword: String, wholeWord: Boolean)

    fun onWholeWord(index: Int, wholeWord: Boolean)

    fun onRemoveKeyword(index: Int)

    fun onContext(context: FilterContext, on: Boolean)

    fun onAction(action: FilterAction)

    fun onExpiry(expiry: Expiry)

    fun onSave()

    fun onDelete()

    fun onFailureShown()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterEditScreen(
    state: FilterEditUiState,
    actions: FilterEditActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(if (state.isNew) R.string.filters_new else R.string.filters_edit)
    val snackbars = remember { SnackbarHostState() }
    val refused = state.refused?.let {
        stringResource(if (it == Refused.Delete) R.string.filters_delete_failed else R.string.filters_save_failed)
    }
    LaunchedEffect(state.refused) {
        if (refused != null) {
            actions.onFailureShown()
            snackbars.showSnackbar(refused)
        }
    }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Close, stringResource(R.string.filters_discard)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { deleting = true }) {
                            Icon(AlohaIcons.Delete, stringResource(R.string.filters_delete))
                        }
                    }
                    TextButton(onClick = actions::onSave, enabled = state.canSave) {
                        Text(stringResource(R.string.filters_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            content(state, actions)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.filters_delete_title)) },
            text = { Text(stringResource(R.string.filters_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.filters_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** The editor's rows; nothing to change until the filter is there, as what was typed would be overwritten. */
private fun LazyListScope.content(state: FilterEditUiState, actions: FilterEditActions) {
    if (state.loading || state.saving) item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    when {
        state.loadFailed -> item(key = "load-failed") {
            Text(stringResource(R.string.filters_load_failed), Modifier.padding(AlohaSpacing.m))
        }

        !state.loading -> fields(state, actions)
    }
}

/** The filter's name, keywords, where it applies, what it does and for how long. */
private fun LazyListScope.fields(state: FilterEditUiState, actions: FilterEditActions) {
    item(key = "title") {
        OutlinedTextField(
            value = state.title,
            onValueChange = actions::onTitle,
            label = { Text(stringResource(R.string.filters_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
        )
    }
    keywords(state, actions)
    heading(R.string.filters_where)
    FilterContext.entries.filter { it != FilterContext.Unknown }.forEach { context ->
        item(key = "c:${context.wire}") {
            CheckRow(stringResource(context.label), context in state.contexts) {
                actions.onContext(context, it)
            }
        }
    }
    item(key = "action") {
        ChoiceRows(
            stringResource(R.string.filters_what),
            actionChoices(state.action),
            state.action,
            actions::onAction,
        )
    }
    item(key = "expiry") {
        ChoiceRows(
            stringResource(R.string.filters_how_long),
            expiries(state.keptExpiry),
            state.expiry,
            actions::onExpiry,
        )
    }
}

/** The words the filter looks for, each whole or anywhere, and a field to add another. */
private fun LazyListScope.keywords(state: FilterEditUiState, actions: FilterEditActions) {
    heading(R.string.filters_keywords)
    itemsIndexed(state.shownKeywords, key = { index, word -> "k:$index:${word.keyword}" }) { index, word ->
        ListItem(
            headlineContent = { Text(word.keyword) },
            supportingContent = {
                Row(
                    Modifier.toggleable(word.wholeWord, role = Role.Checkbox) { actions.onWholeWord(index, it) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = word.wholeWord, onCheckedChange = null)
                    Text(stringResource(R.string.filters_whole_word), Modifier.padding(start = AlohaSpacing.xs))
                }
            },
            trailingContent = {
                IconButton(onClick = { actions.onRemoveKeyword(index) }) {
                    Icon(AlohaIcons.Remove, stringResource(R.string.filters_remove_keyword, word.keyword))
                }
            },
        )
    }
    item(key = "add-keyword") { AddKeyword { actions.onAddKeyword(it, wholeWord = true) } }
}

@Composable
private fun AddKeyword(onAdd: (String) -> Unit) {
    var word by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = word,
            onValueChange = { word = it },
            label = { Text(stringResource(R.string.filters_keyword)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            onAdd(word)
            word = ""
        }, enabled = word.isNotBlank()) { Text(stringResource(R.string.filters_add)) }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChecked),
        headlineContent = { Text(label) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
    )
}

private fun LazyListScope.heading(text: Int) {
    item(key = "h:$text") {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                .semantics { heading() },
        )
    }
}

/** How long a filter can apply: as it is where it has an expiry, for good, or for a while from now. */
@Composable
private fun expiries(kept: Instant?): List<Pair<Expiry, String>> = buildList {
    if (kept != null) {
        val now = remember { Instant.now() }
        val label = if (kept.isAfter(now)) R.string.filters_until else R.string.filters_expired_on
        add(Expiry.Keep(kept) to stringResource(label, fullDate(kept)))
    }
    add(Expiry.Never to stringResource(R.string.filters_forever))
    PERIODS.forEach { (seconds, label) -> add(Expiry.In(seconds) to stringResource(label)) }
}

/** Warn or hide; blurring only for a filter that blurs already, so editing it keeps what it does. */
@Composable
private fun actionChoices(current: FilterAction): List<Pair<FilterAction, String>> = buildList {
    add(FilterAction.Warn to stringResource(R.string.filters_action_warn))
    add(FilterAction.Hide to stringResource(R.string.filters_action_hide))
    if (current == FilterAction.Blur) add(FilterAction.Blur to stringResource(R.string.filters_action_blur))
}

/** How long from now a filter can apply, in seconds, and how that is said. */
private val PERIODS = listOf(
    TimeUnit.MINUTES.toSeconds(HALF_HOUR) to R.string.filters_for_30_minutes,
    TimeUnit.HOURS.toSeconds(1) to R.string.filters_for_hour,
    TimeUnit.HOURS.toSeconds(HALF_DAY) to R.string.filters_for_12_hours,
    TimeUnit.DAYS.toSeconds(1) to R.string.filters_for_day,
    TimeUnit.DAYS.toSeconds(WEEK) to R.string.filters_for_week,
)

private const val HALF_HOUR = 30L
private const val HALF_DAY = 12L
private const val WEEK = 7L
