// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.explore.Explore
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource

/** The home screen's shelves, in the order they are shown. */
internal enum class ShelfKind { ContinueWatching, HomeVideo, Shorts, Following, FederatedVideo, Trending }

/** A shelf of videos, or of people for [ShelfKind.Following]. */
@Immutable
internal data class Shelf(
    val kind: ShelfKind,
    val videos: List<VideoCardUi> = emptyList(),
    val people: List<PersonCardUi> = emptyList(),
    /** Whether its next page is on its way. */
    val loading: Boolean = false,
)

@Immutable
internal data class HomeUiState(val shelves: List<Shelf> = emptyList(), val loadedOnce: Boolean = false)

/** Where the home screen sends the reader. */
internal class HomeLinks(
    val onVideo: (VideoCardUi) -> Unit,
    // the Shorts shelf opens the shorts pager there, every other shelf the watch page
    val onShort: (VideoCardUi) -> Unit,
    val onPerson: (PersonCardUi) -> Unit,
    val onSearch: () -> Unit,
    val onAccount: () -> Unit,
)

/** The shelves read from a timeline: they page further as the reader nears their end. */
private enum class TimelineShelf(val kind: ShelfKind, val mode: FeedMode, val source: TimelineSource) {
    HomeVideo(ShelfKind.HomeVideo, FeedMode.Video, TimelineSource.Home),
    Shorts(ShelfKind.Shorts, FeedMode.Shorts, TimelineSource.Home),
    FederatedVideo(ShelfKind.FederatedVideo, FeedMode.Video, TimelineSource.Federated),
    ;

    val key: TimelineKey get() = TimelineKey(mode, source)

    fun servedBy(reader: SignedInAccount): Boolean = source != TimelineSource.Federated ||
        reader.capabilities.federatedFeed
}

/** Whether the first timeline came in, and which shelves are fetching their next page. */
private data class Paging(val loadedOnce: Boolean, val loading: Set<ShelfKind>)

/** What a shelf that is not a timeline holds, as last asked. */
private data class Fetched(
    val continueWatching: List<Status> = emptyList(),
    val trending: List<Status> = emptyList(),
    val following: List<PersonCardUi> = emptyList(),
    val followingNext: HttpUrl? = null,
)

