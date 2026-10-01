// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.hashtags

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.tags.GroupCursor
import social.aloha.core.data.tags.Hashtags
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.navigation.TagGroupKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

@Immutable
internal data class TagGroupUiState(
    val tags: List<String> = emptyList(),
    val posts: List<Status> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val done: Boolean = false,
    val viewer: String = "",
)

/**
 * A tag group's posts: each hashtag's asked for at once, merged, each post once, newest first, and
 * paged on from where each tag's last page ended.
 */
@HiltViewModel(assistedFactory = TagGroupViewModel.Factory::class)
internal class TagGroupViewModel @AssistedInject constructor(
    @Assisted private val key: TagGroupKey,
    private val accounts: AccountRepository,
    private val hashtags: Hashtags,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: TagGroupKey): TagGroupViewModel
    }

    private val state = MutableStateFlow(TagGroupUiState())
    val uiState: StateFlow<TagGroupUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null
    private var cursor = GroupCursor()

    init {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId) ?: return@launch
            reader = account
            val tags = hashtags.groups(account).first().firstOrNull { it.name == key.name }?.tags.orEmpty()
            state.update { it.copy(tags = tags, viewer = account.serverAccountId) }
            more()
        }
    }

    /** The posts as rows, drawn in [colors]. */
    fun mapper(colors: RichTextColors): StatusRowMapper = StatusRowMapper(cache, colors)

    /** The next page, unless one is on its way or there is none. */
    fun onMore() {
        if (state.value.loading) return
        viewModelScope.launch { more() }
    }

    private suspend fun more() {
        val account = reader ?: return
        val now = state.value
        if (now.done || now.tags.isEmpty()) return state.update { it.copy(loading = false) }
        state.update { it.copy(loading = true, failed = false) }
        catchUp(account, now.tags)
    }

    // a page may hold everything back while one tag is still far behind; a few more rounds catch up
    private suspend fun catchUp(account: SignedInAccount, tags: List<String>) {
        var rounds = 0
        var settled = false
        while (!settled && rounds++ < CATCH_UP_ROUNDS) {
            when (val answer = hashtags.groupPage(account, tags, cursor)) {
                is Answer.Got -> {
                    cursor = answer.value.cursor
                    // each page is older than the last, so it goes below what is shown
                    state.update { current ->
                        current.copy(
                            posts = (current.posts + answer.value.posts).distinctBy { it.id },
                            loading = false,
                            done = answer.value.done,
                        )
                    }
                    settled = answer.value.posts.isNotEmpty() || answer.value.done
                }

                is Answer.Missed -> {
                    state.update { it.copy(loading = false, failed = true) }
                    settled = true
                }
            }
        }
    }

    private companion object {
        const val CATCH_UP_ROUNDS = 3
    }
}
