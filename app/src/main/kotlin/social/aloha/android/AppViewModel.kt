// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import social.aloha.core.data.AccountMaintenance
import social.aloha.core.data.AccountRemoval
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.data.timeline.CacheSweeper
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.NotificationsKey
import social.aloha.core.navigation.ThreadKey
import social.aloha.core.sync.LocalNotifications
import social.aloha.core.sync.PostQueue
import social.aloha.core.sync.PushRegistrar

/** What the root of the app shows. */
sealed interface AppSession {
    data object Loading : AppSession

    /** No account yet, a new sign-in for one the server refused, or one being added beside others ([adding]). */
    data class SigningIn(val adding: Boolean = false) : AppSession

    /**
     * [needsReauth]: the server refused the token; the cache stays and a banner offers a new sign-in.
     * [serverAccountId] is the person's id on their server, which their own profile opens by.
     */
    data class SignedIn(
        val accountId: String,
        val serverAccountId: String,
        val handle: String,
        val needsReauth: Boolean,
    ) : AppSession
}

/** One signed-in account as the switcher lists it. */
data class SwitcherAccount(
    val id: String,
    val displayName: String,
    val handle: String,
    val avatarUrl: String?,
    val active: Boolean,
    val needsReauth: Boolean,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val removal: AccountRemoval,
    private val caches: DeviceCaches,
    private val links: LinkOpener,
    maintenance: AccountMaintenance,
    sweeper: CacheSweeper,
    private val outbox: Outbox,
    private val queue: PostQueue,
    private val unread: UnreadCounts,
    private val localNotifications: LocalNotifications,
    private val push: PushRegistrar,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val signingInAgain = MutableStateFlow(false)
    private val external = MutableStateFlow<String?>(null)

    /** A link another app asked to open, waiting for the shell to open it. */
    val pendingLink: StateFlow<String?> = external.asStateFlow()
    private val destination = MutableStateFlow<Pair<String, NavKey>?>(null)

    /**
     * A screen of the app's own asked for from outside it, a draft to finish, with the account whose
     * shell opens it, waiting for that shell.
     */
    val pendingDestination: StateFlow<Pair<String, NavKey>?> = destination.asStateFlow()
    private val adding = MutableStateFlow(false)

    init {
        // once per launch, off the main thread: moved API bases are found, stale capabilities detected
        // again, the cache trimmed within its budget, and copies that no post attaches any more deleted
        viewModelScope.launch {
            maintenance.checkAll()
            sweeper.sweep()
            outbox.sweep(File(context.filesDir, Outbox.UPLOADS))
        }
        // a sign-in that succeeded ends the request for one, and an added account becomes the active one
        // and what it queued while the sign-in lapsed goes out, as does anything left waiting since last time
        viewModelScope.launch {
            accounts.activeAccount.filterNotNull().filter { !it.needsReauth }.distinctUntilChanged { a, b ->
                a.id ==
                    b.id
            }
                .collect { account ->
                    signingInAgain.value = false
                    queue.resume(account.id)
                }
        }
        viewModelScope.launch {
            accounts.activeAccount.filterNotNull().map { it.id }.distinctUntilChanged().collect { adding.value = false }
        }
    }

    val session: StateFlow<AppSession> =
        combine(accounts.accounts, accounts.activeAccount, signingInAgain, adding, ::sessionOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppSession.Loading)

    /** The active account's unread notifications, as its server last said. */
    val unreadNotifications: StateFlow<Int> =
        combine(accounts.activeAccount, unread.all) { active, counts -> active?.let { counts[it.id] } ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val switcher: StateFlow<List<SwitcherAccount>> =
        combine(accounts.accounts, accounts.activeAccount) { all, active ->
            all.sortedBy { it.addedAt }.map { account ->
                SwitcherAccount(
                    id = account.id,
                    displayName = account.displayName.ifBlank { account.handle },
                    handle = account.qualifiedHandle,
                    avatarUrl = account.avatarUrl,
                    active = account.id == active?.id,
                    needsReauth = account.needsReauth,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun openExternal(address: String) {
        external.value = address
    }

    fun externalHandled() {
        external.value = null
    }

    /**
     * Opens draft [draftId] of [accountId] in the composer, or that account's drafts without one; a new
     * id is a new post. A notification or a widget asks, so an account no longer signed in here opens
     * nothing.
     */
    fun openDraft(accountId: String, draftId: String?) = openAs(accountId) {
        draftId?.let { ComposerKey(accountId, draftId = it) } ?: DraftsKey(accountId)
    }

    /**
     * Opens what a notification is about, as the account it came to: its post, else the profile of
     * whoever did it. An account no longer signed in here opens nothing.
     */
    fun openNotification(accountId: String, statusId: String?, profileId: String?) = openAs(accountId) {
        statusId?.let { ThreadKey(accountId, it) } ?: profileId?.let { AccountKey(accountId, id = it) }
            ?: NotificationsKey
    }

    /**
     * Switches to [accountId] and opens what [key] names there; what lives outside the app asks for it,
     * so an account no longer signed in here opens nothing.
     */
    private fun openAs(accountId: String, key: () -> NavKey) {
        viewModelScope.launch {
            if (accounts.byId(accountId) == null) return@launch
            accounts.activate(accountId)
            destination.value = accountId to key()
        }
    }

    /**
     * Opens the composer on what another app shared, as the active account; one shared before any
     * account is signed in waits for the sign-in.
     */
    fun share(content: SharedContent) {
        viewModelScope.launch {
            val account = accounts.activeAccount.filterNotNull().first()
            destination.value = account.id to ComposerKey(
                account.id,
                draftId = UUID.randomUUID().toString(),
                sharedText = content.text,
                sharedMedia = content.media.map { it.toString() },
            )
        }
    }

    fun destinationHandled() {
        destination.value = null
    }

    /**
     * Where [address] opens in the app: a post, a profile or a hashtag the reader's server can find;
     * null for the browser. The screen asks, so an answer arriving after it has gone lands nowhere.
     */
    suspend fun destination(address: String, fromPost: Boolean): NavKey? =
        accounts.activeAccount.value?.let { links.destination(it, address, fromPost) }

    fun signInAgain() {
        signingInAgain.value = true
    }

    /** Switching paints the other account from its cache at once; nothing waits for its server. */
    fun switchTo(id: String) {
        viewModelScope.launch { accounts.activate(id) }
    }

    fun addAccount() {
        adding.value = true
    }

    fun cancelAdding() {
        adding.value = false
    }

    /**
     * Signs the account in use out of this device; another becomes active, or sign-in shows. Once none
     * is left, the images and responses kept for them go too.
     */
    fun signOut() {
        viewModelScope.launch {
            accounts.activeAccount.value?.let {
                // the server is told to stop pushing while the token still works, but an unreachable one
                // never keeps the account here for long
                withTimeoutOrNull(PUSH_FORGET_MILLIS) { push.forget(it) }
                removal.signOut(it)
                localNotifications.forget(it.id)
            }
            if (accounts.all().isEmpty()) caches.clear()
        }
    }

    private fun sessionOf(
        all: List<SignedInAccount>,
        active: SignedInAccount?,
        again: Boolean,
        adding: Boolean,
    ): AppSession = when {
        active == null && all.isEmpty() -> AppSession.SigningIn()
        active == null -> AppSession.Loading
        again && active.needsReauth -> AppSession.SigningIn()
        adding -> AppSession.SigningIn(adding = true)
        else -> AppSession.SignedIn(active.id, active.serverAccountId, active.qualifiedHandle, active.needsReauth)
    }

    private companion object {
        const val PUSH_FORGET_MILLIS = 5_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
