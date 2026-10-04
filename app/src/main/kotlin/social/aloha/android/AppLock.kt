// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AppLockSettings
import social.aloha.core.data.di.ApplicationScope

/**
 * Whether the app is locked now. A start of the process is locked while the lock is on; so is a return
 * after the reader was away longer than they allowed, decided the moment a window comes back, before it
 * draws. Null until the setting is read, so nothing shows before it is known. [opened] once the reader
 * got in during this process: the app stays composed beneath a later lock, so what was open stays open.
 */
@Singleton
class AppLock @Inject constructor(
    private val settings: AppLockSettings,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = state.asStateFlow()

    private val got = MutableStateFlow(false)
    val opened: StateFlow<Boolean> = got.asStateFlow()

    // held in memory, so a return is decided at once rather than after a read
    private val enabled = settings.enabled.stateIn(scope, SharingStarted.Eagerly, null)
    private val timeoutSeconds = settings.timeoutSeconds.stateIn(scope, SharingStarted.Eagerly, null)

    // when the last window went out of sight; null while one is in sight, or before any was
    private var awaySince: Long? = null

    init {
        scope.launch {
            val on = settings.enabled.first()
            state.value = on
            if (!on) got.value = true
        }
    }

    /** The last window went out of sight. */
    fun onAway() {
        awaySince = SystemClock.elapsedRealtime()
    }

    /** A window came into sight: locked again if the reader was away too long. */
    fun onBack() {
        val since = awaySince ?: return
        awaySince = null
        // before the setting was read the process start decides, and it locks while the lock is on
        if (enabled.value != true) return
        val away = SystemClock.elapsedRealtime() - since
        if (away >= (timeoutSeconds.value ?: 0) * MILLIS) state.value = true
    }

    fun unlock() {
        state.value = false
        got.value = true
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}
