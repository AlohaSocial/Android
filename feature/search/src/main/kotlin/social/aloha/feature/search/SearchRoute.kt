// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.navigation.SearchKey
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.rememberThreadRoutedActions

/**
 * Search: accounts, hashtags and posts on the reader's server, from anywhere by their address. A post
 * found opens its thread, where it is acted on; a pasted address that names one post or person opens it.
 */
@Composable
public fun SearchRoute(
    key: SearchKey,
    navigation: StatusNavigation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    explore: @Composable () -> Unit = {},
) {
    val viewModel = hiltViewModel<SearchViewModel, SearchViewModel.Factory>(key = key.toString()) { it.create(key) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val nav by rememberUpdatedState(navigation)
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    LaunchedEffect(state.found) {
        when (val found = state.found) {
            is Found.Post -> nav.openThread(found.statusId)
            is Found.Person -> nav.openProfile(found.accountId, null)
            null -> return@LaunchedEffect
        }
        viewModel.onFoundShown()
    }
    // a post found is read here and acted on in its thread
    val rowActions = rememberThreadRoutedActions(navigation)
    SearchScreen(
        state,
        SearchActions(viewModel::onQuery, viewModel::onSubmit, viewModel::onClearRecent, onBack),
        rowActions,
        modifier,
        explore = explore,
    )
}

/** What the search screen asks for: typing, a submit, clearing the recent searches, and leaving. */
internal class SearchActions(
    val onQuery: (String) -> Unit,
    val onSubmit: (String) -> Unit,
    val onClearRecent: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    actions: SearchActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    now: Instant = remember { Instant.now() },
    explore: @Composable () -> Unit = {},
    initiallyEditing: Boolean = true,
) {
    val title = stringResource(R.string.search_title)
    // typing, the bar is the field; once searched, the query sits in a pill above the results, a tap edits it
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    val submit: (String) -> Unit = { query ->
        actions.onSubmit(query)
        if (query.isNotBlank()) editing = false
    }
    // a page with the field on top, not Material's expanding search bar: this screen never collapses into a
    // bar, and that one answers a back swipe by shrinking itself, which could leave it half shrunk, a
    // rounded sheet with the field in its middle and nothing below. Back is the navigation's, as elsewhere
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            if (editing || state.query.isBlank()) {
                EditBar(state.query, actions, submit)
            } else {
                QueryBar(state.query, actions, onEdit = { editing = true })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { Found(state, actions, submit, rowActions, now, explore) }
    }
}

/** The field across the whole bar, focused, with a line under it: what Gmail shows while one types. */
@Composable
private fun EditBar(query: String, actions: SearchActions, onSubmit: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(BAR), verticalAlignment = Alignment.CenterVertically) {
                Back(actions.onBack)
                TextField(
                    value = query,
                    onValueChange = actions.onQuery,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (query.isNotEmpty()) Clear { actions.onQuery("") }
            }
            HorizontalDivider()
        }
    }
}

/** Searched: the query in a pill between Back and clearing, as Gmail shows it above the results. */
@Composable
private fun QueryBar(query: String, actions: SearchActions, onEdit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(BAR),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Back(actions.onBack)
            Surface(
                onClick = onEdit,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(PILL),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        query,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = AlohaSpacing.m),
                    )
                }
            }
            Clear {
                actions.onQuery("")
                onEdit()
            }
        }
    }
}

@Composable
private fun Back(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.search_back)) }
}

@Composable
private fun Clear(onClear: () -> Unit) {
    IconButton(onClick = onClear) { Icon(AlohaIcons.Close, stringResource(R.string.search_clear)) }
}

/** What shows under the bar: recent searches and what is going on, the results, or why there are none. */
@Composable
private fun Found(
    state: SearchUiState,
    actions: SearchActions,
    onSubmit: (String) -> Unit,
    rowActions: StatusActions,
    now: Instant,
    explore: @Composable () -> Unit,
) {
    val trouble = state.trouble
    when {
        // nothing typed: the recent searches, and what is going on
        state.query.isBlank() -> Column {
            Recent(state.recent, actions.onClearRecent, onSubmit)
            explore()
        }

        state.searched && state.empty -> EmptyState(stringResource(R.string.search_nothing, state.query.trim()))

        state.searched -> Results(state, rowActions, now)

        trouble != null -> Trouble(trouble)

        else -> Skeleton()
    }
}

/** The recent searches, one rounded group of rows, each searched again on a tap; nothing when there are none. */
@Composable
private fun Recent(recent: List<String>, onClear: () -> Unit, onSubmit: (String) -> Unit) {
    if (recent.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(start = AlohaSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.search_recent),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.search_recent_clear)) }
    }
    Column(Modifier.padding(horizontal = AlohaSpacing.s)) {
        recent.forEachIndexed { index, query ->
            Grouped(index, recent.size) {
                ListItem(
                    leadingContent = { Icon(AlohaIcons.Recent, contentDescription = null) },
                    headlineContent = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onSubmit(query) },
                )
            }
        }
    }
}

