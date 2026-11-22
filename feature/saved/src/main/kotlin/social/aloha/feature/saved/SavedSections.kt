// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.saved

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ListHeading
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.ListSection
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.rememberThreadRoutedActions

/**
 * The Saved tab of [readerId]'s own profile: the posts they liked or bookmarked, one list at a time
 * behind a switch, on the bookmarks first when [bookmarks]. A post opens its thread, where it is acted on.
 */
@Composable
public fun savedSection(
    readerId: String,
    bookmarks: Boolean,
    navigation: StatusNavigation,
    snackbars: SnackbarHostState,
): ListSection {
    var kind by rememberSaveable(readerId) {
        mutableStateOf(if (bookmarks) SavedKind.Bookmarks else SavedKind.Favourites)
    }
    val posts = savedPosts(readerId, kind, navigation, snackbars)
    return ListSection {
        item(key = "saved-which", contentType = "saved-which") { SavedSwitch(kind) { kind = it } }
        posts.addTo(this)
    }
}

/**
 * The Drafts tab's part for [readerId]'s archived posts: their own posts taken off the profile without
 * being deleted, each one put back from here.
 */
@Composable
public fun archivedSection(readerId: String, navigation: StatusNavigation, snackbars: SnackbarHostState): ListSection {
    val posts = savedPosts(readerId, SavedKind.Archived, navigation, snackbars)
    return ListSection {
        item(key = "archived-heading", contentType = "heading") {
            ListHeading(stringResource(R.string.saved_archived))
        }
        posts.addTo(this)
    }
}

@Composable
private fun savedPosts(
    readerId: String,
    kind: SavedKind,
    navigation: StatusNavigation,
    snackbars: SnackbarHostState,
): ListSection {
    val viewModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(key = "saved/$readerId/$kind") {
        it.create(readerId, kind)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    val mapper = remember(colors) { viewModel.mapper(colors) }
    val rows = remember(state.posts, state.viewer, mapper) { state.posts.map { mapper.map(it, state.viewer) } }
    val refused = stringResource(R.string.saved_unarchive_failed)
    LaunchedEffect(state.unarchiveFailed) {
        if (state.unarchiveFailed) {
            snackbars.showSnackbar(refused)
            viewModel.onUnarchiveFailureShown()
        }
    }
    val actions = SavedActions(
        rememberThreadRoutedActions(navigation),
        onMore = viewModel::onMore,
        onUnarchive = viewModel::onUnarchive.takeIf { kind == SavedKind.Archived },
    )
    val now = remember { Instant.now() }
    return ListSection { savedRows(SavedPage(kind, state, rows, now), actions) }
}

/** What a list of kept posts does: [rows] for a post's own actions, [onUnarchive] only for the archive. */
internal class SavedActions(val rows: StatusActions, val onMore: () -> Unit, val onUnarchive: ((String) -> Unit)?)

/** One list of kept posts as it stands: [kind]'s posts, drawn as [rows], their ages from [now]. */
internal class SavedPage(
    val kind: SavedKind,
    val state: SavedPostsUiState,
    val rows: List<StatusRowUi>,
    val now: Instant,
)

/** Favorites or Bookmarks: which of the reader's kept posts the Saved tab shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedSwitch(kind: SavedKind, onKind: (SavedKind) -> Unit) {
    val which = stringResource(R.string.saved_which)
    val choices = listOf(SavedKind.Favourites, SavedKind.Bookmarks)
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth()
            .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
            .semantics { contentDescription = which },
    ) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == kind,
                onClick = { onKind(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
            ) { Text(stringResource(choice.title), maxLines = 1) }
        }
    }
}

/** [page]'s posts, then a way to more of them; an archived post can be put back on the profile. */
internal fun LazyListScope.savedRows(page: SavedPage, actions: SavedActions) {
    val state = page.state
    val rows = page.rows
    val prefix = page.kind.name.lowercase()
    when {
        rows.isEmpty() && state.loading -> item(key = "$prefix-loading") { ListProgress() }

        rows.isEmpty() -> item(key = "$prefix-empty") {
            Text(
                stringResource(if (state.failed) R.string.saved_failed else page.kind.empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.l),
            )
        }

        else -> {
            items(rows, key = { "$prefix-${it.rowId}" }, contentType = { "post" }) { row ->
                StatusCard(row, page.now, LocalSensitiveMediaPolicy.current, actions.rows, showActions = false)
                actions.onUnarchive?.let { unarchive ->
                    // whose post, said aloud: every row has one
                    val label = stringResource(R.string.saved_unarchive_post, row.author.plainName)
                    TextButton(
                        onClick = { unarchive(row.statusId) },
                        modifier = Modifier.padding(horizontal = AlohaSpacing.s)
                            .semantics { contentDescription = label },
                    ) { Text(stringResource(R.string.saved_unarchive)) }
                }
                PostDivider()
            }
            if (!state.done) {
                item(key = "$prefix-more") {
                    TextButton(
                        onClick = actions.onMore,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s),
                    ) { Text(stringResource(R.string.saved_more)) }
                }
            }
        }
    }
}

private val SavedKind.title: Int
    get() = when (this) {
        SavedKind.Bookmarks -> R.string.saved_bookmarks
        SavedKind.Favourites -> R.string.saved_favourites
        SavedKind.Archived -> R.string.saved_archived
    }

private val SavedKind.empty: Int
    get() = when (this) {
        SavedKind.Bookmarks -> R.string.saved_bookmarks_none
        SavedKind.Favourites -> R.string.saved_favourites_none
        SavedKind.Archived -> R.string.saved_archived_none
    }
