// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.NotificationsKey
import social.aloha.core.navigation.SearchKey
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
    /** Whether its server is Nextcloud Social, whose archived posts and interests the sheet then offers. */
    val nextcloudSocial: Boolean = false,
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
    private val shortcuts: AccountShortcuts,
    preferences: ModePreferences,
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    /** The modes the navigation shows, for the server of the account in use. */
    val modes: StateFlow<ModeNavigation> = combine(preferences.modeChoices, accounts.activeAccount) { choices, reader ->
        val capabilities = reader?.capabilities ?: ServerCapabilities.minimal("")
        ModeNavigation(choices.wide(capabilities), choices.narrow(capabilities))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModeNavigation())

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
        // again, the cache trimmed within its budget, and copies that no post attaches any more deleted;
        // an extra window opened beside the app leaves that to the main one
        if (savedState.get<Boolean>(WindowActivity.EXTRA_WINDOW) != true) {
            viewModelScope.launch {
                maintenance.checkAll()
                sweeper.sweep()
                outbox.sweep(File(context.filesDir, Outbox.UPLOADS))
            }
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
        // the launcher's and the share sheet's shortcuts follow who is signed in
        viewModelScope.launch {
            accounts.accounts
                .map { all -> all.sortedBy { it.addedAt } }
                .distinctUntilChanged { a, b -> a.map(::shortcutOf) == b.map(::shortcutOf) }
                .collect { shortcuts.publish(it) }
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
                    nextcloudSocial = account.capabilities.isNextcloudSocial,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /**
     * Opens [address], which another app asked to open. [handedOver]: the reader chose "Open in Aloha"
     * for it in the share sheet, so a post the server finds opens whatever its address looks like,
     * else what any link opens.
     */
    fun openExternal(address: String, handedOver: Boolean = false) {
        if (!handedOver) {
            external.value = address
            return
        }
        viewModelScope.launch {
            val reader = accounts.activeAccount.filterNotNull().first()
            val key = runCatching { links.destination(reader, address, handedOver = true) }.getOrNull()
            if (key != null) destination.value = reader.id to key else external.value = address
        }
    }

    fun externalHandled() {
        external.value = null
    }

    /**
     * Opens draft [draftId] of [accountId] in the composer, or that account's drafts without one; a new
     * id is a new post. A notification, a widget or a shortcut asks, so an account no longer signed in
     * here opens nothing; none named is the account in use.
     */
    fun openDraft(accountId: String?, draftId: String?) = openAs(accountId) { id ->
        draftId?.let { ComposerKey(id, draftId = it) } ?: DraftsKey(id)
    }

    /**
     * Opens what a notification is about, as the account it came to: its post, else the profile of
     * whoever did it, else the notifications. An account no longer signed in here opens nothing; none
     * named is the account in use.
     */
    fun openNotification(accountId: String?, statusId: String?, profileId: String?) = openAs(accountId) { id ->
        statusId?.let { ThreadKey(id, it) } ?: profileId?.let { AccountKey(id, id = it) } ?: NotificationsKey
    }

    /** Opens search, as [accountId] or the account in use. */
    fun openSearch(accountId: String?) = openAs(accountId, key = ::SearchKey)

    /** Switches to the account [accountFor] picks and opens what [key] names there. */
    private fun openAs(accountId: String?, shared: Boolean = false, key: (accountId: String) -> NavKey) {
        viewModelScope.launch {
            val id = accounts.accountFor(accountId, shared) ?: return@launch
            accounts.activate(id)
            destination.value = id to key(id)
        }
    }

    /**
     * Opens the composer on what another app shared, as [accountId] when a Direct Share shortcut named
     * one that is still signed in, else as the active account; one shared before any account is signed in
     * waits for the sign-in.
     */
    fun share(content: SharedContent, accountId: String? = null) = openAs(accountId, shared = true) { id ->
        ComposerKey(
            id,
            draftId = UUID.randomUUID().toString(),
            sharedText = content.text,
            sharedMedia = content.media.map { it.toString() },
        )
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

    private companion object {
        const val PUSH_FORGET_MILLIS = 5_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What an account's shortcut shows, so it is published again only when that changes. */
private fun shortcutOf(account: SignedInAccount) = Triple(account.id, account.qualifiedHandle, account.avatarUrl)

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

// how long a shortcut waits for the account in use to load from disk on a cold start
private const val LOAD_MILLIS = 3_000L

/**
 * The account a request from outside the app acts as, which only asks: [accountId] while it is signed in
 * here, and nothing opens for one signed out since. Something [shared] is the exception: it goes to the
 * account in use instead, waiting for a sign-in if need be. With no account named, the account in use,
 * once it has loaded within [loadMillis]; with none signed in, null, so nothing opens later out of the blue.
 */
internal suspend fun AccountRepository.accountFor(
    accountId: String?,
    shared: Boolean,
    loadMillis: Long = LOAD_MILLIS,
): String? {
    val named = accountId?.let { byId(it) }
    return when {
        named != null -> named.id
        shared -> activeAccount.filterNotNull().first().id
        accountId != null -> null
        else -> withTimeoutOrNull(loadMillis) { activeAccount.filterNotNull().first() }?.id
    }
}
