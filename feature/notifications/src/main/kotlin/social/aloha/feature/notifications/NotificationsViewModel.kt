// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.data.notifications.NotificationPage
import social.aloha.core.data.notifications.NotificationsRepository
import social.aloha.core.data.notifications.preview
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.trouble
import social.aloha.core.model.Account
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.ModerationWarning
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SeveranceEvent
import social.aloha.core.model.SignedInAccount
import social.aloha.core.ui.minuteTicks

/** One notification as its row draws it. [others] is how many did it besides [name]. */
internal data class NotificationRowUi(
    val key: String,
    val kind: NotificationKind,
    val name: String?,
    val others: Int,
    /** Who did it, newest first, as many as the server sent: each face opens its profile. */
    val people: List<Person>,
    val preview: String?,
    val statusId: String?,
    val accountId: String?,
    val groupKey: String?,
    val unread: Boolean,
    val at: Instant,
    /** The post's first picture or video, for a mention shown as a compact card. */
    val media: MediaAttachment? = null,
    val severance: SeveranceEvent? = null,
    val warning: ModerationWarning? = null,
) {
    @Immutable
    data class Person(val id: String, val name: String, val avatarUrl: String?)
}

/** Everyone in a group, once asked for: null while loading, [failed] when it could not be. */
internal data class GroupSheet(val accounts: List<Account>? = null, val failed: Boolean = false)

internal data class NotificationsUiState(
    val rows: List<NotificationRowUi> = emptyList(),
    val kinds: Set<NotificationKind> = emptySet(),
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val loadingOlder: Boolean = false,
    val reachedEnd: Boolean = false,
    val trouble: Trouble? = null,
    val filtering: Boolean = false,
    val pendingRequests: Int = 0,
    val group: GroupSheet? = null,
    val now: Instant = Instant.EPOCH,
    val askedForPermission: Boolean = false,
    /** What a brief notice says after an action, as a string resource; null for none. */
    val notice: Int? = null,
    /**
     * Where "Learn more" leads: the reader's server, whose web pages explain cut follows and warnings;
     * null on Nextcloud Social, which has no such pages.
     */
    val origin: String? = null,
)

/**
 * The notifications of the active account, newest first. Coming on screen refreshes and marks what it
 * shows read; while on screen, a poll that finds more refreshes it. Rows newer than the read marker the
 * screen opened with stay marked new until the next refresh, so the person sees what they had not read.
 */
