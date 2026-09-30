// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.data.trouble
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.ui.minuteTicks

/** One notification as its row draws it. [others] is how many did it besides [name]. */
internal data class NotificationRowUi(
    val key: String,
    val kind: NotificationKind,
    val name: String?,
    val others: Int,
    val avatars: List<String?>,
    val preview: String?,
    val statusId: String?,
    val accountId: String?,
    val groupKey: String?,
    val unread: Boolean,
    val at: Instant,
)

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
    )

    private val account: StateFlow<SignedInAccount?> = accounts.activeAccount
    private val control = MutableStateFlow(Control())
    private var shown = false

    val uiState: StateFlow<NotificationsUiState> =
        combine(control, account, minuteTicks(clock)) { control, account, now ->
            NotificationsUiState(
                rows = control.items.map { it.toRow(control.marker) },
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

    private fun Control.loaded(answer: Answer<NotificationPage>, marker: String?): Control = when (answer) {
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

    private fun NotificationItem.toRow(marker: String?) = NotificationRowUi(
        key = key,
        kind = kind,
        name = accounts.firstOrNull()?.bestDisplayName,
        others = (count - 1).coerceAtLeast(0),
        avatars = accounts.map { it.avatar },
        preview = status?.displayed?.let { shown ->
            shown.spoilerText.ifBlank {
                StatusHtmlParser.plainText(shown.content)
            }.take(PREVIEW_LENGTH).ifBlank { null }
        },
        statusId = status?.id,
        accountId = accounts.firstOrNull()?.id,
        groupKey = groupKey,
        unread = marker != null && NotificationItem.isNewer(newestId, marker),
        at = latestAt,
    )

    private fun Set<NotificationKind>.expanded(): Set<NotificationKind> =
        if (NotificationKind.Follow in this) this + NotificationKind.FollowRequest else this

    private companion object {
        const val STOP_MILLIS = 5_000L
        const val PREVIEW_LENGTH = 280
    }
}
