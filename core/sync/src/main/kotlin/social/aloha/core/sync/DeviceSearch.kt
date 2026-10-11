// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.appsearch.app.ExperimentalAppSearchApi
import androidx.appsearch.builtintypes.PotentialAction
import androidx.appsearch.builtintypes.Thing
import androidx.appsearch.exceptions.AppSearchException
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.DeviceSearchSettings
import social.aloha.core.data.profile.ProfileRepository
import social.aloha.core.data.saved.PostPage
import social.aloha.core.data.saved.SavedPosts
import social.aloha.core.data.search.Searches
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.AppIntents
import timber.log.Timber

/**
 * What the phone's own search finds of the app: the signed-in accounts, the accounts each follows, its
 * bookmarked posts that anyone may see, and its recent searches. Timelines are never in it, and neither
 * is a post only followers or the people it names may read. A content warning stands in for what it
 * hides. Each entry opens in the app as the account it belongs to.
 *
 * Each account's entries are a namespace of their own, replaced whole on every refresh, and gone with the
 * account. A refresh runs daily while the setting is on and whenever an account comes or goes; turned
 * off, the index is emptied.
 */
@Singleton
public class DeviceSearch @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val index: SearchIndex,
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val saved: SavedPosts,
    private val searches: Searches,
    private val settings: DeviceSearchSettings,
) {
    private val work get() = WorkManager.getInstance(context)

    // every change to the index, and the last look at whether it may be made, one at a time: a refresh
    // fetches for a while, and its entries must not land after a sign-out or the setting turned off
    private val writes = Mutex()

    /** Keeps the refresh scheduled while the setting is on, and the index empty while it is off, until cancelled. */
    public suspend fun keepIndexed() {
        // ponytail: a bookmark or a follow made now is in the index at the next refresh, up to a day on; tell the
        // index from the toggle itself if that lag is felt
        var started = false
        settings.shown
            .combine(accounts.accounts.map { all -> all.map(SignedInAccount::id).toSet() }) { on, ids -> on to ids }
            .distinctUntilChanged()
            .collect { (on, _) ->
                if (on) {
                    // the daily refresh runs as soon as it is first scheduled; only a change since the app
                    // started, an account come or gone, or the setting turned on, asks for one now
                    scheduleRefresh(now = started)
                } else {
                    work.cancelUniqueWork(DAILY)
                    work.cancelUniqueWork(NOW)
                    writes.withLock { quietly { index.clear() } }
                }
                started = true
            }
    }

    /** Indexes [account] afresh; what could not be fetched leaves what was there. */
    public suspend fun refresh(account: SignedInAccount) {
        if (!settings.shown.first()) return
        val following = following(account) ?: return
        val bookmarks = bookmarks(account) ?: return
        val gathered = Gathered(following, bookmarks, searches.recent(account).first())
        writes.withLock {
            // still wanted, and the account still here, now that what it is made of has come
            if (settings.shown.first() && accounts.byId(account.id) != null) {
                quietly { index.replace(namespace(account.id), documents(context, account, gathered)) }
            }
        }
    }

    /** Takes [accountId]'s entries out, as it signs out of the device. */
    public suspend fun forget(accountId: String) {
        writes.withLock { quietly { index.remove(namespace(accountId)) } }
    }

    private fun scheduleRefresh(now: Boolean) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true)
            .build()
        work.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeviceSearchWorker>(Duration.ofDays(1)).setConstraints(online).build(),
        )
        if (now) {
            work.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DeviceSearchWorker>().setConstraints(online).build(),
            )
        }
    }

    private suspend fun following(account: SignedInAccount): List<Account>? {
        val all = mutableListOf<Account>()
        var next: HttpUrl? = null
        do {
            val page = (profiles.people(account, account.serverAccountId, followers = false, next) as? Answer.Got)
                ?.value ?: return null
            all += page.accounts
            next = page.next
        } while (next != null && all.size < MAX_FOLLOWING)
        return all.take(MAX_FOLLOWING)
    }

    /**
     * Up to [MAX_BOOKMARKS] bookmarks that may be indexed, each once, paging past the ones that may not;
     * at most [MAX_BOOKMARK_PAGES] pages, so a reader with thousands of private bookmarks is not paged through.
     */
    private suspend fun bookmarks(account: SignedInAccount): List<Status>? {
        val kept = LinkedHashMap<String, Status>()
        var page: PostPage? = null
        var pages = 0
        do {
            val got = (saved.bookmarks(account, page) as? Answer.Got)?.value ?: return null
            got.posts.map(Status::displayed).filter(::indexable).forEach { kept.putIfAbsent(it.id, it) }
            page = got
            pages++
            val more = !got.done && got.next != null
        } while (more && kept.size < MAX_BOOKMARKS && pages < MAX_BOOKMARK_PAGES)
        return kept.values.take(MAX_BOOKMARKS)
    }

    // the index is a convenience: a refusal from it is logged, never the reader's problem
    private suspend fun quietly(change: suspend () -> Unit) {
        try {
            change()
        } catch (e: AppSearchException) {
            Timber.tag(LogArea.Sync.name).w("Device search not updated: %s", e.resultCode)
        }
    }

    internal companion object {
        const val DAILY = "device-search"
        const val NOW = "device-search-now"
        const val MAX_FOLLOWING = 400
        const val MAX_BOOKMARKS = 200
        const val MAX_BOOKMARK_PAGES = 20
        private const val MAX_TEXT = 300
        private const val VIEW = "view"

        fun namespace(accountId: String) = "account:$accountId"

        /** [account]'s entries: itself, whom it follows, what it bookmarked, what it searched for. */
        fun documents(context: Context, account: SignedInAccount, gathered: Gathered): List<Thing> {
            val links = Links(context, account.id)
            val entries = listOf(itself(account, links)) +
                gathered.following.map { followed(it, links) } +
                kept(gathered.bookmarks, links) +
                gathered.recent.map { searched(it, links) }
            return entries.map { it.thing(namespace(account.id)) }
        }

        private fun itself(account: SignedInAccount, links: Links) = Entry(
            "self",
            account.displayName.ifBlank { account.handle },
            account.qualifiedHandle,
            links.open(profileId = account.serverAccountId),
        )

        private fun followed(followed: Account, links: Links) = Entry(
            "follow:${followed.id}",
            followed.displayName.ifBlank { followed.username },
            "@${followed.acct}",
            links.open(profileId = followed.id),
        )

        /** The bookmarks anyone may see, a boost as the post it boosted, each once. */
        private fun kept(bookmarks: List<Status>, links: Links) = bookmarks.map(Status::displayed)
            .filter(::indexable)
            .distinctBy(Status::id)
            .map { Entry("bookmark:${it.id}", text(it), "@${it.account.acct}", links.open(statusId = it.id)) }

        private fun searched(query: String, links: Links) =
            Entry("search:${query.lowercase()}", query, alternate = null, links.search(query))

        /** A post anyone may see; one only followers or the people it names may read never leaves the app. */
        fun indexable(status: Status): Boolean =
            status.visibility == Visibility.Public || status.visibility == Visibility.Unlisted

        /** What a post is found by: its content warning in place of what it hides, else its words. */
        private fun text(status: Status): String {
            val words = status.spoilerText.ifBlank { StatusHtmlParser.plainText(status.content) }.trim()
            return words.ifBlank { status.account.displayName.ifBlank { status.account.username } }.take(MAX_TEXT)
        }

        // what an entry opens is still experimental in AppSearch 1.1.0, which the catalog pins; an entry the
        // phone's search shows but cannot open would be worse than none
        @OptIn(ExperimentalAppSearchApi::class)
        private fun Entry.thing(namespace: String): Thing = Thing.Builder(namespace, id)
            .setName(name)
            .apply { alternate?.let { setAlternateNames(listOf(it)) } }
            .setPotentialActions(listOf(PotentialAction.Builder().setName(VIEW).setUri(opens).build()))
            .build()
    }
}

/** What an account's entries are made of: whom it follows, what it bookmarked, what it searched for. */
internal data class Gathered(val following: List<Account>, val bookmarks: List<Status>, val recent: List<String>)

/** One entry: how it is told apart, what it is called and also found by, and the intent it opens, written out. */
private class Entry(val id: String, val name: String, val alternate: String?, val opens: String)

/** The intents an entry opens, as the system keeps them, each as [accountId]. */
private class Links(private val context: Context, private val accountId: String) {
    fun open(statusId: String? = null, profileId: String? = null): String =
        AppIntents.open(context, accountId, statusId, profileId).toUri(Intent.URI_INTENT_SCHEME)

    fun search(query: String): String = AppIntents.search(context, accountId, query).toUri(Intent.URI_INTENT_SCHEME)
}
