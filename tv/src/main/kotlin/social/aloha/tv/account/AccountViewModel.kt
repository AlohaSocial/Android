// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRemoval
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.model.SignedInAccount

@HiltViewModel
internal class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val removal: AccountRemoval,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ViewModel() {
    val account: StateFlow<SignedInAccount?> = accounts.activeAccount

    /**
     * Signs the account out in the application's scope, so leaving the screen midway never leaves it signed
     * in: the token is given back to the server where it can be reached, and everything kept for it goes.
     */
    fun onSignOut() {
        val account = accounts.activeAccount.value ?: return
        scope.launch { removal.signOut(account) }
    }
}
