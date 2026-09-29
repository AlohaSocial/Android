// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.database.TimelineDao
import social.aloha.core.database.TimelineEntryEntity
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.FeedMode
import social.aloha.core.model.OverFetch
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineFilters
import social.aloha.core.model.TimelineKey
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.di.IoDispatcher
import social.aloha.core.network.endpoints.PageAnchor
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.TimelineEndpoints
import social.aloha.core.network.map

/** A row of a timeline: a post, or a hole to fill. */
public sealed interface TimelineRow {
    public val id: String

    public data class Post(val status: Status) : TimelineRow {
        override val id: String get() = status.id
    }

    public data class Gap(override val id: String) : TimelineRow
}

/**
 * How a fetch went. [arrived] are the ids new to the timeline; [nextCursor] continues paging older;
 * [reachedEnd] when the server has nothing further down.
 */
public sealed interface PageOutcome {
    public data class Loaded(val arrived: List<String>, val nextCursor: HttpUrl?, val reachedEnd: Boolean) : PageOutcome

    public data class Failed(val error: ApiError) : PageOutcome

    /** A fetch of the same timeline was already in flight; its result arrives through [TimelineRepository.observe]. */
    public data object Busy : PageOutcome
}

/**
 * Timelines, cache first: [observe] draws what is stored at once, and [refresh], [older] and [fillGap]
 * fetch, merge through [TimelineMerge] and store in one transaction, whereupon every observer redraws.
 * One fetch per timeline runs at a time.
 */
