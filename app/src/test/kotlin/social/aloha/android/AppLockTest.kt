// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.datastore.preferences.core.emptyPreferences
import java.time.Duration
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import social.aloha.core.data.AppLockSettings
import social.aloha.core.datastore.AppLockPreferences
import social.aloha.core.testing.InMemoryDataStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockTest {
    private fun lock(enabled: Boolean, timeoutSeconds: Int = 0): AppLock = runBlocking {
        val settings = AppLockSettings(AppLockPreferences(InMemoryDataStore(emptyPreferences())))
        settings.setEnabled(enabled)
        settings.setTimeoutSeconds(timeoutSeconds)
        // unconfined: what the lock launches runs before each call returns
        AppLock(settings, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun `turned on, the app opens locked and stays open once unlocked`() {
        val lock = lock(enabled = true)
        assertEquals(true, lock.locked.value)
        lock.unlock()
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun `away for less than allowed it stays open, away longer it locks again`() {
        val lock = lock(enabled = true, timeoutSeconds = 300)
        lock.unlock()
        lock.onAway()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        lock.onBack()
        assertEquals(false, lock.locked.value)
        lock.onAway()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        lock.onBack()
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun `asking every time locks on any return, and turned off it never locks`() {
        val every = lock(enabled = true, timeoutSeconds = 0)
        every.unlock()
        every.onAway()
        every.onBack()
        assertEquals(true, every.locked.value)
        val off = lock(enabled = false)
        assertEquals(false, off.locked.value)
        off.onAway()
        ShadowSystemClock.advanceBy(Duration.ofHours(2))
        off.onBack()
        assertEquals(false, off.locked.value)
    }

    @Test
    fun `a return is decided before anything else runs, so an unlock right after it stays`() = runBlocking {
        val settings = AppLockSettings(AppLockPreferences(InMemoryDataStore(emptyPreferences())))
        settings.setEnabled(true)
        settings.setTimeoutSeconds(0)
        // work the lock launches waits here until run, as it would behind a busy main thread
        val queued = ArrayDeque<Runnable>()
        val run = { while (queued.isNotEmpty()) queued.removeFirst().run() }
        val lock = AppLock(settings, CoroutineScope(Executor { queued.add(it) }.asCoroutineDispatcher()))
        run()
        assertEquals(false, lock.opened.value)
        lock.unlock()
        assertEquals(true, lock.opened.value)
        // the screen lock's own confirmation is an activity of its own: going to it is going away
        lock.onAway()
        lock.onBack()
        assertEquals(true, lock.locked.value)
        lock.unlock()
        run()
        assertEquals(false, lock.locked.value)
        assertEquals(true, lock.opened.value)
    }
}
