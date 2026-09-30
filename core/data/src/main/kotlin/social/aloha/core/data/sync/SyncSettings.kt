// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.PollFrequency

/** The settings that decide how often and how much the app asks the servers; the settings screens write them. */
public class SyncSettings @Inject constructor(
    private val accounts: AccountSettingsStore,
    private val app: AppPreferences,
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
}
