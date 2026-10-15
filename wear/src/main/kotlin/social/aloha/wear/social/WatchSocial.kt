// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.social

import androidx.compose.runtime.Immutable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Notification
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Credentials
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.di.IoDispatcher
import social.aloha.core.network.endpoints.ComposeEndpoints
import social.aloha.core.network.endpoints.NotificationEndpoints
import social.aloha.core.network.endpoints.StatusAction
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusPost
import social.aloha.core.network.endpoints.TimelineEndpoints
import social.aloha.core.network.valueOrNull
import social.aloha.wear.auth.WatchAccounts

/** A post as the watch shows it: who wrote it, its text without markup, and the reader's own marks on it. */
@Immutable
data class WatchPost(
    val id: String,
    val author: String,
    val authorHandle: String,
    val boostedBy: String?,
    val text: String,
    val warning: String,
    val visibility: Visibility,
    val favourited: Boolean,
    val boosted: Boolean,
    val bookmarked: Boolean,
)

/** One notification: who did what, and the post it is about where there is one. */
@Immutable
data class WatchNotice(val id: String, val kind: NotificationKind, val who: String, val post: WatchPost?)

/**
 * The server as the watch reaches it, with the account it is signed in to. A token the server refuses has
 * been revoked, by the person or by the phone signing out, so the watch forgets the account and signs out.
 */
@Singleton
class WatchSocial @Inject constructor(
    private val accounts: WatchAccounts,
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun home(): List<WatchPost>? =
        call(TimelineEndpoints.timeline(TimelineSource.Home, limit = ROWS))?.map(::post)

    suspend fun notifications(): List<WatchNotice>? =
        call(NotificationEndpoints.flat(limit = ROWS))?.map { it.notice() }

    suspend fun post(id: String): WatchPost? = call(StatusEndpoints.status(id))?.let(::post)

    /** Favourites, boosts or bookmarks [post], or takes that back where it is set; the post as it is now. */
    suspend fun toggle(post: WatchPost, mark: Mark): WatchPost? {
        val action = when (mark) {
            Mark.Favourite -> if (post.favourited) StatusAction.Unfavourite else StatusAction.Favourite
            Mark.Boost -> if (post.boosted) StatusAction.Unreblog else StatusAction.Reblog
            Mark.Bookmark -> if (post.bookmarked) StatusAction.Unbookmark else StatusAction.Bookmark
        }
        return call(StatusEndpoints.action(post.id, action))?.let(::post)
    }

    /**
     * Answers [post] with [text], to its author and as visible as the post itself; true once sent. The reader's
     * own post is answered without a mention of themselves.
     */
    suspend fun reply(post: WatchPost, text: String): Boolean {
        val own = post.authorHandle == accounts.current()?.handle
        val body = if (own) text else "@${post.authorHandle} $text"
        return call(
            ComposeEndpoints.post(StatusPost(text = body, visibility = post.visibility, inReplyToId = post.id)),
        ) != null
    }

    private suspend fun <T> call(request: ApiRequest<T>): T? {
        val account = accounts.current() ?: return null
        val base = account.apiBase.toHttpUrlOrNull() ?: return null
        val credentials = Credentials(account.token)
        // a refused token was revoked; forgetting it leaves an account signed in since then alone
        val forget: suspend () -> Unit = { accounts.forget(account.token) }
        val client = ApiClient(base, { credentials }, http, rateLimiter, io, onUnauthorised = forget)
        return client.execute(request).valueOrNull()
    }

    private fun post(status: Status): WatchPost {
        val shown = status.displayed
        return WatchPost(
            id = shown.id,
            author = shown.account.bestDisplayName,
            authorHandle = shown.account.acct,
            boostedBy = status.reblog?.let { status.account.bestDisplayName },
            text = StatusHtmlParser.plainText(shown.content),
            warning = shown.spoilerText,
            visibility = shown.visibility,
            favourited = shown.favourited,
            boosted = shown.reblogged,
            bookmarked = shown.bookmarked,
        )
    }

    private fun Notification.notice() = WatchNotice(id, type, account.bestDisplayName, status?.let(::post))

    enum class Mark { Favourite, Boost, Bookmark }

    private companion object {
        /** A watch shows a screenful or two; the phone has the rest. */
        const val ROWS = 20
    }
}