/**
 * The TV's home: shelves of the videos and shorts of the people followed, those the reader is part way
 * through, the people followed as channels, everyone's videos and what is trending. The timelines are the
 * phone's video and shorts timelines, narrowed by the server where it can and by the post's kind where it
 * cannot; their shelves page as the reader nears their end. Asking again on every return to the screen
 * keeps Continue Watching right after a video.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    timelines: TimelineRepository,
    private val watching: WatchPositions,
    private val explore: Explore,
    private val profiles: ProfileRepository,
    private val sensitive: SensitiveMedia,
    clock: Clock,
) : ViewModel() {
    private val reader: StateFlow<SignedInAccount?> = accounts.activeAccount
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pagers = TimelineShelf.entries.associateWith { shelf ->
        TimelinePager(timelines, clock, viewModelScope) { reader.value?.let { it to shelf.key } }
    }

    private val fetched = MutableStateFlow(Fetched())
    private val loadingPeople = MutableStateFlow(false)
    private var people: Job? = null

    private val paging = combine(TimelineShelf.entries.map { pagers.getValue(it).states }) { states ->
        val loading = TimelineShelf.entries.filterIndexed { index, _ -> states[index].loadingOlder }.map { it.kind }
        Paging(states[TimelineShelf.HomeVideo.ordinal].loadedOnce, loading.toSet())
    }.combine(loadingPeople) { paging, people ->
        if (people) paging.copy(loading = paging.loading + ShelfKind.Following) else paging
    }

    val uiState: StateFlow<HomeUiState> = reader.filterNotNull().flatMapLatest { reader ->
        val rows = TimelineShelf.entries.map { shelf ->
            if (shelf.servedBy(reader)) pagers.getValue(shelf).observe(reader, shelf.key) else flowOf(emptyList())
        }
        combine(
            combine(rows) { it.toList() },
            sensitive.policy(reader.id),
            watching.observe(reader.id),
            fetched,
            paging,
        ) { timelineRows, policy, progress, fetched, paging ->
            val apiBase = reader.capabilities.apiBase
            val cards = { statuses: List<Status> ->
                statuses.mapNotNull { it.toCard(apiBase, policy, progress[it.displayed.id]) }.distinctBy { it.id }
            }
            HomeUiState(shelves(timelineRows, fetched, paging.loading, cards), paging.loadedOnce)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), HomeUiState())

    init {
        viewModelScope.launch {
            reader.filterNotNull().collect { reader ->
                // a page of the previous account's people still on its way never lands on this one's shelf
                people?.cancel()
                loadingPeople.value = false
                fetched.value = Fetched()
                sensitive.refresh(reader)
                TimelineShelf.entries.filter { it.servedBy(reader) }
                    .forEach { pagers.getValue(it).start(reader, it.key) }
                fetch(reader)
            }
        }
    }

    /** The screen is shown again: what changed meanwhile, Continue Watching above all, is asked for. */
    fun onShown() {
        val reader = reader.value ?: return
        viewModelScope.launch {
            TimelineShelf.entries.filter { it.servedBy(reader) }
                .forEach { pagers.getValue(it).refreshIfStale(reader, it.key) }
            continueWatching(reader)
        }
    }

    /** The reader neared the end of [kind]'s shelf: its next page. */
    fun onNearEnd(kind: ShelfKind) {
        TimelineShelf.entries.firstOrNull { it.kind == kind }?.let { pagers.getValue(it).older() }
        if (kind == ShelfKind.Following) morePeople()
    }

    private suspend fun fetch(reader: SignedInAccount) {
        continueWatching(reader)
        val trending = (explore.posts(reader) as? Answer.Got)?.value.orEmpty().filter(::isVideo)
        publish(reader) { it.copy(trending = trending) }
        morePeople()
    }

    private suspend fun continueWatching(reader: SignedInAccount) {
        if (!reader.capabilities.watchPositions) return
        val watched = (watching.continueWatching(reader) as? Answer.Got)?.value.orEmpty().map { it.status }
        publish(reader) { it.copy(continueWatching = watched) }
    }

    private fun morePeople() {
        val reader = reader.value ?: return
        val now = fetched.value
        if (loadingPeople.value || (now.following.isNotEmpty() && now.followingNext == null)) return
        loadingPeople.value = true
        people = viewModelScope.launch {
            val page = profiles.people(reader, reader.serverAccountId, followers = false, next = now.followingNext)
            if (page is Answer.Got) {
                val people = page.value.accounts.map { it.toPerson() }
                publish(reader) { it.copy(following = it.following + people, followingNext = page.value.next) }
            }
            loadingPeople.value = false
        }
    }

    /**
     * Applies an answer asked for [asker], unless the active account changed meanwhile: one account's watching,
     * trending or people never land on another's shelves.
     */
    private fun publish(asker: SignedInAccount, change: (Fetched) -> Fetched) {
        if (reader.value?.id == asker.id) fetched.update(change)
    }

    /** Every shelf with something on it, in order; [cards] turns posts into cards as the reader sees them. */
    private fun shelves(
        timelineRows: List<List<TimelineRow>>,
        fetched: Fetched,
        loading: Set<ShelfKind>,
        cards: (List<Status>) -> List<VideoCardUi>,
    ): List<Shelf> {
        val timeline = TimelineShelf.entries.zip(timelineRows).associate { (shelf, rows) ->
            shelf.kind to cards(rows.filterIsInstance<TimelineRow.Post>().map { it.status })
        }
        return ShelfKind.entries.map { kind ->
            when (kind) {
                ShelfKind.ContinueWatching -> Shelf(kind, cards(fetched.continueWatching))
                ShelfKind.Following -> Shelf(kind, people = fetched.following)
                ShelfKind.Trending -> Shelf(kind, cards(fetched.trending))
                else -> Shelf(kind, timeline[kind].orEmpty())
            }
        }.filter { it.videos.isNotEmpty() || it.people.isNotEmpty() }.map { it.copy(loading = it.kind in loading) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
