// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.FilterEditKey
import social.aloha.core.network.endpoints.FilterDraft
import social.aloha.core.network.endpoints.KeywordDraft

/** How long a filter applies: as it is, for good, or for a while from now. */
internal sealed interface Expiry {
    /** The expiry the filter already has, [at], which may have run out: left to the server as it is. */
    data class Keep(val at: Instant) : Expiry

    data object Never : Expiry

    data class In(val seconds: Long) : Expiry
}

@Immutable
internal data class FilterEditUiState(
    val title: String = "",
    /** Every keyword, the removed ones too: those are sent so the server drops them. */
    val keywords: List<KeywordDraft> = emptyList(),
    val contexts: Set<FilterContext> = DEFAULT_CONTEXTS,
    val action: FilterAction = FilterAction.Warn,
    val expiry: Expiry = Expiry.Never,
    /** The expiry the filter came with, which can be chosen again after another was picked. */
    val keptExpiry: Instant? = null,
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** The filter could not be loaded: there is nothing to change. */
    val loadFailed: Boolean = false,
    /** What the server refused last, a save or a delete, which the screen says once. */
    val refused: Refused? = null,
    /** Saved or deleted: the editor closes. */
    val done: Boolean = false,
) {
    val shownKeywords: List<KeywordDraft> get() = keywords.filterNot { it.destroy }

    /** A filter needs a name and somewhere to apply. */
    val canSave: Boolean get() = title.isNotBlank() && contexts.isNotEmpty() && !saving

    companion object {
        val DEFAULT_CONTEXTS = setOf(FilterContext.Home, FilterContext.Public, FilterContext.Thread)
    }
}

/**
 * One filter, new or changed: its name, its keywords (each matched as a whole word or anywhere),
 * where it applies, whether it hides or warns, and for how long. Saved to the server, whose answer is
 * kept on the device, so it applies to what is already loaded as soon as it is saved.
 */
@HiltViewModel(assistedFactory = FilterEditViewModel.Factory::class)
internal class FilterEditViewModel @AssistedInject constructor(
    @Assisted private val key: FilterEditKey,
    private val accounts: AccountRepository,
    private val filters: FilterRepository,
) : ViewModel(),
    FilterEditActions {
    @AssistedFactory
    interface Factory {
        fun create(key: FilterEditKey): FilterEditViewModel
    }

    private val state =
        MutableStateFlow(FilterEditUiState(isNew = key.filterId == null, loading = key.filterId != null))
    val uiState: StateFlow<FilterEditUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId) ?: return@launch
            reader = account
            val id = key.filterId ?: return@launch
            // the one kept on the device, else the server's: one made elsewhere may not be kept yet
            val filter = filters.observe(account.id).first().firstOrNull { it.id == id }
                ?: (filters.get(account, id) as? Answer.Got)?.value
            state.update { filter?.editing() ?: it.copy(loading = false, loadFailed = true) }
        }
    }

    override fun onTitle(title: String) = state.update { it.copy(title = title) }

    /** Adds a keyword, unless it is blank or already there. */
    override fun onAddKeyword(keyword: String, wholeWord: Boolean) = state.update { now ->
        val word = keyword.trim()
        if (word.isEmpty() || now.shownKeywords.any { it.keyword.equals(word, ignoreCase = true) }) {
            now
        } else {
            now.copy(keywords = now.keywords + KeywordDraft(word, wholeWord = wholeWord))
        }
    }

    override fun onWholeWord(index: Int, wholeWord: Boolean) = changeShown(index) { it.copy(wholeWord = wholeWord) }

    /** A keyword the server has is sent as removed; one only typed here just goes. */
    override fun onRemoveKeyword(index: Int) = changeShown(index) { word -> word.id?.let { word.copy(destroy = true) } }

    override fun onContext(context: FilterContext, on: Boolean) =
        state.update { it.copy(contexts = if (on) it.contexts + context else it.contexts - context) }

    override fun onAction(action: FilterAction) = state.update { it.copy(action = action) }

    override fun onExpiry(expiry: Expiry) = state.update { it.copy(expiry = expiry) }

    override fun onSave() {
        val account = reader ?: return
        val now = state.value
        if (!now.canSave) return
        state.update { it.copy(saving = true, refused = null) }
        viewModelScope.launch {
            val answer = filters.save(account, key.filterId, now.draft())
            state.update {
                it.copy(
                    saving = false,
                    refused = Refused.Save.takeIf {
                        answer is Answer.Missed
                    },
                    done = answer is Answer.Got,
                )
            }
        }
    }

    override fun onDelete() {
        val account = reader ?: return
        val id = key.filterId ?: return
        viewModelScope.launch {
            val answer = filters.delete(account, id)
            state.update {
                it.copy(refused = Refused.Delete.takeIf { answer is Answer.Missed }, done = answer is Answer.Got)
            }
        }
    }

    override fun onFailureShown() = state.update { it.copy(refused = null) }

    private fun changeShown(index: Int, change: (KeywordDraft) -> KeywordDraft?) = state.update { now ->
        val target = now.shownKeywords.getOrNull(index) ?: return@update now
        now.copy(keywords = now.keywords.mapNotNull { if (it === target) change(it) else it })
    }
}

/**
 * The editor's start for [this] filter. Its expiry, run out or not, is kept unless another is chosen, and
 * its action too: blurring, which this app offers only to a filter that has it, stays blurring.
 */
internal fun Filter.editing() = FilterEditUiState(
    title = title,
    keywords = keywords.map { KeywordDraft(it.keyword, id = it.id, wholeWord = it.wholeWord) },
    contexts = context.filter { it != FilterContext.Unknown }.toSet(),
    action = filterAction.takeIf { it in EDITABLE_ACTIONS } ?: FilterAction.Warn,
    expiry = expiresAt?.let(Expiry::Keep) ?: Expiry.Never,
    keptExpiry = expiresAt,
    isNew = false,
)

/** What the server is sent; an expiry kept as it is is not sent, so the server keeps it. */
internal fun FilterEditUiState.draft() = FilterDraft(
    title = title.trim(),
    context = contexts.toList(),
    action = action,
    expiresInSeconds = (expiry as? Expiry.In)?.seconds,
    keywords = keywords,
    keepExpiry = expiry is Expiry.Keep,
)

/** What the server refused: a save, or a delete. */
internal enum class Refused { Save, Delete }

private val EDITABLE_ACTIONS = setOf(FilterAction.Warn, FilterAction.Hide, FilterAction.Blur)