/** Which of the answer's parts show; all of them unless one is picked. */
private enum class Part(val title: Int) {
    All(R.string.search_all),
    Accounts(R.string.search_accounts),
    Hashtags(R.string.search_hashtags),
    Posts(R.string.search_posts),
}

@Composable
private fun Results(state: SearchUiState, rowActions: StatusActions, now: Instant) {
    var part by rememberSaveable { mutableStateOf(Part.All) }
    val shown = { which: Part, has: Boolean -> has && (part == Part.All || part == which) }
    LazyColumn(Modifier.fillMaxSize()) {
        stickyHeader(key = "parts") { Parts(state, part) { part = it } }
        section(R.string.search_accounts, shown(Part.Accounts, state.accounts.isNotEmpty())) {
            itemsIndexed(state.accounts, key = { _, person -> "a:${person.author.id}" }) { index, person ->
                Grouped(index, state.accounts.size, Modifier.padding(horizontal = AlohaSpacing.s)) {
                    AccountRow(person.author, person.emojis, onOpen = { rowActions.onProfile(person.author.id) })
                }
            }
        }
        section(R.string.search_hashtags, shown(Part.Hashtags, state.hashtags.isNotEmpty())) {
            itemsIndexed(state.hashtags, key = { _, tag -> "t:${tag.name}" }) { index, tag ->
                Grouped(index, state.hashtags.size, Modifier.padding(horizontal = AlohaSpacing.s)) {
                    ListItem(
                        leadingContent = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                        headlineContent = { Text("#${tag.name}") },
                        modifier = Modifier.clickable { rowActions.onLink(RichLinkTarget.Hashtag(tag.name)) },
                    )
                }
            }
        }
        section(R.string.search_posts, shown(Part.Posts, state.posts.isNotEmpty())) {
            itemsIndexed(state.posts, key = { _, row -> "p:${row.rowId}" }) { index, row ->
                Grouped(index, state.posts.size, Modifier.padding(horizontal = AlohaSpacing.s)) {
                    StatusCard(row, now, LocalSensitiveMediaPolicy.current, rowActions, showActions = false)
                }
            }
        }
    }
}

/** The parts the answer has, as filter chips above it, as Gmail puts its filters above the mail it found. */
@Composable
private fun Parts(state: SearchUiState, part: Part, onPart: (Part) -> Unit) {
    val present = Part.entries.filter {
        when (it) {
            Part.All -> true
            Part.Accounts -> state.accounts.isNotEmpty()
            Part.Hashtags -> state.hashtags.isNotEmpty()
            Part.Posts -> state.posts.isNotEmpty()
        }
    }
    // one part found leaves nothing to choose between
    if (present.size <= 2) return
    Surface(color = MaterialTheme.colorScheme.surface) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AlohaSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            items(present, key = { it.name }) {
                FilterChip(
                    selected = it == part,
                    onClick = { onPart(it) },
                    label = { Text(stringResource(it.title)) },
                )
            }
        }
    }
}

/**
 * One row of a rounded group, as Gmail draws its lists: the group's ends round off, the rows between meet
 * with small corners and a sliver of space, and the rows sit on a tone of their own.
 */
@Composable
private fun Grouped(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val large = GROUP_CORNER
    val small = ROW_CORNER
    val shape = RoundedCornerShape(
        topStart = if (index == 0) large else small,
        topEnd = if (index == 0) large else small,
        bottomStart = if (index == count - 1) large else small,
        bottomEnd = if (index == count - 1) large else small,
    )
    val tone = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(shape = shape, color = tone, modifier = modifier.padding(vertical = ROW_GAP)) {
        // a list row paints the surface colour; within the group, that is the group's tone
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = tone), content = content)
    }
}

/** A heading and what is under it, where there is anything. */
private fun LazyListScope.section(heading: Int, shown: Boolean, content: LazyListScope.() -> Unit) {
    if (!shown) return
    item(key = "h:$heading") {
        Text(
            stringResource(heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                .semantics { heading() },
        )
    }
    content()
}

@Composable
private fun Trouble(trouble: Trouble) {
    TroubleStrip(stringResource(if (trouble == Trouble.Offline) R.string.search_offline else R.string.search_error))
}

private val BAR = 64.dp
private val PILL = 48.dp
private val GROUP_CORNER = 24.dp
private val ROW_CORNER = 4.dp
private val ROW_GAP = 1.dp
