// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.saved

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.saved.PostPage
import social.aloha.core.data.saved.SavedPosts
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.navigation.SavedKind
import social.aloha.core.navigation.SavedPostsKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

@Immutable
internal data class SavedPostsUiState(
    val posts: List<Status> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val done: Boolean = false,
    val viewer: String = "",
    /** An unarchive the server refused. */
    val unarchiveFailed: Boolean = false,
)

/** The reader's bookmarked, favourited or archived posts, in the server's order, a page at a time. */
@HiltViewModel(assistedFactory = SavedPostsViewModel.Factory::class)
internal class SavedPostsViewModel @AssistedInject constructor(
    @Assisted private val key: SavedPostsKey,
    private val accounts: AccountRepository,
    private val saved: SavedPosts,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: SavedPostsKey): SavedPostsViewModel
    }

    private val state = MutableStateFlow(SavedPostsUiState())
    val uiState: StateFlow<SavedPostsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null
    private var last: PostPage? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId) ?: return@launch
            reader = account
            state.update { it.copy(viewer = account.serverAccountId) }
            more()
        }
    }

    fun mapper(colors: RichTextColors): StatusRowMapper = StatusRowMapper(cache, colors)

    /** The next page, unless one is on its way or there is none. */
    fun onMore() {
        if (state.value.loading && state.value.posts.isNotEmpty()) return
        viewModelScope.launch { more() }
    }

    /** Back on the profile; off this list at once, and back where it was if the server refuses. */
    fun onUnarchive(id: String) {
        val account = reader ?: return
        val shown = state.value.posts
        val index = shown.indexOfFirst { it.id == id }
        if (index < 0) return
        val post = shown[index]
        state.update { it.copy(posts = it.posts.filter { kept -> kept.id != id }) }
        viewModelScope.launch {
            if (saved.unarchive(account, id) is Answer.Missed) {
                // only this one comes back: another put back meanwhile stays put back
                state.update { now ->
                    val back = now.posts.toMutableList().apply { add(index.coerceAtMost(size), post) }
                    now.copy(posts = back, unarchiveFailed = true)
                }
            }
        }
    }

    fun onUnarchiveFailureShown() {
        state.update { it.copy(unarchiveFailed = false) }
    }

    private suspend fun more() {
        val account = reader ?: return
        if (state.value.done) return
        state.update { it.copy(loading = true, failed = false) }
        val after = last
        val answer = when (key.kind) {
            SavedKind.Bookmarks -> saved.bookmarks(account, after)
            SavedKind.Favourites -> saved.favourites(account, after)
            SavedKind.Archived -> saved.archived(account, after)
        }
        when (answer) {
            is Answer.Got -> {
                last = answer.value
                state.update { current ->
                    current.copy(
                        posts = (current.posts + answer.value.posts).distinctBy { it.id },
                        loading = false,
                        done = answer.value.done,
                    )
                }
            }

            is Answer.Missed -> state.update { it.copy(loading = false, failed = true) }
        }
    }
}
