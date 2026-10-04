// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.model.Digest
import social.aloha.core.model.PollFrequency
import social.aloha.core.model.QuietHours

/**
 * The settings of sync and notifications: how often and how much the app asks the servers, quiet hours,
 * the digest, and whether the person was asked to allow notifications. The settings screens write them.
 */
public class SyncSettings @Inject constructor(
    private val accounts: AccountSettingsStore,
    private val app: AppPreferences,
    private val notifications: NotificationPreferences,
) {
    public fun pollFrequency(accountId: String): Flow<PollFrequency> =
        accounts.settings(accountId).map { it.pollFrequency }.distinctUntilChanged()

    public suspend fun setPollFrequency(accountId: String, frequency: PollFrequency) {
        accounts.update(accountId) { it.copy(pollFrequency = frequency) }
    }

    public val wifiOnly: Flow<Boolean> = app.wifiOnlySync

    public suspend fun setWifiOnly(wifiOnly: Boolean) {
        app.setWifiOnlySync(wifiOnly)
    }

    public val quietHours: Flow<QuietHours?> = notifications.quietHours

    public suspend fun setQuietHours(hours: QuietHours?) {
        notifications.setQuietHours(hours)
    }

    public val digest: Flow<Digest?> = notifications.digest

    public suspend fun setDigest(digest: Digest?) {
        notifications.setDigest(digest)
    }

    public val askedForNotifications: Flow<Boolean> = app.askedForNotifications

    public suspend fun setAskedForNotifications() {
        app.setAskedForNotifications()
    }
}
