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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.tags.Hashtags
import social.aloha.core.data.tags.TagGroup
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Tag
import social.aloha.core.network.ApiError

@Immutable
internal data class HashtagsUiState(
    val followed: List<Tag> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val groups: List<TagGroup> = emptyList(),
    /** What the screen says once: something typed was no hashtag, a follow failed, or a name was taken. */
    val notice: HashtagsNotice? = null,
)

internal enum class HashtagsNotice { NotATag, FollowFailed, NameTaken }

/**
 * The hashtags the reader follows, followed from a name typed here and unfollowed with a tap, and the
 * reader's tag groups, kept on the device.
 */
@HiltViewModel(assistedFactory = HashtagsViewModel.Factory::class)
internal class HashtagsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val hashtags: Hashtags,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): HashtagsViewModel
    }

    private val state = MutableStateFlow(HashtagsUiState())
    val uiState: StateFlow<HashtagsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            launch { hashtags.groups(account).collect { groups -> state.update { it.copy(groups = groups) } } }
            load(account)
        }
    }

    fun onRetry() {
        reader?.let { viewModelScope.launch { load(it) } }
    }

    fun onFollow(name: String, follow: Boolean) = follow(name, follow)

    fun onSaveGroup(name: String, tags: List<String>, previous: String?) {
        val account = reader ?: return
        viewModelScope.launch {
            if (!hashtags.saveGroup(account, name, tags, previous)) {
                state.update {
                    it.copy(notice = HashtagsNotice.NameTaken)
                }
            }
        }
    }

    fun onDeleteGroup(name: String) {
        val account = reader ?: return
        viewModelScope.launch { hashtags.deleteGroup(account, name) }
    }

    fun onNoticeShown() {
        state.update { it.copy(notice = null) }
    }

    private fun follow(name: String, follow: Boolean) {
        val account = reader ?: return
        viewModelScope.launch {
            when (val answer = hashtags.follow(account, name, follow)) {
                is Answer.Got -> load(account)

                is Answer.Missed -> {
                    val notice = if (answer.error is ApiError.Unprocessable) {
                        HashtagsNotice.NotATag
                    } else {
                        HashtagsNotice.FollowFailed
                    }
                    state.update { it.copy(notice = notice) }
                }
            }
        }
    }

    private suspend fun load(account: SignedInAccount) {
        state.update { it.copy(loading = true, failed = false) }
        val answer = hashtags.followed(account)
        state.update {
            it.copy(
                followed = (answer as? Answer.Got)?.value ?: it.followed,
                loading = false,
                failed = answer is Answer.Missed,
            )
        }
    }
}
