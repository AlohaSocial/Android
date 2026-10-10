// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.data.previewText
import social.aloha.core.model.WeeklyRecap

/** A post from this day in an earlier year, as a line: how many years back, and what it said. */
@Immutable
internal data class Memory(val statusId: String, val yearsAgo: Int, val text: String, val attachments: Int)

@Immutable
internal data class LookingBackUiState(
    val status: PageStatus = PageStatus.Loading,
    val recap: WeeklyRecap? = null,
    val memories: List<Memory> = emptyList(),
    val changeFailed: Boolean = false,
)

/** What the looking-back page asks for. */
internal interface LookingBackActions {
    fun onRecap(enabled: Boolean)

    fun onRetry()

    fun onChangeFailureShown()
}

/** [readerId]'s posts from this day in other years, and the weekly recap with its switch. */
@HiltViewModel(assistedFactory = LookingBackViewModel.Factory::class)
internal class LookingBackViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
) : ViewModel(),
    LookingBackActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): LookingBackViewModel
    }

    private val state = MutableStateFlow(LookingBackUiState())
    private var switching: Job? = null
    val uiState: StateFlow<LookingBackUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun onRetry() = load()

    override fun onChangeFailureShown() = state.update { it.copy(changeFailed = false) }

    override fun onRecap(enabled: Boolean) {
        val before = state.value.recap ?: return
        // the switch moves at once; the counts come once it is on, and it goes back if the server refused.
        // A later tap takes over from an earlier one still under way, so only the last one is answered.
        state.update { it.copy(recap = before.copy(enabled = enabled)) }
        switching?.cancel()
        switching = viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            if (extras.setRecap(account, enabled) is Answer.Missed) {
                state.update { it.copy(recap = before, changeFailed = true) }
                return@launch
            }
            if (enabled) {
                (extras.recap(account) as? Answer.Got)?.let { got ->
                    state.update { it.copy(recap = got.value) }
                }
            }
        }
    }

    private fun load() {
        state.update { it.copy(status = PageStatus.Loading) }
        viewModelScope.launch {
            val account = accounts.byId(readerId)
            val blocked = pageStatusOf(account)
            if (account == null || blocked != null) {
                state.update { it.copy(status = blocked ?: PageStatus.Failed) }
                return@launch
            }
            val memories = async { extras.onThisDay(account) }
            val recap = extras.recap(account)
            val posts = memories.await()
            if (recap !is Answer.Got || posts !is Answer.Got) {
                state.update { it.copy(status = PageStatus.Failed) }
                return@launch
            }
            val now = ZonedDateTime.now()
            state.update {
                it.copy(
                    status = PageStatus.Ready,
                    recap = recap.value,
                    memories = posts.value.sortedByDescending { post -> post.createdAt }.map { post ->
                        val shown = post.displayed
                        val years = ChronoUnit.YEARS.between(post.createdAt.atZone(now.zone), now).toInt()
                        Memory(
                            post.id,
                            years.coerceAtLeast(1),
                            shown.previewText().orEmpty(),
                            shown.mediaAttachments.size,
                        )
                    },
                )
            }
        }
    }
}
