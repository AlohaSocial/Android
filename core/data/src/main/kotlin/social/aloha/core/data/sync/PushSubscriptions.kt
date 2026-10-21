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
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.PushRefusals
import social.aloha.core.model.NotificationsFrom
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
    private val settings: AccountSettingsStore,
    private val refusals: PushRefusals,
) {
    /** The ids of the accounts pushed to. */
    public val active: Flow<Set<String>> = app.pushAccounts

    public suspend fun isActive(accountId: String): Boolean = accountId in app.pushAccounts.first()

    /** Asks [account]'s server to push to [endpoint], encrypted for [p256dh] and [auth]. */
    public suspend fun subscribe(account: SignedInAccount, endpoint: String, p256dh: String, auth: String): ApiError? {
        val policy = settings.settings(account.id).first().notificationsFrom.wire
        return when (val answer = clients.answer(account, PushEndpoints.subscribe(endpoint, p256dh, auth, policy))) {
            is Answer.Got -> {
                app.setPush(account.id, active = true)
                null
            }

            is Answer.Missed -> answer.error.also {
                // Nextcloud Social refuses an endpoint not on the public internet, such as a self-hosted ntfy,
                // with a 422 that carries no code, only this error; its other 422s here are about the keys
                if (it is ApiError.Unprocessable && it.message == ENDPOINT_REFUSED) {
                    refusals.set(account.id, refused = true)
                }
            }
        }
    }

    /** Whether [accountId]'s server refused the distributor's endpoint; it is not asked again until one is chosen. */
    public suspend fun refused(accountId: String): Boolean = accountId in refusals.accounts.first()

    /** Asks [accountId]'s server again from now on, as for a distributor chosen. */
    public suspend fun forgetRefusal(accountId: String) {
        refusals.set(accountId, refused = false)
    }

    /** Whose notifications [account]'s server pushes from now on, where it pushes to this device at all. */
    public suspend fun setPolicy(account: SignedInAccount, from: NotificationsFrom) {
        if (isActive(account.id)) clients.answer(account, PushEndpoints.policy(from.wire))
    }

    /** Tells [account]'s server to stop, and stops counting on it; an unreachable server just stays unused. */
    public suspend fun unsubscribe(account: SignedInAccount) {
        app.setPush(account.id, active = false)
        app.setPushEndpoint(account.id, null)
        clients.answer(account, PushEndpoints.unsubscribe())
    }

    /** Whether [accountId] is pushed to through [endpoint], the one its registration [instance] last took. */
    public suspend fun subscribed(accountId: String, instance: String, endpoint: String): Boolean =
        endpoint.isNotEmpty() && isActive(accountId) && app.pushEndpoint(instance).first() == endpoint

    /** The endpoint registration [instance] was last subscribed with, if it still is. */
    public suspend fun endpoint(instance: String): String? = app.pushEndpoint(instance).first()

    /** The [endpoint] registration [instance] was subscribed with, or null once it no longer is. */
    public suspend fun rememberEndpoint(instance: String, endpoint: String?) {
        app.setPushEndpoint(instance, endpoint)
    }

    /** [accountId] is pushed to by another route, its Nextcloud, which confirmed the registration. */
    public suspend fun markActive(accountId: String) {
        app.setPush(accountId, active = true)
    }

    /** The distributor dropped [accountId]'s registration: polling alone serves it again. */
    public suspend fun lost(accountId: String) {
        app.setPush(accountId, active = false)
    }

    private companion object {
        const val ENDPOINT_REFUSED = "The push endpoint must be a reachable public HTTPS URL"
    }
}
