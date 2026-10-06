// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.profile.ListChoice
import social.aloha.core.data.profile.ProfileFeatured
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.data.search.Searches
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.PageOutcome
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.trouble
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.Filter
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.Relationship
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Story
import social.aloha.core.model.TimelineKey
import social.aloha.core.navigation.AccountKey
import social.aloha.core.network.ApiError
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.minuteTicks

/**
 * One account's profile as the reader sees it. The account is fetched when the profile opens; its
 * posts tabs are account timelines in the cache, so what was read before draws at once and actions
 * on a post show here and everywhere else it appears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
internal class ProfileViewModel @AssistedInject constructor(
    @Assisted private val key: AccountKey,
    accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val timelines: TimelineRepository,
    private val filters: FilterRepository,
    private val interactions: StatusInteractions,
    private val cache: RichTextCache,
    private val clock: Clock,
    private val featured: ProfileFeatured,
    searches: Searches,
) : ViewModel(),
    ProfileActions {
    @AssistedFactory
    interface Factory {
        fun create(key: AccountKey): ProfileViewModel
    }

    private data class Control(
        val loading: Boolean = true,
        val trouble: Trouble? = null,
        val gone: Boolean = false,
        val relationship: Relationship? = null,
        val highlights: ProfileHighlights? = null,
        val tab: ProfileTab = ProfileTab.Posts,
        val loadingOlder: Boolean = false,
        val reachedEnd: Set<ProfileTab> = emptySet(),
        val loadingGaps: Set<String> = emptySet(),
        val changing: Boolean = false,
        val collections: List<MediaCollection>? = null,
        val stories: List<Story>? = null,
        val actionFailed: Boolean = false,
        val lists: List<ListChoice>? = null,
        val familiar: List<Account> = emptyList(),
        val pinned: List<Status> = emptyList(),
        val featuredTags: List<FeaturedTag> = emptyList(),
    )

    /** What is drawn, as opposed to what the screen is doing. */
    private data class Inputs(
        val reader: SignedInAccount,
        val target: Account?,
        val stored: Stored,
        val filters: List<Filter>,
        val colors: RichTextColors,
    )

    /** Stored rows with the timeline they came from, so a tab never draws the one it replaced. */
    private data class Stored(val key: TimelineKey?, val rows: List<TimelineRow>)

    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val target = MutableStateFlow<Account?>(null)
    private val control = MutableStateFlow(Control())
    private val rows = StatusRowCache(cache)
    private val cursors = HashMap<ProfileTab, HttpUrl?>()

    @Volatile private var storedRows: List<TimelineRow> = emptyList()

    /** The header last built, parsed again only for another account or other colours. */
    private var header: Triple<Account, RichTextColors, ProfileHeader>? = null

    private val reader: StateFlow<SignedInAccount?> = accounts.accounts
        .map { all -> all.firstOrNull { it.id == key.readerId } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val tabKey: Flow<TimelineKey?> =
        // none until the account is known, so a profile that is never found still reaches the screen
        combine(target, control.map { it.tab }.distinctUntilChanged()) { account, tab ->
            account?.let { ProfilePresentation.timelineOf(it.id, tab) }
        }.distinctUntilChanged()

    private val stored: Flow<Stored> = combine(reader.filterNotNull(), tabKey) { reader, key -> reader to key }
        .flatMapLatest { (reader, key) ->
            (key?.let { timelines.observe(reader, it) } ?: flowOf(emptyList())).map { Stored(key, it) }
        }

    private val inputs: Flow<Inputs> = reader.filterNotNull().flatMapLatest { reader ->
        combine(target, stored, filters.observe(reader.id), colors.filterNotNull()) { target, stored, filters, colors ->
            Inputs(reader, target, stored, filters, colors)
        }
    }

    val uiState: StateFlow<ProfileUiState> = combine(inputs, control, minuteTicks(clock), ::state)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ProfileUiState())

    init {
        viewModelScope.launch { load(reader.filterNotNull().first()) }
    }

    /** The account's posts searched, where the reader's server searches one account's posts. */
    private val search = ProfileSearch(searches, viewModelScope)

    /**
     * What the search found, its rows from the profile's own row cache: each found post is acted on as the
     * posts on the tab are.
     */
    val searched: StateFlow<PostSearchUi> = combine(search.found, colors.filterNotNull()) { found, palette ->
        rows.use(palette)
        PostSearchUi(found.query, found.posts?.map { rows.rowFor(it, found.viewer, null) }, found.failed)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), PostSearchUi())

    fun onSearchPosts(query: String) = search.onQuery(reader.value, target.value?.id, query)

    /** Rows and the header are rendered with the theme's colours, which only the screen knows. */
    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    override fun onRefresh() {
        viewModelScope.launch { reader.value?.let { load(it, force = true) } }
    }

    override fun onTab(tab: ProfileTab) {
        if (tab == control.value.tab) return
        control.update { it.copy(tab = tab) }
        viewModelScope.launch { reader.value?.let { loadTab(it, tab, force = false) } }
    }

    /** Everything a next page needs; none while one loads, at the end, or before the profile is known. */
    private data class Older(val reader: SignedInAccount, val key: TimelineKey, val tab: ProfileTab, val oldest: String)

    private fun older(): Older? {
        val state = control.value.takeIf { !it.loadingOlder && it.tab !in it.reachedEnd } ?: return null
        val oldest = storedRows.lastOrNull { it is TimelineRow.Post }?.id ?: return null
        val key = target.value?.let { ProfilePresentation.timelineOf(it.id, state.tab) } ?: return null
        val reader = reader.value ?: return null
        return Older(reader, key, state.tab, oldest)
    }

    override fun onNearEnd() {
        val older = older() ?: return
        val tab = older.tab
        control.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val outcome = timelines.older(older.reader, older.key, cursors[tab], older.oldest)
            control.update { it.copy(loadingOlder = false) }
            when (outcome) {
                is PageOutcome.Loaded -> {
                    cursors[tab] = outcome.nextCursor
                    if (outcome.reachedEnd) control.update { it.copy(reachedEnd = it.reachedEnd + tab) }
                }

                is PageOutcome.Failed -> control.update { it.copy(trouble = outcome.error.trouble) }

                PageOutcome.Busy -> Unit
            }
        }
    }

    override fun onFillGap(gapId: String) {
        val index = storedRows.indexOfFirst { it.id == gapId }.takeIf { it >= 0 } ?: return
        val above = storedRows.subList(0, index).lastOrNull { it is TimelineRow.Post }?.id
        val reader = reader.value ?: return
        val key = target.value?.let { ProfilePresentation.timelineOf(it.id, control.value.tab) } ?: return
        control.update { it.copy(loadingGaps = it.loadingGaps + gapId) }
        viewModelScope.launch {
            val outcome = timelines.fillGap(reader, key, gapId, above)
            control.update {
                it.copy(
                    loadingGaps = it.loadingGaps - gapId,
                    trouble = (outcome as? PageOutcome.Failed)?.error?.trouble,
                )
            }
        }
    }

    override fun onChange(change: RelationshipChange) = relate { reader, id -> profiles.change(reader, id, change) }

    override fun onBlockDomain(block: Boolean) = relate { reader, id ->
        val domain = target.value?.acct?.substringAfter('@', "")?.ifEmpty { null }
        domain?.let { profiles.blockDomain(reader, id, it, block) } ?: Answer.Missed(ApiError.NotFound)
    }

    override fun onLists() {
        val reader = reader.value ?: return
        val account = target.value ?: return
        control.update { it.copy(lists = null) }
        viewModelScope.launch {
            val lists = profiles.lists(reader, account.id)
            control.update {
                if (lists is Answer.Got) {
                    it.copy(
                        lists = lists.value,
                    )
                } else {
                    it.copy(lists = emptyList(), actionFailed = true)
                }
            }
        }
    }

    /** Puts the account on list [listId] or takes it off, shown at once and put back if refused. */
    override fun onListed(listId: String, add: Boolean) {
        val reader = reader.value ?: return
        val account = target.value ?: return
        fun mark(member: Boolean) = control.update { state ->
            state.copy(lists = state.lists?.map { if (it.list.id == listId) it.copy(member = member) else it })
        }
        mark(add)
        viewModelScope.launch {
            if (profiles.setListed(reader, listId, account.id, add) is Answer.Missed) {
                mark(!add)
                control.update { it.copy(actionFailed = true) }
            }
        }
    }

    fun onToggle(statusId: String, toggle: Toggle) = act(statusId) { reader, status ->
        interactions.toggle(reader, status, toggle)
    }

    fun onVote(statusId: String, choices: List<Int>) = act(statusId) { reader, status ->
        interactions.vote(reader, status, choices)
    }

    fun onDelete(statusId: String) = act(statusId) { reader, status -> interactions.delete(reader, status) }

    fun onActionFailureShown() {
        control.update { it.copy(actionFailed = false) }
    }

    private fun act(statusId: String, block: suspend (SignedInAccount, Status) -> ApiError?) {
        val status = rows.statusFor(statusId) ?: return
        viewModelScope.launch {
            val reader = reader.value ?: return@launch
            if (block(reader, status) != null) control.update { it.copy(actionFailed = true) }
        }
    }

    private fun relate(change: suspend (SignedInAccount, String) -> Answer<Relationship>) {
        val reader = reader.value ?: return
        val account = target.value ?: return
        control.update { it.copy(changing = true) }
        viewModelScope.launch {
            val answer = change(reader, account.id)
            control.update {
                when (answer) {
                    is Answer.Got -> it.copy(changing = false, relationship = answer.value)
                    is Answer.Missed -> it.copy(changing = false, actionFailed = true)
                }
            }
        }
    }

    private suspend fun load(reader: SignedInAccount, force: Boolean = false) {
        control.update { it.copy(loading = true) }
        when (val answer = profiles.resolve(reader, key.id, key.acct)) {
            is Answer.Got -> {
                val account = answer.value
                target.value = account
                control.update { it.copy(loading = false, trouble = null) }
                if (account.id != reader.serverAccountId) {
                    viewModelScope.launch {
                        profiles.relationship(reader, account.id)?.let { r ->
                            control.update { it.copy(relationship = r) }
                        }
                    }
                    viewModelScope.launch {
                        val familiar = featured.familiarFollowers(reader, account.id)
                        control.update { it.copy(familiar = familiar) }
                    }
                }
                viewModelScope.launch {
                    profiles.highlights(reader, account.id)?.let { h -> control.update { it.copy(highlights = h) } }
                }
                viewModelScope.launch {
                    val pinned = (featured.pinned(reader, account.id) as? Answer.Got)?.value.orEmpty()
                    val tags = (featured.tags(reader, account.id) as? Answer.Got)?.value.orEmpty()
                    control.update { it.copy(pinned = pinned, featuredTags = tags) }
                }
                loadTab(reader, control.value.tab, force)
            }

            is Answer.Missed -> control.update {
                it.copy(loading = false, gone = answer.error == ApiError.NotFound, trouble = answer.error.trouble)
            }
        }
    }

    /** Fills the tab: a posts tab refreshes when stale (or when asked), the others fetch once. */
    private suspend fun loadTab(reader: SignedInAccount, tab: ProfileTab, force: Boolean) {
        val account = target.value ?: return
        val key = ProfilePresentation.timelineOf(account.id, tab)
        when {
            key != null -> refresh(reader, key, tab, force)

            tab == ProfileTab.Collections -> (profiles.collections(reader, account.id) as? Answer.Got)?.let { answer ->
                control.update { it.copy(collections = answer.value) }
            }

            tab == ProfileTab.Stories -> (profiles.stories(reader, account.id) as? Answer.Got)?.let { answer ->
                control.update { it.copy(stories = answer.value.filter { story -> story.isLive(clock.instant()) }) }
            }
        }
    }

    private suspend fun refresh(reader: SignedInAccount, key: TimelineKey, tab: ProfileTab, force: Boolean) {
        val last = timelines.lastFetched(reader, key)
        if (!force && last != null && clock.millis() - last < STALE_MILLIS) return
        val newest = timelines.observe(reader, key).first().firstOrNull { it is TimelineRow.Post }?.id
        when (val outcome = timelines.refresh(reader, key, RefreshPlan.of(last != null, newest))) {
            is PageOutcome.Loaded -> {
                if (last == null) cursors[tab] = outcome.nextCursor
                if (last == null && outcome.reachedEnd) control.update { it.copy(reachedEnd = it.reachedEnd + tab) }
            }

            is PageOutcome.Failed -> control.update { it.copy(trouble = outcome.error.trouble) }

            PageOutcome.Busy -> Unit
        }
    }

    private fun state(inputs: Inputs, control: Control, now: Instant): ProfileUiState {
        val reader = inputs.reader
        val account = inputs.target
        val current = account?.let { ProfilePresentation.timelineOf(it.id, control.tab) }
        val stored = inputs.stored.rows.takeIf { inputs.stored.key == current }.orEmpty()
        storedRows = stored
        rows.use(inputs.colors)
        val decide = ProfilePresentation.decider(inputs.filters, now, cache)
        return ProfileUiState(
            header = account?.let { headerOf(it, inputs.colors, reader) },
            relation = control.relationship?.let(ProfilePresentation::relation),
            highlights = control.highlights,
            tabs = ProfilePresentation.tabs(
                reader.capabilities,
                control.pinned.isNotEmpty() || control.featuredTags.isNotEmpty(),
            ),
            tab = control.tab,
            items = ProfilePresentation.items(stored, decide, { status, warning ->
                rows.rowFor(status, reader.serverAccountId, warning?.titles, warning?.keywords.orEmpty())
            }, control.loadingGaps),
            collections = control.collections,
            stories = control.stories,
            loading = control.loading,
            loadingOlder = control.loadingOlder,
            changing = control.changing,
            trouble = control.trouble,
            gone = control.gone,
            now = now,
            actionFailed = control.actionFailed,
            lists = control.lists,
            familiar = control.familiar.map { Familiar(it.id, it.bestDisplayName, it.avatar) },
            knownHandle = key.acct?.let { if (it.startsWith('@')) it else "@$it" },
            featured = ProfilePresentation.featured(control.pinned, control.featuredTags) {
                rows.rowFor(it, reader.serverAccountId, null)
            },
        )
    }

    private fun headerOf(account: Account, colors: RichTextColors, reader: SignedInAccount): ProfileHeader {
        header?.let { (built, with, shown) -> if (built === account && with == colors) return shown }
        return ProfilePresentation.header(account, cache, colors, account.id == reader.serverAccountId).also {
            header = Triple(account, colors, it)
        }
    }

    private companion object {
        val STALE_MILLIS = Duration.ofSeconds(60).toMillis()
        const val STOP_MILLIS = 5_000L
    }
}
