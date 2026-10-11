// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import social.aloha.core.datastore.AppLockPreferences
import social.aloha.core.datastore.DeviceSearchPreferences

/**
 * Whether the phone's own search finds the signed-in accounts, the accounts they follow, their public
 * bookmarks and recent searches. Timelines and private posts are never in it.
 */
public class DeviceSearchSettings @Inject constructor(
    private val preferences: DeviceSearchPreferences,
    private val lock: AppLockPreferences,
) {
    /** What the reader chose. */
    public val chosen: Flow<Boolean> get() = preferences.shown

    /**
     * Whether the phone's search is given anything: chosen, and the app lock off, since what the lock
     * keeps behind it must not be found from the launcher without it.
     */
    public val shown: Flow<Boolean> get() = combine(preferences.shown, lock.enabled) { shown, locked ->
        shown && !locked
    }

    public suspend fun setShown(shown: Boolean): Unit = preferences.setShown(shown)
}
