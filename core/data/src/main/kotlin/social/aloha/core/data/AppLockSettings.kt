// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import social.aloha.core.datastore.AppLockPreferences

/**
 * The app lock as the rest of the app reads it: whether it is on, and how long the app may be away
 * before it asks again. Notifications leave their buttons off while it is on, so nothing is answered,
 * favourited or boosted without unlocking.
 */
public class AppLockSettings @Inject constructor(private val preferences: AppLockPreferences) {
    public val enabled: Flow<Boolean> get() = preferences.enabled

    public val timeoutSeconds: Flow<Int> get() = preferences.timeoutSeconds

    public suspend fun setEnabled(enabled: Boolean): Unit = preferences.setEnabled(enabled)

    public suspend fun setTimeoutSeconds(seconds: Int): Unit = preferences.setTimeoutSeconds(seconds)
}
