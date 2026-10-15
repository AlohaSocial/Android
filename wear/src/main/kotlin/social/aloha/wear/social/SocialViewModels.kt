// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list the watch asked the server for: on its way, there, or not to be had. */
@Immutable
sealed interface Rows<out T> {
    data object Loading : Rows<Nothing>

    data class Loaded<T>(val items: List<T>) : Rows<T>

    data object Failed : Rows<Nothing>
}

/** A list loaded on opening and again on asking; a failed load says so rather than showing nothing. */
abstract class RowsViewModel<T>(private val load: suspend () -> List<T>?) : ViewModel() {
    private val state = MutableStateFlow<Rows<T>>(Rows.Loading)
    val uiState: StateFlow<Rows<T>> = state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        state.value = Rows.Loading
        viewModelScope.launch { state.value = load()?.let { Rows.Loaded(it) } ?: Rows.Failed }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(social: WatchSocial) : RowsViewModel<WatchPost>(social::home)

@HiltViewModel
class NotificationsViewModel @Inject constructor(social: WatchSocial) :
    RowsViewModel<WatchNotice>(social::notifications)

/** One post: [post] once loaded, whether a mark or a reply [failed], and whether a reply was [sent]. */
@Immutable
data class PostUiState(
    val post: WatchPost? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val sent: Boolean = false,
)

@HiltViewModel(assistedFactory = PostViewModel.Factory::class)
class PostViewModel @AssistedInject constructor(@Assisted private val id: String, private val social: WatchSocial) :
    ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(id: String): PostViewModel
    }

    private val state = MutableStateFlow(PostUiState())
    val uiState: StateFlow<PostUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val post = social.post(id)
            state.value = PostUiState(post = post, loading = false, failed = post == null)
        }
    }

    fun onToggle(mark: WatchSocial.Mark) {
        val post = state.value.post ?: return
        viewModelScope.launch {
            val now = social.toggle(post, mark)
            state.update { it.copy(post = now ?: it.post, failed = now == null) }
        }
    }

    fun onReply(text: String) {
        val post = state.value.post ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val sent = social.reply(post, text.trim())
            state.update { it.copy(sent = sent, failed = !sent) }
        }
    }
}