@HiltViewModel
internal class NotificationsViewModel @Inject constructor(
    accounts: AccountRepository,
    private val repository: NotificationsRepository,
    private val filtering: NotificationFiltering,
    unread: UnreadCounts,
    private val settings: SyncSettings,
    private val interactions: StatusInteractions,
    clock: Clock,
) : ViewModel() {
    private data class Control(
        val items: List<NotificationItem> = emptyList(),
        val marker: String? = null,
        val kinds: Set<NotificationKind> = emptySet(),
        val refreshing: Boolean = false,
        val loadedOnce: Boolean = false,
        val loadingOlder: Boolean = false,
        val olderThan: String? = null,
        val trouble: Trouble? = null,
        val pendingRequests: Int = 0,
        val group: GroupSheet? = null,
        val notice: Int? = null,
    ) {
        fun loaded(answer: Answer<NotificationPage>, marker: String?): Control = when (answer) {
            is Answer.Got -> copy(
                items = answer.value.items,
                marker = marker,
                olderThan = answer.value.olderThan,
                refreshing = false,
                loadedOnce = true,
                trouble = null,
            )

            is Answer.Missed -> copy(refreshing = false, loadedOnce = true, trouble = answer.error.trouble)
        }
    }

    private val account: StateFlow<SignedInAccount?> = accounts.activeAccount
    private val control = MutableStateFlow(Control())
    private var shown = false

    // rows are built once for what was loaded, off the main thread; the clock only moves their ages
    private val rows = control.map { it.items to it.marker }.distinctUntilChanged()
        .map { (items, marker) -> items.map { it.toRow(marker) } }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<NotificationsUiState> =
        combine(control, rows, account, minuteTicks(clock), settings.askedForNotifications) {
                control,
                rows,
                account,
                now,
                asked,
            ->
            NotificationsUiState(
                rows = rows,
                kinds = control.kinds,
                refreshing = control.refreshing,
                loadedOnce = control.loadedOnce,
                loadingOlder = control.loadingOlder,
                reachedEnd = control.loadedOnce && control.olderThan == null,
                trouble = control.trouble,
                filtering = account?.capabilities?.notificationPolicy == true,
                pendingRequests = control.pendingRequests,
                group = control.group,
                now = now,
                askedForPermission = asked,
                notice = control.notice,
                origin = account?.takeUnless { it.capabilities.isNextcloudSocial }?.let { "https://${it.host}" },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), NotificationsUiState())

    init {
        viewModelScope.launch {
            // another account starts from nothing
            account.filterNotNull().map { it.id }.distinctUntilChanged().collect {
                control.value = Control()
                if (shown) refresh()
            }
        }
        viewModelScope.launch {
            // a poll that found more while the list is on screen brings it in
            unread.all.map { counts -> account.value?.let { counts[it.id] } ?: 0 }.distinctUntilChanged().collect {
                if (it > 0 && shown) refresh()
            }
        }
    }

    fun onShown(isShown: Boolean) {
        shown = isShown
        if (isShown) viewModelScope.launch { refresh() }
    }

    fun onRefresh() {
        viewModelScope.launch { refresh() }
    }

    /** Toggles [kind]; none chosen shows every kind. Follows include follow requests. */
    fun onKind(kind: NotificationKind) {
        control.update { it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind) }
        viewModelScope.launch { refresh(asked = false) }
    }

    fun onAllKinds() {
        control.update { it.copy(kinds = emptySet()) }
        viewModelScope.launch { refresh(asked = false) }
    }

    fun onNearEnd() {
        val state = control.value
        val account = account.value
        val olderThan = state.olderThan
        val busy = state.loadingOlder || state.refreshing
        if (account == null || olderThan == null || busy) return
        control.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val answer = repository.page(account, state.kinds.expanded(), olderThan)
            control.update { current ->
                when (answer) {
                    is Answer.Got -> current.copy(
                        items = (current.items + answer.value.items).distinctBy { it.key },
                        olderThan = answer.value.olderThan,
                        loadingOlder = false,
                    )

                    is Answer.Missed -> current.copy(loadingOlder = false, trouble = answer.error.trouble)
                }
            }
        }
    }

    fun onOthers(groupKey: String) {
        val account = account.value ?: return
        control.update { it.copy(group = GroupSheet()) }
        viewModelScope.launch {
            val answer = repository.groupAccounts(account, groupKey)
            control.update {
                it.copy(group = GroupSheet((answer as? Answer.Got)?.value, failed = answer is Answer.Missed))
            }
        }
    }

    /** Mutes the conversation of the mention or reply [key]; a mute already there stays. */
    fun onMuteConversation(key: String) {
        val account = account.value ?: return
        val status = control.value.items.firstOrNull { it.key == key }?.status ?: return
        viewModelScope.launch {
            val error = if (status.muted) null else interactions.toggle(account, status, Toggle.MuteConversation)
            val notice = if (error == null) R.string.notifications_muted else R.string.notifications_mute_failed
            control.update { it.copy(notice = notice) }
        }
    }

    /** Everything shown counts as read, on the server too. */
    fun onMarkAllRead() {
        val newest = control.value.items.map { it.newestId }
            .reduceOrNull { a, b -> if (NotificationItem.isNewer(b, a)) b else a } ?: return
        control.update { it.copy(marker = newest) }
        account.value?.let { viewModelScope.launch { repository.markRead(it, newest) } }
    }

    /**
     * Accepts or declines every follow request of row [key], which may group several; the row goes once
     * the server took each answer.
     */
    fun onFollowRequest(key: String, accept: Boolean) {
        val item = control.value.items.firstOrNull { it.key == key } ?: return
        val reader = account.value ?: return
        viewModelScope.launch {
            val done = item.accounts.isNotEmpty() &&
                item.accounts.map { repository.answerFollowRequest(reader, it.id, accept) }.all { it }
            control.update {
                val notice = when {
                    !done -> R.string.notifications_request_failed
                    accept -> R.string.notifications_request_accepted
                    else -> R.string.notifications_request_declined
                }
                it.copy(items = if (done) it.items - item else it.items, notice = notice)
            }
        }
    }

    fun onNoticeShown() {
        control.update { it.copy(notice = null) }
    }

    fun onAskedForPermission() {
        viewModelScope.launch { settings.setAskedForNotifications() }
    }

    fun onGroupClosed() {
        control.update { it.copy(group = null) }
    }

    private suspend fun refresh(asked: Boolean = true) {
        val account = account.value
        if (account == null || control.value.refreshing) return
        control.update { it.copy(refreshing = true) }
        val kinds = control.value.kinds
        // a marker that cannot be read leaves the rows as new as they were
        val marker = (repository.readMarker(account) as? Answer.Got)?.value ?: control.value.marker
        val answer = repository.page(account, kinds.expanded())
        // the kinds or the account changed meanwhile, and their own refresh was turned away: run it now
        val current = account.id == this.account.value?.id && kinds == control.value.kinds
        control.update { if (current) it.loaded(answer, marker) else it.copy(refreshing = false) }
        if (!current) return refresh(asked)
        afterLoad(account, (answer as? Answer.Got)?.value, kinds.isEmpty(), asked)
    }

    /**
     * What is on screen is read, only from the whole list: a filtered one would move the marker past
     * kinds nobody saw. The count of filtered senders comes with coming on screen, not with every chip.
     */
    private suspend fun afterLoad(
        account: SignedInAccount,
        page: NotificationPage?,
        unfiltered: Boolean,
        asked: Boolean,
    ) {
        if (unfiltered && shown) page?.items?.firstOrNull()?.let { repository.markRead(account, it.newestId) }
        if (asked && account.capabilities.notificationPolicy) {
            val pending = (filtering.policy(account) as? Answer.Got)?.value?.summary?.pendingRequestsCount ?: 0
            control.update { it.copy(pendingRequests = pending) }
        }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

private fun NotificationItem.toRow(marker: String?) = NotificationRowUi(
    key = key,
    kind = kind,
    name = newest?.bestDisplayName,
    others = others,
    people = accounts.map { NotificationRowUi.Person(it.id, it.bestDisplayName, it.avatar) },
    preview = preview(),
    statusId = status?.id,
    accountId = newest?.id,
    groupKey = groupKey,
    unread = marker != null && NotificationItem.isNewer(newestId, marker),
    at = latestAt,
    media = status?.displayed?.mediaAttachments?.firstOrNull(),
    severance = severance,
    warning = warning,
)

private fun Set<NotificationKind>.expanded(): Set<NotificationKind> =
    if (NotificationKind.Follow in this) this + NotificationKind.FollowRequest else this
