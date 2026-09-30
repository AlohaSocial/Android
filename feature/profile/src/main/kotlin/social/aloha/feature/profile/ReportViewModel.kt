// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

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
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.data.profile.Reports
import social.aloha.core.model.InstanceRule
import social.aloha.core.navigation.ReportKey
import social.aloha.core.network.endpoints.ReportDraft

/** What a report is about, as moderators sort them. */
internal enum class ReportCategory(val wire: String) {
    Spam("spam"),
    Legal("legal"),
    Violation("violation"),
    Other("other"),
}

/** What the report's controls do. */
internal interface ReportActions {
    fun onCategory(category: ReportCategory)

    fun onRule(id: String, broken: Boolean)

    fun onComment(comment: String)

    fun onForward(forward: Boolean)

    fun onSend()

    fun onMute()

    fun onBlock()
}

@Immutable
internal data class ReportUiState(
    val category: ReportCategory? = null,
    val rules: List<InstanceRule> = emptyList(),
    val broken: Set<String> = emptySet(),
    val comment: String = "",
    /** Whether a copy goes to the account's own server too, for a remote account; only when chosen. */
    val forward: Boolean = false,
    val sending: Boolean = false,
    val failed: Boolean = false,
    val sent: Boolean = false,
    /** What the reader did about the account after reporting it. */
    val muted: Boolean = false,
    val blocked: Boolean = false,
) {
    /** A category, and the rules it names when it is one of them. */
    val canSend: Boolean
        get() = category != null && !sending && (category != ReportCategory.Violation || broken.isNotEmpty())
}

/**
 * A report about one account, and the post it was opened from when there is one. Once it is sent,
 * muting and blocking the account are offered, since a report alone changes nothing the reader sees.
 */
@HiltViewModel(assistedFactory = ReportViewModel.Factory::class)
internal class ReportViewModel @AssistedInject constructor(
    @Assisted private val key: ReportKey,
    private val accounts: AccountRepository,
    private val reports: Reports,
    private val profiles: ProfileRepository,
) : ViewModel(),
    ReportActions {
    @AssistedFactory
    interface Factory {
        fun create(key: ReportKey): ReportViewModel
    }

    private val state = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val rules = reader()?.let { reports.rules(it) }
            if (rules is Answer.Got) state.update { it.copy(rules = rules.value) }
        }
    }

    override fun onCategory(category: ReportCategory) = state.update { it.copy(category = category) }

    override fun onRule(id: String, broken: Boolean) =
        state.update { it.copy(broken = if (broken) it.broken + id else it.broken - id) }

    override fun onComment(comment: String) = state.update { it.copy(comment = comment.take(COMMENT_LIMIT)) }

    override fun onForward(forward: Boolean) = state.update { it.copy(forward = forward) }

    override fun onSend() {
        val current = state.value
        val category = current.category ?: return
        state.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val draft = ReportDraft(
                accountId = key.accountId,
                comment = current.comment.trim(),
                category = category.wire,
                statusIds = listOfNotNull(key.statusId),
                ruleIds = if (category == ReportCategory.Violation) current.broken.toList() else emptyList(),
                forward = key.remote && current.forward,
            )
            val answer = reader()?.let { reports.send(it, draft) }
            state.update { it.copy(sending = false, sent = answer is Answer.Got, failed = answer !is Answer.Got) }
        }
    }

    override fun onMute() = relate(RelationshipChange.Mute()) { it.copy(muted = true) }

    override fun onBlock() = relate(RelationshipChange.Block) { it.copy(blocked = true) }

    private fun relate(change: RelationshipChange, done: (ReportUiState) -> ReportUiState) {
        viewModelScope.launch {
            val answer = reader()?.let { profiles.change(it, key.accountId, change) }
            state.update { if (answer is Answer.Got) done(it) else it.copy(failed = true) }
        }
    }

    private suspend fun reader() = accounts.byId(key.readerId)

    private companion object {
        /** Mastodon's limit on a report's comment. */
        const val COMMENT_LIMIT = 1_000
    }
}
