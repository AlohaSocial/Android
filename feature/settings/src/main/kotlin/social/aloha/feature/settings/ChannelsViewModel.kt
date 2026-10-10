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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.VideoChannel
import social.aloha.core.network.ApiError

/** A channel being made ([id] null) or renamed; its handle is fixed once made. */
@Immutable
internal data class ChannelDraft(
    val id: String? = null,
    val handle: String = "",
    val name: String = "",
    val description: String = "",
) {
    val isNew: Boolean get() = id == null

    /** A name, and for a new channel a handle of letters, digits and underscores the server can take. */
    val canSave: Boolean
        get() = name.isNotBlank() &&
            (!isNew || handle.trim().let { it.isNotEmpty() && it.length <= MAX_HANDLE && it.all(::handleChar) })

    private companion object {
        const val MAX_HANDLE = 64

        fun handleChar(c: Char) = c.isLetterOrDigit() || c == '_'
    }
}

@Immutable
internal data class ChannelsUiState(
    val status: PageStatus = PageStatus.Loading,
    val channels: List<VideoChannel> = emptyList(),
    val editing: ChannelDraft? = null,
    val saving: Boolean = false,
    /** Why the last save failed: the server's own words where it gave them, an empty string where it did not. */
    val saveFailure: String? = null,
)

/** What the channels page asks for. */
internal interface ChannelsActions {
    fun onNew()

    fun onEdit(channel: VideoChannel)

    fun onDraft(draft: ChannelDraft)

    fun onSave()

    fun onCancel()

    fun onRetry()
}

/**
 * [readerId]'s video channels: listed, made and renamed. A write answers with the channel it wrote, which
 * takes its place in the list, or joins it when new.
 */
@HiltViewModel(assistedFactory = ChannelsViewModel.Factory::class)
internal class ChannelsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
) : ViewModel(),
    ChannelsActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ChannelsViewModel
    }

    private val state = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun onRetry() = load()

    override fun onNew() = state.update { it.copy(editing = ChannelDraft(), saveFailure = null) }

    override fun onEdit(channel: VideoChannel) = state.update {
        it.copy(
            editing = ChannelDraft(channel.id, channel.handle, channel.name, channel.description),
            saveFailure = null,
        )
    }

    override fun onDraft(draft: ChannelDraft) = state.update { it.copy(editing = draft) }

    override fun onCancel() = state.update { it.copy(editing = null, saveFailure = null) }

    override fun onSave() {
        val draft = state.value.editing?.takeIf { it.canSave && !state.value.saving } ?: return
        state.update { it.copy(saving = true, saveFailure = null) }
        viewModelScope.launch {
            val account = accounts.byId(readerId)
            if (account == null) {
                state.update { it.copy(saving = false, saveFailure = "") }
                return@launch
            }
            val name = draft.name.trim()
            val answer = draft.id?.let { extras.updateChannel(account, it, name, draft.description.trim()) }
                ?: extras.createChannel(account, draft.handle.trim().removePrefix("@"), name, draft.description.trim())
            state.update {
                when (answer) {
                    is Answer.Got -> it.copy(
                        channels = it.channels.merged(answer.value),
                        editing = null,
                        saving = false,
                    )

                    is Answer.Missed -> it.copy(
                        saving = false,
                        saveFailure = (answer.error as? ApiError.Unprocessable)?.message.orEmpty(),
                    )
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
            when (val answer = extras.channels(account)) {
                is Answer.Got -> state.update { it.copy(status = PageStatus.Ready, channels = answer.value) }
                is Answer.Missed -> state.update { it.copy(status = PageStatus.Failed) }
            }
        }
    }
}

/** This list with [written] in place of the channels they replace, and those that are new at the end. */
private fun List<VideoChannel>.merged(written: List<VideoChannel>): List<VideoChannel> {
    val byId = written.associateBy { it.id }
    return map { byId[it.id] ?: it } + written.filter { new -> none { it.id == new.id } }
}
