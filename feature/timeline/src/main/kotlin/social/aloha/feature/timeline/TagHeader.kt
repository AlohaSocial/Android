// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.tags.Hashtags
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Tag
import social.aloha.core.network.ApiError

/** What a hashtag's timeline says of the tag: whether it is followed, and the tags used with it. */
internal data class TagHeaderState(
    val following: Boolean? = null,
    val related: List<Tag> = emptyList(),
    /** The name normalises to nothing, or the server said it is no hashtag. */
    val notATag: Boolean = false,
    val failed: Boolean = false,
)

/** One hashtag as its timeline heads it: followed and unfollowed here, the tags used with it beside. */
@HiltViewModel(assistedFactory = TagHeaderViewModel.Factory::class)
internal class TagHeaderViewModel @AssistedInject constructor(
    @Assisted private val name: String,
    private val accounts: AccountRepository,
    private val hashtags: Hashtags,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(name: String): TagHeaderViewModel
    }

    private val state = MutableStateFlow(TagHeaderState())
    val uiState: StateFlow<TagHeaderState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.activeAccount.filterNotNull().first()
            reader = account
            show(hashtags.tag(account, name))
            state.update { it.copy(related = hashtags.related(account, name)) }
        }
    }

    fun onRetry() {
        val account = reader ?: return
        viewModelScope.launch { show(hashtags.tag(account, name)) }
    }

    fun onFollow() {
        val account = reader ?: return
        val following = state.value.following ?: return
        viewModelScope.launch { show(hashtags.follow(account, name, !following)) }
    }

    private fun show(answer: Answer<Tag>) = state.update {
        when (answer) {
            is Answer.Got -> it.copy(following = answer.value.following ?: false, failed = false)

            is Answer.Missed -> if (answer.error is ApiError.Unprocessable) {
                it.copy(
                    notATag = true,
                )
            } else {
                it.copy(failed = true)
            }
        }
    }
}

/** Above a hashtag's posts: follow or unfollow it, and the tags used with it, each opening its own. */
@Composable
internal fun TagHeader(name: String, onTag: (String) -> Unit) {
    val viewModel = hiltViewModel<TagHeaderViewModel, TagHeaderViewModel.Factory>(key = "tag-$name") { it.create(name) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagHeader(state, viewModel::onFollow, viewModel::onRetry, onTag)
}

@Composable
internal fun TagHeader(state: TagHeaderState, onFollow: () -> Unit, onRetry: () -> Unit, onTag: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs)) {
        when {
            state.notATag -> Text(
                stringResource(R.string.tag_not_a_tag),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            state.following == true -> OutlinedButton(onClick = onFollow) {
                Text(stringResource(R.string.tag_unfollow))
            }

            state.following == false -> Button(onClick = onFollow) { Text(stringResource(R.string.tag_follow)) }

            // whether it is followed is not known: offline, or the server did not answer
            state.failed -> TextButton(onClick = onRetry) { Text(stringResource(R.string.tag_retry)) }

            else -> Unit
        }
        if (state.failed && state.following != null) {
            Text(
                stringResource(R.string.tag_follow_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.related.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = AlohaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tag_related), style = MaterialTheme.typography.labelLarge)
                state.related.forEach { tag ->
                    AssistChip(onClick = { onTag(tag.name) }, label = { Text("#${tag.name}") })
                }
            }
        }
    }
}
