// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.runtime.Immutable
import java.time.Instant
import social.aloha.core.data.Trouble
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.StatusRowUi

/** What a timeline screen reads: home, from the source chosen for it, or one hashtag's public posts. */
public sealed interface TimelineFeed {
    public data object Home : TimelineFeed

    public data class Tag(val name: String) : TimelineFeed
}

/**
 * Where the home screen's posts can come from: the people followed, this server, or everyone it knows;
 * the last two only where the server serves them (mastodon.social, for one, disables both).
 */
internal fun homeSources(capabilities: ServerCapabilities): List<TimelineSource> = buildList {
    add(TimelineSource.Home)
    if (capabilities.localFeed) add(TimelineSource.Local)
    if (capabilities.federatedFeed) add(TimelineSource.Federated)
}

@Immutable
internal sealed interface TimelineItem {
    val key: String

    data class Post(val row: StatusRowUi) : TimelineItem {
        override val key: String get() = row.rowId
    }

    data class Gap(val id: String, val loading: Boolean) : TimelineItem {
        override val key: String get() = id
    }
}

/**
 * What the home screen shows. [pending] posts arrived while the person was reading and wait behind the
 * pill, so the list never moves under them. [restoreTo] and [scrollToTop] are requests the screen
 * carries out once and reports back.
 */
@Immutable
internal data class TimelineUiState(
    val source: TimelineSource = TimelineSource.Home,
    val sources: List<TimelineSource> = listOf(TimelineSource.Home),
    val items: List<TimelineItem> = emptyList(),
    val loadedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val pending: Int = 0,
    val trouble: Trouble? = null,
    val loadingOlder: Boolean = false,
    val reachedEnd: Boolean = false,
    val showBoosts: Boolean = true,
    val showReplies: Boolean = true,
    val now: Instant = Instant.EPOCH,
    val restoreTo: Restore? = null,
    val scrollToTop: Boolean = false,
    val actionFailed: Boolean = false,
    val swipeTowardsEnd: SwipeAction = SwipeAction.Favourite,
    val swipeTowardsStart: SwipeAction = SwipeAction.Boost,
) {
    @Immutable
    data class Restore(val index: Int, val offset: Int)
}

/** What the screen can ask for beyond a row's own actions. */
internal interface TimelineScreenActions {
    fun onRefresh()
    fun onRevealPending()
    fun onScrolledToTop()
    fun onRestored()
    fun onSource(source: TimelineSource)
    fun onShowBoosts(show: Boolean)
    fun onShowReplies(show: Boolean)
    fun onScrolled(rowId: String, offset: Int)
    fun onNearEnd()
    fun onFillGap(gapId: String)

    /** A swipe that changes the post; a reply opens the post instead, which the screen does. */
    fun onSwipe(row: StatusRowUi, action: SwipeAction)
}
