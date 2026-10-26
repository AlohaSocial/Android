// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource

/**
 * The posts of a mode with a screen of its own, such as Shorts or Audio: the account in use's timeline for
 * the mode, or every account shown together, merged as [MergedTimeline] merges them. Each account reads the
 * source it chose for the mode and asks its own server, which narrows to the mode where it can and leaves
 * the rest to the device. One per screen, living in the screen's scope.
 */
public sealed interface ModeFeed {
    /** A post as [account], which it acts as, holds it; [seenBy] the accounts that have it, shown together. */
    public data class Post(
        val account: SignedInAccount,
        val status: Status,
        val inUse: Boolean,
        val seenBy: List<ModeTimeline> = emptyList(),
    )

    /** What the screen shows: the [posts], newest first, the source read and the others offered, how loading went. */
    public data class Page(
        val posts: List<Post> = emptyList(),
        val source: TimelineSource = TimelineSource.Home,
        val sources: List<TimelineSource> = listOf(TimelineSource.Home),
        val loadedOnce: Boolean = false,
        val trouble: Trouble? = null,
    )

    public val pages: Flow<Page>

    /** The next page below: the account in use's, or that of each account the merged list waits for. */
    public fun older()
}

/** Makes a screen's [ModeFeed]. */
public class ModeFeeds @Inject constructor(
    private val timelines: TimelineRepository,
    private val modes: ModeSources,
    private val clock: Clock,
) {
    /** [mode]'s feed in [scope]: of every account shown where [together], else of the account in use. */
    public fun of(scope: CoroutineScope, mode: FeedMode, together: Boolean): ModeFeed = if (together) {
        All(timelines, modes, clock, scope, mode)
    } else {
        One(timelines, modes, clock, scope, mode)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class One(
    timelines: TimelineRepository,
    modes: ModeSources,
    clock: Clock,
    scope: CoroutineScope,
    mode: FeedMode,
) : ModeFeed {
    private val timeline = modes.timeline(mode).stateIn(scope, SharingStarted.Eagerly, null)
    private val pager = TimelinePager(timelines, clock, scope) { timeline.value?.let { it.reader to it.key } }

    init {
        scope.launch { timeline.filterNotNull().collect { pager.start(it.reader, it.key) } }
    }

    override val pages: Flow<ModeFeed.Page> = timeline.filterNotNull().flatMapLatest { (reader, key, sources) ->
        combine(pager.observe(reader, key), pager.states) { rows, paging ->
            ModeFeed.Page(
                posts = rows.mapNotNull { row ->
                    (row as? TimelineRow.Post)?.let { ModeFeed.Post(reader, it.status, inUse = true) }
                },
                source = key.source,
                sources = sources,
                loadedOnce = paging.loadedOnce,
                trouble = paging.trouble,
            )
        }
    }

    override fun older() = pager.older()
}

@OptIn(ExperimentalCoroutinesApi::class)
private class All(
    timelines: TimelineRepository,
    modes: ModeSources,
    clock: Clock,
    scope: CoroutineScope,
    mode: FeedMode,
) : ModeFeed {
    private val shown = modes.together(mode).stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val pager = MergedPager(timelines, clock, scope) { keysOf(shown.value) }

    @Volatile private var behind: List<String> = emptyList()

    init {
        scope.launch {
            // other accounts, or another source of one of them, start afresh
            shown.filter { it.isNotEmpty() }
                .distinctUntilChanged { a, b -> a.map { it.reader.id to it.key } == b.map { it.reader.id to it.key } }
                .collect { pager.start(keysOf(it)) }
        }
    }

    override val pages: Flow<ModeFeed.Page> = shown.filter { it.isNotEmpty() }.flatMapLatest { list ->
        val byId = list.associateBy { it.reader.id }
        val inUse = list.first().reader.id
        combine(pager.observe(keysOf(list)), pager.states) { stored, paging ->
            val page = MergedTimeline.merge(
                list.map { AccountRows.of(it.reader.id, stored[it.reader.id].orEmpty(), it.reader.id in paging.ended) },
            )
            behind = page.behind
            ModeFeed.Page(
                posts = page.posts.map { post ->
                    ModeFeed.Post(
                        byId.getValue(post.owner).reader,
                        post.status,
                        inUse = post.owner == inUse,
                        seenBy = post.seenBy.mapNotNull(byId::get),
                    )
                },
                source = list.first().key.source,
                // each account reads the source it chose for the mode on its own
                sources = emptyList(),
                loadedOnce = paging.loadedOnce,
                trouble = list.firstNotNullOfOrNull { paging.troubles[it.reader.id] },
            )
        }
    }

    override fun older() = pager.older(behind)

    private fun keysOf(list: List<ModeTimeline>) = list.map { it.reader to it.key }
}
