// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.PushEndpoints

/**
 * Which accounts their server pushes to, through the distributor on this device. While one does, its
 * polling slows to a safety net; it never stops, since a push can be lost.
 */
@Singleton
public class PushSubscriptions @Inject constructor(
    private val clients: ClientFactory,
    private val app: AppPreferences,
) {
    /** The ids of the accounts pushed to. */
    public val active: Flow<Set<String>> = app.pushAccounts

    public suspend fun isActive(accountId: String): Boolean = accountId in app.pushAccounts.first()

    /** Asks [account]'s server to push to [endpoint], encrypted for [p256dh] and [auth]. */
    public suspend fun subscribe(account: SignedInAccount, endpoint: String, p256dh: String, auth: String): ApiError? =
        when (val answer = clients.answer(account, PushEndpoints.subscribe(endpoint, p256dh, auth))) {
            is Answer.Got -> {
                app.setPush(account.id, active = true)
                null
            }

            is Answer.Missed -> answer.error
        }

    /** Tells [account]'s server to stop, and stops counting on it; an unreachable server just stays unused. */
    public suspend fun unsubscribe(account: SignedInAccount) {
        app.setPush(account.id, active = false)
        app.setPushEndpoint(account.id, null)
        clients.answer(account, PushEndpoints.unsubscribe())
    }

    /** The distributor dropped [accountId]'s registration: polling alone serves it again. */
    public suspend fun lost(accountId: String) {
        app.setPush(accountId, active = false)
    }
}
