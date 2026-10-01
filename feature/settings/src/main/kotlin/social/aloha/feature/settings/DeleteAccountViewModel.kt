// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRemoval
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.network.ApiError
import social.aloha.core.sync.AccountSignOut

/** How the account in use can be deleted from here. */
internal enum class DeletionMode {
    /** In the app: a Nextcloud Social account connected to its Nextcloud, whose app password asks. */
    InApp,

    /** On Nextcloud Social, once connected to the Nextcloud, which the section above does. */
    NeedsNextcloud,

    /** Anywhere else, on the server's own website. */
    OnTheWeb,
}

/** A refusal: the server's own words where it gave some (it names the handle to type), else why. */
internal sealed interface DeletionRefusal {
    data class Server(val message: String) : DeletionRefusal

    data object TooOften : DeletionRefusal

    data object Failed : DeletionRefusal
}

/**
 * [handle] is the one to type, `@user@server`; [webPage] is where a server other than Nextcloud
 * Social deletes accounts.
 */
internal data class DeleteAccountUiState(
    val mode: DeletionMode = DeletionMode.OnTheWeb,
    val handle: String = "",
    val webPage: String? = null,
    val deleting: Boolean = false,
    val refusal: DeletionRefusal? = null,
)

@HiltViewModel
internal class DeleteAccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val removal: AccountRemoval,
    private val signOuts: AccountSignOut,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ViewModel() {
    private val deleting = MutableStateFlow(false)
    private val refusal = MutableStateFlow<DeletionRefusal?>(null)

    val uiState: StateFlow<DeleteAccountUiState> =
        combine(accounts.activeAccount, deleting, refusal) { account, deleting, refusal ->
            DeleteAccountUiState(
                mode = when {
                    account?.capabilities?.isNextcloudSocial != true -> DeletionMode.OnTheWeb
                    account.nextcloudConnected -> DeletionMode.InApp
                    else -> DeletionMode.NeedsNextcloud
                },
                handle = account?.qualifiedHandle.orEmpty(),
                // Mastodon's own page for it; other servers keep theirs elsewhere, and none is guessed
                webPage = account?.takeIf { it.capabilities.softwareName == MASTODON }
                    ?.let { "https://${it.host}/settings/delete" },
                deleting = deleting,
                refusal = refusal,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), DeleteAccountUiState())

    /**
     * Deletes the account in use on its server with [typed] as the confirmation, then signs it out of
     * this device as signing out would; a refusal leaves everything as it was. It runs in the
     * application's scope: once the server is asked, leaving Settings must not leave a deleted account
     * signed in here.
     */
    fun onDelete(typed: String) {
        val account = accounts.activeAccount.value ?: return
        if (deleting.value || !confirms(typed, account.qualifiedHandle)) return
        deleting.value = true
        refusal.value = null
        scope.launch {
            val error = removal.deleteOnServer(account, typed)
            if (error == null) signOuts.signOut(account).join() else refusal.value = refusalOf(error)
            deleting.value = false
        }
    }

    fun onRefusalShown() {
        refusal.value = null
    }

    companion object {
        private const val STOP_MILLIS = 5_000L
        private const val MASTODON = "mastodon"

        /**
         * Whether [typed] names the account [handle] (`@user@server`) closely enough to ask: its username,
         * in any case, with or without a domain or leading `@`. Only the username is checked here: the
         * server's domain for its handles can differ from the address signed in to, so the server checks
         * the rest and says which handle to type.
         */
        fun confirms(typed: String, handle: String): Boolean {
            val name = typed.trim().trimStart('@').substringBefore('@').lowercase()
            return name.isNotEmpty() && name == handle.trimStart('@').substringBefore('@').lowercase()
        }

        private fun refusalOf(error: ApiError): DeletionRefusal = when (error) {
            is ApiError.Unprocessable -> error.message?.let(DeletionRefusal::Server) ?: DeletionRefusal.Failed
            is ApiError.RateLimited -> DeletionRefusal.TooOften
            else -> DeletionRefusal.Failed
        }
    }
}
