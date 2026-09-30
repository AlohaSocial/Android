// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.nextcloud.NextcloudConnection
import social.aloha.core.data.notifications.RaisedNotifications
import social.aloha.core.data.sync.WidgetUpdates
import social.aloha.core.data.timeline.CacheSweeper
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthEndpoints

/**
 * Signing an account out of this device. Its token is revoked on its server, so it stops working
 * there too, and so is the Nextcloud app password when it had one; then its secrets, its cache, its
 * settings and the posts it had not sent go. Other
 * devices stay signed in. A server that cannot be reached (offline, gone) does not keep the account
 * here: the token is still deleted from the device, and only that server keeps a record of it.
 */
@Singleton
public class AccountRemoval @Inject constructor(
    private val accounts: AccountRepository,
    private val oauth: OAuthClient,
    private val sweeper: CacheSweeper,
    private val settings: AccountSettingsStore,
    private val outbox: Outbox,
    private val nextcloud: NextcloudConnection,
    private val widgets: WidgetUpdates,
    private val raised: RaisedNotifications,
) {
    public suspend fun signOut(account: SignedInAccount) {
        // the device forgets first, so a slow or unreachable server can never leave the account behind
        val token = accounts.token(account.id)
        val registration = accounts.registration(account.host)
        val appPassword = accounts.credentials(account.id).nextcloudBasic
        accounts.remove(account.id)
        sweeper.forget(account.id)
        settings.forget(account.id)
        outbox.forget(account.id)
        widgets.forget(account.id)
        raised.forget(account.id)
        appPassword?.let { nextcloud.revoke(account, it) }
        val base = account.apiBase.toHttpUrlOrNull() ?: return
        if (token != null && registration != null) {
            oauth.revoke(OAuthEndpoints.from(oauth.metadata(base), base), registration, token)
        }
    }
}
