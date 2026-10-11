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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.notifications.NotificationFiltering
import social.aloha.core.data.notifications.NotificationsRepository
import social.aloha.core.data.notifications.preview
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.trouble
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.Account
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.ModerationWarning
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SeveranceEvent
import social.aloha.core.model.SignedInAccount
import social.aloha.core.ui.SeenBy
import social.aloha.core.ui.minuteTicks

/**
 * One notification as its row draws it. [others] is how many did it besides [name]. Where every account
 * shows together, [to] is the account it came to; one that came to another account than the one in use
 * names its post by [url] and its people by handle, as that server knows them.
 */
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
    val to: SeenBy? = null,
    val url: String? = null,
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
    /** Until when no notification is raised; null, or a time gone by, while they come as they arrive. */
    val pausedUntil: Instant? = null,
)

/**
 * The notifications of the account in use, or of every account where they show together, newest first.
 * Coming on screen refreshes and marks what it shows read, each account on its own server; while on
 * screen, a poll that finds more refreshes it. Rows newer than the read marker the screen opened with
 * stay marked new until the next refresh, so the person sees what they had not read.
 */
@HiltViewModel
internal class NotificationsViewModel @Inject constructor(
    allAccounts: AllAccounts,
    private val repository: NotificationsRepository,
    private val filtering: NotificationFiltering,
    unread: UnreadCounts,
    private val settings: SyncSettings,
    private val interactions: StatusInteractions,
    private val accountSettings: AccountSettingsStore,
    clock: Clock,
) : ViewModel() {
    private data class Control(
        val parts: Map<String, NotificationPart> = emptyMap(),
        val kinds: Set<NotificationKind> = emptySet(),
        val refreshing: Boolean = false,
        val loadedOnce: Boolean = false,
        val loadingOlder: Boolean = false,
        val pendingRequests: Int = 0,
        val group: GroupSheet? = null,
        val notice: Int? = null,
    )

    /** The accounts shown, the one in use first. */
    private val accounts: StateFlow<List<SignedInAccount>> =
        allAccounts.shown.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val control = MutableStateFlow(Control())
    private var shown = false

    @Volatile private var merged = MergedNotifications(emptyList(), emptyList())

    /** Where several accounts show, each one's colour, its own or its server's, which marks its rows. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val colours = accounts.flatMapLatest { list ->
        if (list.size < 2) {
            flowOf(emptyMap())
        } else {
            combine(
                list.map { account ->
                    accountSettings.accent(account.id).map { account.id to (it ?: account.capabilities.theme?.colour) }
                },
            ) { it.toMap() }
        }
    }

    // rows are built once for what was loaded, off the main thread; the clock only moves their ages
    private val rows = combine(control.map { it.parts }.distinctUntilChanged(), accounts, colours) {
            parts,
            accounts,
            colours,
        ->
        mergeNotifications(accounts.map { it.id }, parts).also { merged = it }.items.map { owned ->
            owned.toRow(accounts, parts[owned.owner]?.marker, colours[owned.owner])
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<NotificationsUiState> =
        combine(control, rows, accounts, minuteTicks(clock), settings.askedForNotifications) {
                control,
                rows,
                accounts,
                now,
                asked,
            ->
            val inUse = accounts.firstOrNull()
            NotificationsUiState(
                rows = rows,
                kinds = control.kinds,
                refreshing = control.refreshing,
                loadedOnce = control.loadedOnce,
                loadingOlder = control.loadingOlder,
                reachedEnd = control.loadedOnce && control.parts.values.all { it.olderThan == null },
                trouble = accounts.firstNotNullOfOrNull { control.parts[it.id]?.trouble },
                filtering = inUse?.capabilities?.notificationPolicy == true,
                pendingRequests = control.pendingRequests,
                group = control.group,
                now = now,
                askedForPermission = asked,
                notice = control.notice,
                origin = inUse?.takeUnless { it.capabilities.isNextcloudSocial }?.let { "https://${it.host}" },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), NotificationsUiState())

    init {
        viewModelScope.launch {
            // other accounts start from nothing
            accounts.filter { it.isNotEmpty() }.map { list -> list.map { it.id } }.distinctUntilChanged().collect {
                control.value = Control()
                if (shown) refresh()
            }
        }
        viewModelScope.launch {
            // a poll that found more while the list is on screen brings it in
            combine(unread.all, accounts) { counts, accounts -> accounts.sumOf { counts[it.id] ?: 0 } }
                .distinctUntilChanged()
                .collect { if (it > 0 && shown) refresh() }
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

    /** The next page of each account the list waits for. */
    fun onNearEnd() {
        val state = control.value
        val behind = accounts.value.filter { it.id in merged.behind }
        if (behind.isEmpty() || state.loadingOlder || state.refreshing) return
        control.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val pages = coroutineScope {
                behind.map { account ->
                    async {
                        val olderThan = state.parts[account.id]?.olderThan
                        account.id to olderThan?.let { repository.page(account, state.kinds.expanded(), it) }
                    }
                }.awaitAll()
            }
            control.update { current ->
                // a page asked for below what a refresh has since replaced belongs to nothing shown now
                val parts = pages.fold(current.parts) { parts, (id, answer) ->
                    val part = parts[id]?.takeIf { it.olderThan == state.parts[id]?.olderThan } ?: return@fold parts
                    parts + (id to part.with(answer))
                }
                current.copy(parts = parts, loadingOlder = false)
            }
        }
    }

    /** Everyone in the group of row [key], as the account it came to knows them. */
    fun onOthers(key: String) {
        val (account, owned) = merged.owned(accounts.value) { it.key == key } ?: return
        val groupKey = owned.item.groupKey ?: return
        control.update { it.copy(group = GroupSheet()) }
        viewModelScope.launch {
            val answer = repository.groupAccounts(account, groupKey)
            val foreign = owned.owner != accounts.value.firstOrNull()?.id
            val people = (answer as? Answer.Got)?.value?.map { if (foreign) it.elsewhere(account.host) else it }
            control.update { it.copy(group = GroupSheet(people, failed = answer is Answer.Missed)) }
        }
    }

    /** Mutes the conversation of the mention or reply [key], as the account it came to; a mute already there stays. */
    fun onMuteConversation(key: String) {
        val (account, owned) = merged.owned(accounts.value) { it.key == key } ?: return
        val status = owned.item.status ?: return
        viewModelScope.launch {
            val error = if (status.muted) null else interactions.toggle(account, status, Toggle.MuteConversation)
            val notice = if (error == null) R.string.notifications_muted else R.string.notifications_mute_failed
            control.update { it.copy(notice = notice) }
        }
    }

    /** Everything shown counts as read, on each account's server too. */
    fun onMarkAllRead() {
        val newest = control.value.parts.mapNotNull { (id, part) -> part.newestId()?.let { id to it } }.toMap()
        control.update { current ->
            current.copy(
                parts = current.parts.mapValues { (id, part) ->
                    part.copy(marker = newest[id] ?: part.marker)
                },
            )
        }
        viewModelScope.launch {
            accounts.value.forEach { account -> newest[account.id]?.let { repository.markRead(account, it) } }
        }
    }

    /**
     * Accepts or declines every follow request of row [key], which may group several, as the account they
     * came to; the row goes once the server took each answer.
     */
    fun onFollowRequest(key: String, accept: Boolean) {
        val (account, owned) = merged.owned(accounts.value) { it.key == key } ?: return
        val item = owned.item
        viewModelScope.launch {
            val done = item.accounts.isNotEmpty() &&
                item.accounts.map { repository.answerFollowRequest(account, it.id, accept) }.all { it }
            control.update {
                val notice = when {
                    !done -> R.string.notifications_request_failed
                    accept -> R.string.notifications_request_accepted
                    else -> R.string.notifications_request_declined
                }
                val part = it.parts[owned.owner]
                val parts = if (done &&
                    part != null
                ) {
                    it.parts + (owned.owner to part.copy(items = part.items - item))
                } else {
                    it.parts
                }
                it.copy(parts = parts, notice = notice)
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
        val shownNow = accounts.value
        if (shownNow.isEmpty() || control.value.refreshing) return
        control.update { it.copy(refreshing = true) }
        val kinds = control.value.kinds
        val parts = coroutineScope {
            shownNow.map { async { it.id to repository.newest(it, kinds.expanded(), control.value.parts[it.id]) } }
                .awaitAll()
        }.toMap()
        // the kinds or the accounts changed meanwhile, and their own refresh was turned away: run it now
        val current = shownNow.map { it.id } == accounts.value.map { it.id } && kinds == control.value.kinds
        control.update {
            if (current) it.copy(parts = parts, refreshing = false, loadedOnce = true) else it.copy(refreshing = false)
        }
        if (!current) return refresh(asked)
        afterLoad(shownNow, parts, kinds.isEmpty(), asked)
    }

    /**
     * What is on screen is read, only from the whole list: a filtered one would move a marker past kinds
     * nobody saw. The count of filtered senders, the account in use's, comes with coming on screen, not
     * with every chip.
     */
    private suspend fun afterLoad(
        shownNow: List<SignedInAccount>,
        parts: Map<String, NotificationPart>,
        unfiltered: Boolean,
        asked: Boolean,
    ) {
        val read = if (unfiltered &&
            shown
        ) {
            shownNow.mapNotNull { a -> parts[a.id]?.newestId()?.let { a to it } }
        } else {
            null
        }
        read?.forEach { (account, newest) -> repository.markRead(account, newest) }
        val inUse = shownNow.first()
        if (asked && inUse.capabilities.notificationPolicy) {
            val pending = (filtering.policy(inUse) as? Answer.Got)?.value?.summary?.pendingRequestsCount ?: 0
            control.update { it.copy(pendingRequests = pending) }
        }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

/** [this] part with the next page [answer] brought, or the trouble it met; none asked for leaves it as it is. */
private fun NotificationPart.with(answer: Answer<social.aloha.core.data.notifications.NotificationPage>?) =
    when (answer) {
        is Answer.Got -> copy(
            items = (items + answer.value.items).distinctBy {
                it.key
            },
            olderThan = answer.value.olderThan,
        )

        is Answer.Missed -> copy(trouble = answer.error.trouble)

        null -> this
    }

/**
 * [account]'s newest notifications of [kinds], after [previous]; a marker that cannot be read leaves the
 * rows as new as they were, and a failure keeps what was there.
 */
private suspend fun NotificationsRepository.newest(
    account: SignedInAccount,
    kinds: Set<NotificationKind>,
    previous: NotificationPart?,
): NotificationPart {
    val before = previous ?: NotificationPart()
    val marker = (readMarker(account) as? Answer.Got)?.value ?: before.marker
    return when (val answer = page(account, kinds)) {
        is Answer.Got -> NotificationPart(answer.value.items, marker, answer.value.olderThan)
        is Answer.Missed -> before.copy(marker = marker, trouble = answer.error.trouble)
    }
}

private fun NotificationPart.newestId(): String? =
    items.map { it.newestId }.reduceOrNull { a, b -> if (NotificationItem.isNewer(b, a)) b else a }

/**
 * The row of [this] notification, unread past [marker]. Where several accounts show, it says which it came,
 * marked in its [colour]; one that came to another than the one in use names its post and people as that
 * server knows them.
 */
private fun OwnedNotification.toRow(accounts: List<SignedInAccount>, marker: String?, colour: Int?): NotificationRowUi {
    val account = accounts.firstOrNull { it.id == owner }
    val foreign = account != null && owner != accounts.first().id
    val people = if (foreign) item.accounts.map { it.elsewhere(account.host) } else item.accounts
    return NotificationRowUi(
        key = key,
        kind = item.kind,
        name = people.firstOrNull()?.bestDisplayName,
        others = item.others,
        people = people.map { NotificationRowUi.Person(it.id, it.bestDisplayName, it.avatar) },
        preview = item.preview(),
        statusId = item.status?.id?.takeUnless { foreign },
        accountId = people.firstOrNull()?.id,
        groupKey = item.groupKey,
        unread = marker != null && NotificationItem.isNewer(item.newestId, marker),
        at = item.latestAt,
        media = item.status?.displayed?.mediaAttachments?.firstOrNull(),
        severance = item.severance,
        warning = item.warning,
        to = account?.takeIf { accounts.size > 1 }?.let { SeenBy(it.qualifiedHandle, it.avatarUrl, colour) },
        url = item.status?.let { it.url ?: it.uri.takeIf(String::isNotEmpty) },
    )
}

private fun Set<NotificationKind>.expanded(): Set<NotificationKind> =
    if (NotificationKind.Follow in this) this + NotificationKind.FollowRequest else this