@Singleton
public class TimelineRepository @Inject constructor(
    private val dao: TimelineDao,
    private val statuses: StatusRepository,
    private val clients: ClientFactory,
    private val accounts: AccountRepository,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val inFlight = ConcurrentHashMap<String, Mutex>()
    private val fetchedAt = ConcurrentHashMap<String, Long>()

    /**
     * When [key] last got an answer from the server in this process, or null before it did: a timeline
     * that has rows but no fetch yet still asks for the head (see [RefreshPlan]).
     */
    public fun lastFetched(account: SignedInAccount, key: TimelineKey): Long? = fetchedAt[lockKey(account, key)]

    private fun lockKey(account: SignedInAccount, key: TimelineKey) = "${account.id}|${key.storageKey}"

    /**
     * The stored rows, newest first, up to the timeline's cap. ponytail: the window is the whole capped
     * list (500 home rows at most); a Room `PagingSource` as the reader is the upgrade when that is too
     * much to hold.
     */
    public fun observe(account: SignedInAccount, key: TimelineKey): Flow<List<TimelineRow>> =
        dao.observeRows(account.id, key.storageKey, CachePolicy.rowsFor(key.storageKey)).map { records ->
            records.mapNotNull { record ->
                if (record.isGap) {
                    TimelineRow.Gap(record.statusId)
                } else {
                    record.payloadJson?.let { statuses.decode(account.id, record.statusId, it, record.cachedAt ?: 0) }
                        ?.let(TimelineRow::Post)
                }
            }
        }.flowOn(ioDispatcher)

    /** The head of the timeline, or what is newer than its top row, as [plan] says. */
    public suspend fun refresh(account: SignedInAccount, key: TimelineKey, plan: RefreshPlan): PageOutcome =
        fetchAndMerge(Fetch(account, key, plan.anchor, cursor = null, plan.direction))

    /** Further down: the server's `next` cursor where it gave one, else older than [oldestId]. */
    public suspend fun older(
        account: SignedInAccount,
        key: TimelineKey,
        cursor: HttpUrl?,
        oldestId: String,
    ): PageOutcome =
        fetchAndMerge(Fetch(account, key, PageAnchor.OlderThan(oldestId), cursor, TimelineMerge.Direction.Older))

    /** Fills [gapId] from just below [aboveId], the post over it; closed only when the page proves the ranges touch. */
    public suspend fun fillGap(
        account: SignedInAccount,
        key: TimelineKey,
        gapId: String,
        aboveId: String?,
    ): PageOutcome {
        val anchor = aboveId?.let(PageAnchor::OlderThan) ?: PageAnchor.Cold
        return fetchAndMerge(Fetch(account, key, anchor, cursor = null, TimelineMerge.Direction.FillingGap(gapId)))
    }

    /** One fetch of one timeline: where it starts, and how its page merges. */
    private data class Fetch(
        val account: SignedInAccount,
        val key: TimelineKey,
        val anchor: PageAnchor,
        val cursor: HttpUrl?,
        val direction: TimelineMerge.Direction,
    )

    private suspend fun fetchAndMerge(fetch: Fetch): PageOutcome {
        val lock = inFlight.getOrPut(lockKey(fetch.account, fetch.key)) { Mutex() }
        if (!lock.tryLock()) return PageOutcome.Busy
        return try {
            // parsing, encoding and merging a page is not the main thread's work, whoever asked for it
            withContext(ioDispatcher) { fetchLocked(fetch) }
        } finally {
            lock.unlock()
        }
    }

    private suspend fun fetchLocked(fetch: Fetch): PageOutcome = when (
        val harvest = clients.forAccount(fetch.account)?.let {
            harvest(it, fetch)
        }
    ) {
        null -> PageOutcome.Failed(ApiError.NotFound)
        is ApiResult.Failure -> PageOutcome.Failed(harvest.error)
        is ApiResult.Success -> merge(fetch, harvest.value)
    }

    private suspend fun merge(fetch: Fetch, harvest: Harvest): PageOutcome {
        val (account, key) = fetch
        val existing = dao.entries(account.id, key.storageKey).map {
            TimelineMerge.Slot(it.statusId, it.position, it.isGap)
        }
        val plan = TimelineMerge.plan(existing, harvest.statuses.map { it.id }, fetch.direction, harvest.pageWasFull)
        val kept = plan.slots.take(CachePolicy.rowsFor(key.storageKey))
        val now = clock.millis()
        // every status on the page is written, not only the new ones: a cached one may have been edited
        dao.apply(
            account.id,
            key.storageKey,
            harvest.statuses.map { statuses.entity(account.id, it) },
            kept.map { TimelineEntryEntity(account.id, key.storageKey, it.statusId, it.position, it.isGap, now) },
        )
        latchCapabilities(account, harvest.statuses)
        fetchedAt[lockKey(account, key)] = clock.millis()
        // a page that landed wholly below the cap was dropped: paging on would only fetch more to drop
        val keptIds = kept.mapTo(HashSet()) { it.statusId }
        val arrived = plan.inserted.filter { it in keptIds }
        val droppedAll =
            fetch.direction == TimelineMerge.Direction.Older && plan.inserted.isNotEmpty() && arrived.isEmpty()
        return PageOutcome.Loaded(arrived, harvest.nextCursor, reachedEnd = harvest.isLastUpstream || droppedAll)
    }

    private suspend fun latchCapabilities(account: SignedInAccount, seen: List<Status>) {
        val latched = account.capabilities.latched(seen)
        if (latched != account.capabilities) accounts.updateCapabilities(account.id, latched)
    }

    private data class Harvest(val statuses: List<Status>, val pageWasFull: Boolean, val nextCursor: HttpUrl?) {
        /** A short page, or one without a way further down: the server has nothing more to over-fetch. */
        val isLastUpstream: Boolean get() = !pageWasFull || nextCursor == null
    }

    /**
     * One visible page. Where the server cannot narrow a media mode, the client filters and fetches on,
     * at most [OverFetch.MAXIMUM_UPSTREAM_PAGES] upstream pages, stopping at a short page so nothing
     * is skipped. `pageWasFull` is about what the server sent, not what survived the filter.
     */
    private suspend fun harvest(client: ApiClient, fetch: Fetch): ApiResult<Harvest> {
        val mode = fetch.key.mode
        val serverFilters = TimelineFilters.forMode(mode, fetch.account.capabilities)
        val onDevice = mode != FeedMode.Home && serverFilters.isEmpty
        val budget = upstreamPages(mode, serverFilters, onDevice)
        val request = TimelineEndpoints.timeline(fetch.key.source, serverFilters, Paging.DEFAULT_LIMIT, fetch.anchor)
        val kept = mutableListOf<Status>()
        var last = Harvest(emptyList(), pageWasFull = false, nextCursor = fetch.cursor)
        for (page in 1..budget) {
            val fetched = when (val result = page(client, request, last.nextCursor)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value
            }
            // on the device, only what belongs in the mode is kept; the server has already narrowed otherwise
            kept += fetched.statuses.filter { !onDevice || it.belongs(mode) }
            last = fetched
            // the budget is one page when the server narrowed; a filtered page stops once it is full
            if (fetched.isLastUpstream || kept.size >= Paging.DEFAULT_LIMIT) break
        }
        return ApiResult.Success(last.copy(statuses = kept))
    }

    private fun upstreamPages(mode: FeedMode, serverFilters: TimelineFilters, onDevice: Boolean): Int =
        if (onDevice) minOf(OverFetch.MAXIMUM_UPSTREAM_PAGES, OverFetch.multiplier(mode, serverFilters)) else 1

    /** One upstream page: the request, or the cursor that continues it. */
    private suspend fun page(
        client: ApiClient,
        request: ApiRequest<List<Status>>,
        cursor: HttpUrl?,
    ): ApiResult<Harvest> {
        val result =
            cursor?.let { client.page(it, request, Paging.DEFAULT_LIMIT) } ?: client.page(request, Paging.DEFAULT_LIMIT)
        return result.map {
            Harvest(it.items, pageWasFull = it.rawCount >= Paging.DEFAULT_LIMIT, nextCursor = it.link.next)
        }
    }

    private fun Status.belongs(mode: FeedMode) = ContentClassifier.classify(this).belongs(mode)
}
