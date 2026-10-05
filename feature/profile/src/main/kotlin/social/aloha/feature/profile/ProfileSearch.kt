// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.search.Searches
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusRowUi

/** A search through one account's posts as shown: what is typed, and its rows; [rows] null until answered. */
@Immutable
internal data class PostSearchUi(
    val query: String = "",
    val rows: List<StatusRowUi>? = null,
    val failed: Boolean = false,
)

/** What a search found: the posts, null until answered, and the reader's own id, which tells their own. */
internal data class PostsFound(
    val query: String = "",
    val posts: List<Status>? = null,
    val failed: Boolean = false,
    val viewer: String = "",
)

/** One account's posts searched, as the reader types, a moment after typing stops. */
internal class ProfileSearch(private val searches: Searches, private val scope: CoroutineScope) {
    private val shown = MutableStateFlow(PostsFound())
    val found: StateFlow<PostsFound> = shown.asStateFlow()
    private var job: Job? = null

    fun onQuery(reader: SignedInAccount?, accountId: String?, query: String) {
        job?.cancel()
        shown.value = PostsFound(query, viewer = reader?.serverAccountId.orEmpty())
        if (reader == null || accountId == null || query.isBlank()) return
        job = scope.launch {
            delay(TYPING_MILLIS)
            val answer = searches.search(reader, query, accountId)
            val found = (answer as? Answer.Got)?.value?.statuses
            shown.value = shown.value.copy(posts = found ?: emptyList(), failed = found == null)
        }
    }

    private companion object {
        const val TYPING_MILLIS = 300L
    }
}

/**
 * The field over a profile's posts that searches them, and once something is typed, what it found in the
 * posts' place; false while nothing is typed, and the posts show as they are.
 */
internal fun LazyListScope.postSearch(
    searching: PostSearch,
    handle: String,
    now: Instant,
    rowActions: StatusActions,
): Boolean {
    val search = searching.state
    item(key = "search", contentType = "search") { SearchField(search.query, handle, searching.onQuery) }
    if (search.query.isBlank()) return false
    val rows = search.rows
    // what the search came to is said as it comes, the field keeping the focus
    val said = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when {
        rows == null -> item(key = "searching") { ListProgress() }

        search.failed -> item(key = "search-failed") {
            EmptyState(stringResource(R.string.profile_search_failed), said)
        }

        rows.isEmpty() -> item(key = "search-none") {
            EmptyState(stringResource(R.string.profile_search_none, search.query.trim()), said)
        }

        else -> items(rows, key = { "found:${it.rowId}" }) { row ->
            StatusCard(row, now, LocalSensitiveMediaPolicy.current, rowActions)
            PostDivider()
        }
    }
    return true
}

@Composable
private fun SearchField(query: String, handle: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        label = { Text(stringResource(R.string.profile_search, handle)) },
        leadingIcon = { Icon(AlohaIcons.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(AlohaIcons.Close, stringResource(R.string.profile_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
    )
}
