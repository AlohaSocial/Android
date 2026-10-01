// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import kotlinx.coroutines.launch
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusRowUi

/** What a key does on a timeline, with the letters Mastodon's web app gives them; L favourites like F. */
internal enum class TimelineCommand { Next, Previous, Open, Favourite, Boost, Reply, Compose, Help }

/**
 * The command [event] gives, or null for a key the timeline leaves to others: anything held with
 * Ctrl, Alt or Meta belongs to the system and the app's other shortcuts.
 */
internal fun timelineCommandOf(event: KeyEvent): TimelineCommand? {
    val held = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
    if (event.type != KeyEventType.KeyDown || held) return null
    val key = event.key
    return when {
        // by the character, since `?` sits on a different key on many layouts
        event.utf16CodePoint == '?'.code -> TimelineCommand.Help

        key == Key.J -> TimelineCommand.Next

        key == Key.K -> TimelineCommand.Previous

        key == Key.O || key == Key.Enter -> TimelineCommand.Open

        key == Key.F || key == Key.L -> TimelineCommand.Favourite

        key == Key.B -> TimelineCommand.Boost

        key == Key.R -> TimelineCommand.Reply

        key == Key.N -> TimelineCommand.Compose

        else -> null
    }
}

/**
 * The post [command] moves the selection to, from [selected] among [posts] (keys, top to bottom); with
 * nothing selected yet, the first moves to [firstVisible]. Null when it does not move.
 */
internal fun moveSelection(command: TimelineCommand, posts: List<String>, selected: String?, firstVisible: String?) =
    when {
        posts.isEmpty() -> null
        selected == null || selected !in posts -> firstVisible ?: posts.first()
        command == TimelineCommand.Next -> posts.getOrNull(posts.indexOf(selected) + 1)
        command == TimelineCommand.Previous -> posts.getOrNull(posts.indexOf(selected) - 1)
        else -> null
    }

/**
 * The list's key handler: J and K move [selected] (reported through [onSelect]) and scroll to it, the
 * other keys act on it. Moving takes the keyboard focus back to the list from a post's own button, so
 * Enter opens the selected post, never the one that had focus before. True when the key did something.
 */
@Composable
internal fun timelineKeyHandler(
    state: TimelineUiState,
    rowActions: StatusActions,
    listState: LazyListState,
    focus: FocusRequester,
    onCompose: (() -> Unit)?,
    selected: String?,
    onSelect: (String?) -> Unit,
): (KeyEvent) -> Boolean {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val move = { command: TimelineCommand ->
        val posts = state.items.filterIsInstance<TimelineItem.Post>().map { it.key }
        val first = (state.items.getOrNull(listState.firstVisibleItemIndex) as? TimelineItem.Post)?.key
        moveSelection(command, posts, selected, first)?.also { to ->
            onSelect(to)
            focus.requestFocus()
            scope.launch { listState.animateScrollToItem(state.items.indexOfFirst { it.key == to }) }
        } != null
    }
    val run = { command: TimelineCommand ->
        val row = state.items.firstNotNullOfOrNull {
            (it as? TimelineItem.Post)?.takeIf { p -> p.key == selected }
        }?.row
        when (command) {
            TimelineCommand.Next, TimelineCommand.Previous -> move(command)
            TimelineCommand.Compose -> onCompose?.invoke() != null
            TimelineCommand.Help -> activity?.requestShowKeyboardShortcuts() != null
            else -> row?.let { act(command, it, rowActions) } != null
        }
    }
    return { event -> timelineCommandOf(event)?.let(run) ?: false }
}

/** What a key does to the selected post. */
private fun act(command: TimelineCommand, row: StatusRowUi, actions: StatusActions) = when (command) {
    TimelineCommand.Open -> actions.onOpen(row.statusId)
    TimelineCommand.Favourite -> actions.onFavourite(row)
    TimelineCommand.Boost -> actions.onBoost(row)
    TimelineCommand.Reply -> actions.onReply(row)
    else -> Unit
}
