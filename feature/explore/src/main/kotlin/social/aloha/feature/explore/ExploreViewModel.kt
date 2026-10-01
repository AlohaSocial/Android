// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

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
import social.aloha.core.data.Trouble
import social.aloha.core.data.explore.Explore
import social.aloha.core.data.explore.People
import social.aloha.core.data.trouble
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Tag
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

/** The parts of Explore, one tab each. */
internal enum class ExploreTab { Posts, Hashtags, News, People, Directory }

/** Something Explore loads: on its way, loaded, or not, and why. */
internal sealed interface Load<out T> {
    data object Waiting : Load<Nothing>

    data class Loaded<T>(val value: T) : Load<T>

    data class Failed(val trouble: Trouble) : Load<Nothing>
}

/** The directory as far as it was paged, in [order]; [end] once the server sent a short page. */
@Immutable
internal data class Directory(
    val order: DirectoryOrder = DirectoryOrder.Active,
    val accounts: List<Account> = emptyList(),
    /** Where the next page starts: as many as the server sent, which the accounts read may be fewer than. */
    val offset: Int = 0,
    val loading: Boolean = false,
    val end: Boolean = false,
    val trouble: Trouble? = null,
)

@Immutable
internal data class ExploreUiState(
    val tab: ExploreTab = ExploreTab.Posts,
    val posts: Load<List<Status>> = Load.Waiting,
    val hashtags: Load<List<Tag>> = Load.Waiting,
    val period: String = Explore.PERIODS[2],
    /** Whether the server measures trending hashtags over a period the reader picks: Nextcloud Social. */
    val periods: Boolean = false,
    val news: Load<List<Card>> = Load.Waiting,
    val people: Load<People> = Load.Waiting,
    /** The starter packs followed here, which say so rather than offer it again. */
    val followed: Set<String> = emptySet(),
    val directory: Directory = Directory(),
    val viewer: String = "",
)

/**
 * Explore as one account: each tab loads the first time it is shown, and again on a retry. A
 * suggestion dismissed goes at once, and comes back if the server would not forget it.
 */
@HiltViewModel(assistedFactory = ExploreViewModel.Factory::class)
internal class ExploreViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val explore: Explore,
    private val cache: RichTextCache,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ExploreViewModel
    }

    private val state = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            state.update { it.copy(periods = account.capabilities.isNextcloudSocial, viewer = account.serverAccountId) }
            // the tab shown by now, which may have been picked before the account was known
            load(state.value.tab)
        }
    }

    /** The posts as rows, drawn in [colors]. */
    fun mapper(colors: RichTextColors): StatusRowMapper = StatusRowMapper(cache, colors)

    fun onTab(tab: ExploreTab) {
        state.update { it.copy(tab = tab) }
        if (waiting(tab)) load(tab)
    }

    fun onRetry() = load(state.value.tab)

    fun onPeriod(period: String) {
        state.update { it.copy(period = period, hashtags = Load.Waiting) }
        load(ExploreTab.Hashtags)
    }

    fun onOrder(order: DirectoryOrder) {
        state.update { it.copy(directory = Directory(order = order)) }
        moreDirectory()
    }

    /** The next page of the directory, unless one is on its way or there is none. */
    fun moreDirectory() {
        val account = reader ?: return
        val now = state.value.directory
        if (now.loading || now.end) return
        state.update { it.copy(directory = now.copy(loading = true, trouble = null)) }
        viewModelScope.launch {
            val answer = explore.directory(account, now.order, now.offset)
            state.update { current ->
                if (current.directory.order != now.order) return@update current
                val page = (answer as? Answer.Got)?.value
                current.copy(
                    // an account that moved up while paging comes once: a key used twice crashes the list
                    directory = current.directory.copy(
                        accounts = (current.directory.accounts + page?.accounts.orEmpty()).distinctBy { it.id },
                        offset = current.directory.offset + (page?.sent ?: 0),
                        loading = false,
                        end = page?.done == true,
                        trouble = (answer as? Answer.Missed)?.error?.trouble,
                    ),
                )
            }
        }
    }

    fun onDismiss(accountId: String) {
        val account = reader ?: return
        val shown = (state.value.people as? Load.Loaded)?.value?.suggestions ?: return
        val index = shown.indexOfFirst { it.id == accountId }
        if (index < 0) return
        val dismissed = shown[index]
        people { it.copy(suggestions = it.suggestions.filterNot { s -> s.id == accountId }) }
        viewModelScope.launch {
            // the server would not forget them: they are suggested again, where they were, and only they
            if (explore.dismiss(account, accountId) is Answer.Missed) {
                people { now ->
                    val back = now.suggestions.toMutableList().apply { add(index.coerceAtMost(size), dismissed) }
                    now.copy(suggestions = back)
                }
            }
        }
    }

    private fun people(change: (People) -> People) = state.update { now ->
        val loaded = (now.people as? Load.Loaded)?.value ?: return@update now
        now.copy(people = Load.Loaded(change(loaded)))
    }

    fun onFollowAll(slug: String) {
        val account = reader ?: return
        viewModelScope.launch {
            if (explore.followAll(account, slug) is Answer.Got) state.update { it.copy(followed = it.followed + slug) }
        }
    }

    private fun waiting(tab: ExploreTab): Boolean = when (tab) {
        ExploreTab.Posts -> state.value.posts !is Load.Loaded
        ExploreTab.Hashtags -> state.value.hashtags !is Load.Loaded
        ExploreTab.News -> state.value.news !is Load.Loaded
        ExploreTab.People -> state.value.people !is Load.Loaded
        ExploreTab.Directory -> state.value.directory.accounts.isEmpty()
    }

    private fun load(tab: ExploreTab) {
        val account = reader ?: return
        if (tab == ExploreTab.Directory) return moreDirectory()
        viewModelScope.launch {
            when (tab) {
                ExploreTab.Posts -> state.update { it.copy(posts = explore.posts(account).load()) }

                ExploreTab.Hashtags -> {
                    val period = state.value.period
                    val answer = explore.hashtags(account, period).load()
                    state.update { if (it.period == period) it.copy(hashtags = answer) else it }
                }

                ExploreTab.News -> state.update { it.copy(news = explore.links(account).load()) }

                ExploreTab.People -> state.update { it.copy(people = explore.people(account).load()) }

                ExploreTab.Directory -> Unit
            }
        }
    }
}

private fun <T> Answer<T>.load(): Load<T> = when (this) {
    is Answer.Got -> Load.Loaded(value)
    is Answer.Missed -> Load.Failed(error.trouble)
}
