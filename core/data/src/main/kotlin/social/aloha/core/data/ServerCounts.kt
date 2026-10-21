// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.network.endpoints.CountsEndpoints

/**
 * Reading's *Show numbers* told to every Nextcloud Social account's server, which zeroes the like, boost
 * and reply numbers for a reader who hides them, and hides them unless told otherwise. It is told when
 * the switch changes, and whenever the accounts change (one signed in, its capabilities detected again)
 * to each account whose server has not taken the value stored then; a failure is told again next time.
 */
@Singleton
public class ServerCounts @Inject constructor(
    private val accounts: AccountRepository,
    private val clients: ClientFactory,
    private val reading: ReadingPreferences,
    private val settings: AccountSettingsStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val telling = Mutex()
    private val started = AtomicBoolean(false)

    /** Tells the servers from now on, for the life of the process; a second start does nothing. */
    public fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val shown = reading.style.map { it.showCounts }.distinctUntilChanged()
            combine(accounts.accounts, shown) { _, _ -> }
                .onEach { tell() }
                // storage that failed is read again later, so the switch is still told once it works
                .retryWhen { cause, attempt ->
                    (cause is IOException).also {
                        if (it) delay((FIRST_RETRY_MILLIS shl attempt.toInt().coerceAtMost(MAX_DOUBLINGS)))
                    }
                }
                .collect()
        }
    }

    /** Sends what is stored to every Social account whose server did not take it yet, one telling at a time. */
    internal suspend fun tell(): Unit = telling.withLock {
        val hide = !reading.style.first().showCounts
        for (account in accounts.all().filter { it.capabilities.isNextcloudSocial }) {
            if (settings.settings(account.id).first().countsHidden == hide) continue
            if (clients.answer(account, CountsEndpoints.hide(hide)) is Answer.Got) {
                settings.update(account.id) { it.copy(countsHidden = hide) }
            }
        }
    }

    private companion object {
        const val FIRST_RETRY_MILLIS = 2_000L
        const val MAX_DOUBLINGS = 5
    }
}
