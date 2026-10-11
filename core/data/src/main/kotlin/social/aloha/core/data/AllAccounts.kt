// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import social.aloha.core.datastore.AccountViewPreferences
import social.aloha.core.model.SignedInAccount

/**
 * Every account shown together, the way a mail client shows all inboxes, or one at a time. Together is
 * the default and needs two accounts; with one, nothing changes. Shown together, the first account in
 * the reader's order is the one in use: whatever is written, searched or opened goes as it, and its
 * theme is worn. Picking an account shows that one alone, as before.
 */
public class AllAccounts @Inject constructor(
    private val accounts: AccountRepository,
    private val preferences: AccountViewPreferences,
) {
    /** Whether every account shows together now. */
    public val on: Flow<Boolean> =
        combine(preferences.allAccounts, accounts.accounts) { all, signedIn -> all && signedIn.size > 1 }
            .distinctUntilChanged()

    /** Every account together, as the first of them. */
    public suspend fun showAll() {
        preferences.setAllAccounts(true)
        accounts.accounts.first().firstOrNull()?.let { accounts.activate(it.id) }
    }

    /** Account [id] alone. */
    public suspend fun show(id: String) {
        preferences.setAllAccounts(false)
        accounts.activate(id)
    }

    /**
     * Keeps the first account the one in use while every account shows together: one added, moved to the
     * top or signed out changes which that is.
     */
    public suspend fun keepFirstInUse() {
        combine(on, accounts.accounts, accounts.activeAccount) { on, all, active ->
            all.firstOrNull()?.takeIf { on && active != null && it.id != active.id }
        }.collect { first ->
            // picking one account writes the choice before activating it: read it again, not as combined
            if (first != null && preferences.allAccounts.first()) accounts.activate(first.id)
        }
    }

    /** The accounts shown now, the first being the one in use: all of them together, else the one in use. */
    public val shown: Flow<List<SignedInAccount>> =
        combine(on, accounts.accounts, accounts.activeAccount) { on, all, active ->
            if (on) all else listOfNotNull(active)
        }.distinctUntilChanged()
}
