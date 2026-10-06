// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.Backoff
import social.aloha.core.network.streaming.StreamEvent
import social.aloha.core.network.streaming.StreamListener
import social.aloha.core.network.streaming.UserSockets
import timber.log.Timber

/**
 * The active account's own stream from its server's streaming socket, while the app is in front and
 * online: posts arriving mark Home due, a few seconds' worth at a time; edits and deletions reach the cache
 * at once; a notification asks for the count now; changed filters are fetched again. Only where the server
 * advertises streaming, which Mastodon does and Nextcloud Social does not. Five failures in a row, a socket
 * that drops within half a minute counting as one, and it gives up until the app comes back to the front,
 * the network comes back or the account changes; polling carries on meanwhile.
 */
@Singleton
public class UserStream @Inject constructor(
    private val accounts: AccountRepository,
    private val sockets: UserSockets,
    private val statuses: StatusRepository,
    private val filters: FilterRepository,
    private val timelines: TimelineSignals,
) {
    private val open = MutableStateFlow<String?>(null)
    private val notified = MutableSharedFlow<String>(extraBufferCapacity = NOTIFIED_BUFFER)

    /** The account whose stream is open, which polling slows to a safety net for; null when none is. */
    public val openFor: StateFlow<String?> = open.asStateFlow()

    /** Accounts a notification just arrived for, to be asked for their count at once. */
    public val notifications: SharedFlow<String> = notified.asSharedFlow()

    /** Keeps the active account's stream open for as long as [inFront] and [online] both hold. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public suspend fun keepOpen(inFront: Flow<Boolean>, online: Flow<Boolean>) {
        combine(inFront, online, accounts.activeAccount) { front, reachable, account ->
            account?.takeIf { front && reachable && !it.needsReauth && it.capabilities.streamingUrl != null }
        }
            .distinctUntilChanged { old, new -> old?.id == new?.id && old?.capabilities == new?.capabilities }
            .collectLatest { account -> if (account != null) stream(account) }
    }

    private suspend fun stream(account: SignedInAccount) {
        val url = account.capabilities.streamingUrl ?: return
        var failures = 0
        while (failures < ATTEMPTS) {
            val token = accounts.credentials(account.id).bearerToken ?: return
            val started = TimeSource.Monotonic.markNow()
            // one that held a while starts counting again; one that never opened, or dropped at once, counts on
            val held = session(account, url, token) && started.elapsedNow() >= STEADY
            failures = if (held) 0 else failures + 1
            if (failures < ATTEMPTS) delay(Backoff.delay(failures, base = RECONNECT_BASE))
        }
        Timber.tag(LogArea.Sync.name).i("Stream of %s given up after %d failures", account.id, failures)
    }

    /** One socket, until it ends; whether it opened. */
    private suspend fun session(account: SignedInAccount, url: String, token: String): Boolean = coroutineScope {
        var opened = false
        var due: Job? = null
        try {
            socket(url, token).collect { heard ->
                when (heard) {
                    null -> {
                        opened = true
                        open.value = account.id
                        Timber.tag(LogArea.Sync.name).i("Stream of %s open", account.id)
                    }

                    StreamEvent.Update -> if (due?.isActive != true) {
                        // a busy Home arrives by the second: the timeline is told once for a few seconds' worth
                        due = launch {
                            delay(GATHER)
                            timelines.markDue(account.id)
                        }
                    }

                    else -> apply(account, heard)
                }
            }
        } finally {
            open.compareAndSet(account.id, null)
        }
        opened
    }

    private suspend fun apply(account: SignedInAccount, event: StreamEvent) {
        when (event) {
            // only a post already here: the stream's copy is the server's own, without the reader's marks
            is StreamEvent.Edited -> statuses.get(account.id, event.status.id)?.let { cached ->
                statuses.save(account.id, event.status.markedAs(cached))
            }

            is StreamEvent.Deleted -> statuses.delete(account.id, event.statusId)

            StreamEvent.Notified -> notified.tryEmit(account.id)

            StreamEvent.FiltersChanged -> filters.refresh(account)

            StreamEvent.Update -> timelines.markDue(account.id)
        }
    }

    /** The socket's events as they come, null when it opened, ending when it does. */
    private fun socket(url: String, token: String): Flow<StreamEvent?> = callbackFlow {
        val socket = sockets.open(
            url,
            token,
            object : StreamListener {
                override fun onOpen() {
                    trySend(null)
                }

                override fun onEvent(event: StreamEvent) {
                    trySend(event)
                }

                override fun onClosed(failure: Throwable?) {
                    failure?.let { Timber.tag(LogArea.Sync.name).i("Stream closed: %s", it.javaClass.simpleName) }
                    channel.close()
                }
            },
        )
        awaitClose { socket.close() }
    }.buffer(Channel.UNLIMITED)

    private companion object {
        const val ATTEMPTS = 5
        val STEADY = 30.seconds
        const val NOTIFIED_BUFFER = 8
        val RECONNECT_BASE = 2.seconds
        val GATHER = 5.seconds
    }
}

/** This edit with what the reader did to [cached]: favourited, boosted, bookmarked, pinned, muted, voted. */
private fun Status.markedAs(cached: Status): Status = copy(
    favourited = cached.favourited,
    reblogged = cached.reblogged,
    bookmarked = cached.bookmarked,
    pinned = cached.pinned,
    muted = cached.muted,
    poll = poll?.let { edited ->
        val mine = cached.poll?.takeIf { it.id == edited.id }
        mine?.let { edited.copy(voted = it.voted, ownVotes = it.ownVotes) } ?: edited
    },
    filtered = cached.filtered,
)
