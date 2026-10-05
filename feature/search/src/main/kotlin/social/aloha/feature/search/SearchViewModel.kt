// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.Trouble
import social.aloha.core.data.search.Searches
import social.aloha.core.data.trouble
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.SearchResults
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Tag
import social.aloha.core.navigation.SearchKey
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/** Where a pasted address led: straight to the one post or person it names. */
internal sealed interface Found {
    data class Post(val statusId: String) : Found

    data class Person(val accountId: String) : Found

    /** An address that names neither: it opens in the browser. */
    data class Web(val url: String) : Found
}

/** An account found, as its row shows it. */
@Immutable
internal data class SearchPerson(val author: StatusRowUi.AuthorUi, val emojis: List<CustomEmoji>)

@Immutable
internal data class SearchUiState(
    val query: String = "",
    val recent: List<String> = emptyList(),
    val accounts: List<SearchPerson> = emptyList(),
    val hashtags: List<Tag> = emptyList(),
    val posts: List<StatusRowUi> = emptyList(),
    /** What is typed has been asked about and not answered yet. */
    val searching: Boolean = false,
    /** A search was answered for the query shown. */
    val searched: Boolean = false,
    val trouble: Trouble? = null,
    val found: Found? = null,
) {
    val empty: Boolean get() = accounts.isEmpty() && hashtags.isEmpty() && posts.isEmpty()
}

/** An answer as shown: the query it answers, its rows mapped once, and what went wrong if it failed. */
private data class Shown(
    val asked: String = "",
    val accounts: List<SearchPerson> = emptyList(),
    val hashtags: List<Tag> = emptyList(),
    val posts: List<StatusRowUi> = emptyList(),
    val answered: Boolean = false,
    val trouble: Trouble? = null,
)

/**
 * Search as one account: what is typed is searched a moment after typing stops, the last results
 * staying until the new ones come. A submitted search is kept among the recent searches, and an
 * address that names exactly one post or person opens it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = SearchViewModel.Factory::class)
internal class SearchViewModel @AssistedInject constructor(
    @Assisted private val key: SearchKey,
    accounts: AccountRepository,
    private val searches: Searches,
    private val lookup: RemoteLookup,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: SearchKey): SearchViewModel
    }

    private val reader = MutableStateFlow<SignedInAccount?>(null)
    private val query = MutableStateFlow(key.query)
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val control = MutableStateFlow(SearchUiState())

    /** An address submitted to be opened once its answer comes. */
    private var opening: String? = null

    // what was asked and its answer: a newer query drops an older one on its way; shared, so a submit
    // reads the answer the screen shows rather than asking again
    private val answered: SharedFlow<Pair<String, Answer<SearchResults>?>> =
        combine(reader.filterNotNull(), query.debounce(TYPING_MILLIS).distinctUntilChanged(), ::Pair)
            .flatMapLatest { (account, typed) ->
                if (typed.isBlank()) {
                    flowOf(typed to null)
                } else {
                    flow<Pair<String, Answer<SearchResults>?>> { emit(typed to searches.search(account, typed)) }
                }
            }
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    // the rows are mapped once per answer, not on every key typed
    private val shown: Flow<Shown> = combine(answered, colors.filterNotNull()) { (asked, answer), palette ->
        val results = (answer as? Answer.Got)?.value
        val mapper = StatusRowMapper(cache, palette)
        val viewer = reader.value?.serverAccountId.orEmpty()
        Shown(
            asked = asked,
            accounts = results?.accounts.orEmpty().map { SearchPerson(mapper.author(it), it.emojis) },
            hashtags = results?.hashtags.orEmpty(),
            posts = results?.statuses.orEmpty().map { mapper.map(it, viewer) },
            answered = results != null,
            trouble = (answer as? Answer.Missed)?.error?.trouble,
        )
    }.flowOn(Dispatchers.Default)

    init {
        viewModelScope.launch { reader.value = accounts.byId(key.readerId) }
        viewModelScope.launch { answered.collect { (asked, answer) -> open(asked, answer) } }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        control,
        query,
        reader.filterNotNull().flatMapLatest { searches.recent(it) }.onStart { emit(emptyList()) },
        shown.onStart { emit(Shown()) },
    ) { state, typed, recent, shown ->
        // nothing typed shows nothing; while typing, what was found last stays until the new answer
        val showing = shown.takeIf { typed.isNotBlank() && it.asked.isNotBlank() } ?: Shown()
        state.copy(
            query = typed,
            recent = recent,
            accounts = showing.accounts,
            hashtags = showing.hashtags,
            posts = showing.posts,
            searching = typed.isNotBlank() && shown.asked != typed,
            searched = showing.answered,
            trouble = showing.trouble.takeIf { shown.asked == typed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), SearchUiState())

    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    fun onQuery(value: String) {
        query.value = value
    }

    /** Keeps [value] among the recent, and opens what an address names alone once it is answered. */
    fun onSubmit(value: String = query.value) {
        val account = reader.value ?: return
        val wanted = value.trim().takeIf { it.isNotEmpty() } ?: return
        query.value = value
        viewModelScope.launch { searches.remember(account, wanted) }
        if (!Searches.resolvable(wanted)) return
        opening = wanted
        // answered already, when the search ran before the submit
        answered.replayCache.lastOrNull()?.let { (asked, answer) -> open(asked, answer) }
    }

    private fun open(asked: String, answer: Answer<SearchResults>?) {
        val wanted = opening ?: return
        if (asked.trim() != wanted || answer == null) return
        opening = null
        val found = (answer as? Answer.Got)?.value?.let(::only)
        control.update { it.copy(found = found ?: Found.Web(wanted).takeIf { Searches.isAddress(wanted) }) }
    }

    /** Opens the person [handle] names, looked up directly; searched for as typed when it is not found. */
    fun onPerson(handle: String) {
        val account = reader.value ?: return
        viewModelScope.launch {
            val id = lookup.person(account, handle)
            if (id == null) onSubmit(handle) else control.update { it.copy(found = Found.Person(id)) }
        }
    }

    fun onFoundShown() {
        control.update { it.copy(found = null) }
    }

    fun onClearRecent() {
        val account = reader.value ?: return
        viewModelScope.launch { searches.clearRecent(account) }
    }

    /** The one post, else the one person, an address named; nothing when it named more, or none. */
    private fun only(results: SearchResults): Found? = when {
        results.statuses.size == 1 && results.accounts.isEmpty() -> Found.Post(results.statuses.single().id)
        results.accounts.size == 1 && results.statuses.isEmpty() -> Found.Person(results.accounts.single().id)
        else -> null
    }

    private companion object {
        const val TYPING_MILLIS = 300L
        const val STOP_MILLIS = 5_000L
    }
}
