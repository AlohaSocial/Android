// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.data.sync.UserStream
import social.aloha.core.data.sync.WidgetUpdates
import social.aloha.core.model.LogArea
import social.aloha.core.model.PollFrequency
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.Backoff
import social.aloha.core.network.endpoints.NotificationEndpoints
import timber.log.Timber

/**
 * Asks every signed-in account's server what is new while the app is open, at the pace [PollScheduler]
 * sets. Nextcloud Social has no streaming and no push of its own, so polling is the normal case here,
 * not a fallback.
 *
 * Each poll asks for the unread count first, the cheapest call there is. A timeline on screen is then
 * told through [TimelineSignals] that it is due; [PollListener]s hear when the count moved, and again on
 * the next poll when one of them could not finish. A failure backs off with full jitter from the pace, a
 * 429 without `Retry-After` halves the pace for an hour, and an account that needs a new sign-in, or a
 * device without a network, is not asked at all. Polls of one account never overlap.
 */
@Singleton
public class SyncEngine @Inject internal constructor(
    private val accounts: AccountRepository,
    private val clients: ClientFactory,
    private val settings: SyncSettings,
    private val timelines: TimelineSignals,
    private val counts: UnreadCounts,
    private val push: PushSubscriptions,
    private val registrar: PushRegistrar,
    private val widgets: WidgetUpdates,
    private val device: DeviceConditions,
    private val listeners: Set<@JvmSuppressWildcards PollListener>,
    private val background: BackgroundRefresh,
    private val digests: DigestScheduler,
    private val stream: UserStream,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val foreground = MutableStateFlow(false)
    private val lastPoll = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val slowUntil = ConcurrentHashMap<String, Long>()
    private val holdUntil = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    // accounts a listener could not finish with, told again on the next poll whatever the count
    private val unsettled = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var lastInteraction = clock.millis()
    private var loop: Job? = null

    /**
     * The app came to the foreground or left it; polling runs only while it is in front. The first call
     * also starts keeping the background refresh and the digest scheduled and every account registered
     * for push.
     */
    public fun setForeground(inFront: Boolean) {
        if (inFront) noteInteraction()
        // what the timelines just fetched is the freshest the widgets can show
        if (!inFront && foreground.value) widgets.redraw()
        foreground.value = inFront
        synchronized(this) {
            if (loop == null) {
                loop = scope.launch { run() }
                scope.launch { background.keepScheduled() }
                scope.launch { digests.keepScheduled() }
                scope.launch { keepRegistered() }
                scope.launch { stream.keepOpen(foreground, device.online) }
                // a notification on the stream is a count to fetch now rather than at the next poll; a burst
                // of them is one fetch
                scope.launch {
                    stream.notifications.conflate().collect { id ->
                        accounts.byId(id)?.let { poll(it, PollScope.NotificationsOnly) }
                    }
                }
            }
        }
    }

    /** The person touched the app; an idle app is asked less often. */
    public fun noteInteraction() {
        lastInteraction = clock.millis()
    }

    /** Polls each account on its own loop, started again when the accounts or their pace change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run() {
        val paced: Flow<Map<String, PollFrequency>> = accounts.accounts.flatMapLatest { all ->
            counts.retain(all.map { it.id }.toSet())
            val polled = all.filterNot { it.needsReauth }
            if (polled.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(polled.map { account -> settings.pollFrequency(account.id).map { account.id to it } }) {
                    it.toMap()
                }
            }
        }
        // a stream opening or closing starts the loops again, so the pace follows it at once
        combine(foreground, device.online, paced, stream.openFor) { inFront, online, pace, streamed ->
            if (inFront && online) pace to streamed else emptyMap<String, PollFrequency>() to null
        }
            .distinctUntilChanged()
            .collectLatest { (pace, _) -> coroutineScope { pace.keys.forEach { launch { tick(it) } } } }
    }

    /** Registers again for push whenever what decides it changes: an account, its key, its Nextcloud. */
    private suspend fun keepRegistered() {
        accounts.accounts
            .map { all ->
                all.map {
                    Triple(
                        it.id,
                        it.needsReauth,
                        it.capabilities.webPushVapidKey to it.nextcloudConnected,
                    )
                }
            }
            .distinctUntilChanged()
            .collect { registrar.registerAll() }
    }

    private suspend fun tick(accountId: String) {
        while (true) {
            val wait = accounts.byId(accountId)?.let { waitBeforeNext(it) } ?: return
            if (wait > Duration.ZERO) delay(wait)
            val account = accounts.byId(accountId) ?: return
            poll(account, scheduler(account).scope)
        }
    }

    /** How long until [account] is asked again; null when it is asked only by hand. */
    internal suspend fun waitBeforeNext(account: SignedInAccount): Duration? {
        val scheduled = scheduler(account).interval ?: return null
        // a pushed or streamed account is polled only as a safety net, since a push or a frame can be lost
        val floor = when {
            push.isActive(account.id) -> PUSHED_FLOOR
            stream.openFor.value == account.id -> STREAMED_FLOOR
            else -> Duration.ZERO
        }
        val paced = maxOf(scheduled, floor)
        val now = clock.millis()
        val slowed = if ((slowUntil[account.id] ?: 0) > now) paced * 2 else paced
        val backedOff = failures[account.id]?.let { maxOf(slowed, Backoff.delay(it, base = paced)) } ?: slowed
        val held = maxOf(backedOff, ((holdUntil[account.id] ?: 0) - now).milliseconds)
        return held - (now - (lastPoll[account.id] ?: 0)).milliseconds
    }

    private suspend fun scheduler(account: SignedInAccount): PollScheduler = PollScheduler(
        activeAccount = accounts.activeAccount.value?.id == account.id,
        attention = Attention.after((clock.millis() - lastInteraction).milliseconds),
        frequency = settings.pollFrequency(account.id).first(),
        powerSave = device.powerSave,
        metered = device.metered,
        wifiOnly = settings.wifiOnly.first(),
    )

    /** Asks [account]'s server for its unread count, and passes on what it found; null when it could not. */
    public suspend fun poll(account: SignedInAccount, pollScope: PollScope): Int? {
        if (account.needsReauth) return null
        return locks.getOrPut(account.id) { Mutex() }.withLock {
            lastPoll[account.id] = clock.millis()
            val request = NotificationEndpoints.unreadCount(account.capabilities.groupedNotifications)
            when (val answer = clients.answer(account, request)) {
                is Answer.Got -> answer.value.count.also {
                    Timber.tag(LogArea.Sync.name).d("Poll of %s: %d unread", account.id, it)
                    counted(account, it, pollScope)
                }

                is Answer.Missed -> {
                    Timber.tag(
                        LogArea.Sync.name,
                    ).i("Poll of %s failed: %s", account.id, answer.error.javaClass.simpleName)
                    failed(account.id, answer.error)
                    null
                }
            }
        }
    }

    private suspend fun counted(account: SignedInAccount, count: Int, pollScope: PollScope) {
        // a process started for background work knows the count only from what the widgets were given
        val previous = counts.of(account.id) ?: widgets.feed(account.id).first().unread
        counts.set(account.id, count)
        failures.remove(account.id)
        if (pollScope == PollScope.Full && timelines.onScreen(account.id)) timelines.markDue(account.id)
        // ponytail: an unchanged count asks for nothing, so a group that grew while another was read goes
        // unraised until the count moves; comparing each group's newest id per poll is the upgrade
        if (previous == count && account.id !in unsettled) return
        // every listener is told, even after one could not finish
        val settled = listeners.map { it.onUnreadChanged(account, count) }.all { it }
        if (settled) unsettled -= account.id else unsettled += account.id
    }

    /**
     * Asks every account that is polled on a timer for what drives notifications and the badge, never
     * for timelines: a background refresh is there to tell the person something happened, not to warm a
     * cache.
     */
    public suspend fun refreshInBackground() {
        accounts.all()
            .filter { !it.needsReauth && settings.pollFrequency(it.id).first().multiplier != null }
            .forEach { poll(it, PollScope.NotificationsOnly) }
    }

    internal fun failed(accountId: String, error: ApiError) {
        failures.merge(accountId, 1, Int::plus)
        if (error is ApiError.RateLimited) {
            val now = clock.millis()
            val retryAfter = error.retryAfter
            if (retryAfter == null) {
                slowUntil[accountId] = now + SLOWED_FOR.inWholeMilliseconds
            } else {
                holdUntil[accountId] = now + retryAfter.inWholeMilliseconds
            }
        }
    }

    private companion object {
        val SLOWED_FOR = 1.hours
        val PUSHED_FLOOR = 10.minutes
        val STREAMED_FLOOR = 5.minutes
    }
}
