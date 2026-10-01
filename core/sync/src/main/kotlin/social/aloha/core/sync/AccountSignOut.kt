// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import social.aloha.core.data.AccountRemoval
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.model.SignedInAccount

/**
 * Signing an account out of this device from start to end, the same whether the person signed out or
 * deleted the account: push stopped while its credentials still work, then [AccountRemoval], then its
 * notifications. It runs in the application's scope, so leaving the screen that asked never stops it
 * halfway, with the account gone on its server and still here.
 */
public class AccountSignOut @Inject constructor(
    private val push: PushRegistrar,
    private val removal: AccountRemoval,
    private val notifications: LocalNotifications,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    public fun signOut(account: SignedInAccount): Job = scope.launch {
        // the server is told to stop pushing while the token still works, but an unreachable one never
        // keeps the account here for long
        withTimeoutOrNull(PUSH_FORGET_MILLIS) { push.forget(account) }
        removal.signOut(account)
        notifications.forget(account.id)
    }

    private companion object {
        const val PUSH_FORGET_MILLIS = 5_000L
    }
}
