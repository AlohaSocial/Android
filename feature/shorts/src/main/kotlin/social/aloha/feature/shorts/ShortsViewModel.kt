// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Trouble
import social.aloha.core.data.timeline.ModeFeed
import social.aloha.core.data.timeline.ModeFeeds
import social.aloha.core.data.timeline.ModeSources
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.media.ShortSource
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.FeedMode
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.VideoSources
import social.aloha.core.sync.DeviceConditions
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.SeenBy
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.heldBy
import social.aloha.core.ui.namingFrom

/**
 * One short: its post as a row (caption, author, counts), where it plays from and its still; where every
 * account shows together, the accounts that see it.
 */
@Immutable
internal data class ShortUi(
    val row: StatusRowUi,
    val source: ShortSource,
    val clip: MediaAttachment,
    val seenBy: List<SeenBy> = emptyList(),
)

@Immutable
internal data class ShortsUiState(
    val shorts: List<ShortUi> = emptyList(),
    val source: TimelineSource = TimelineSource.Home,
    /** Where shorts can come from on this server: the people followed, this server, everyone. */
    val sources: List<TimelineSource> = listOf(TimelineSource.Home),
    val muted: Boolean = true,
    val loop: Boolean = true,
    /** Whether a short waits for a tap as it comes into view: on mobile data, where the reader asked. */
    val waitForTap: Boolean = false,
    val loadedOnce: Boolean = false,
    val trouble: Trouble? = null,
    val actionFailed: Boolean = false,
)

/**
 * Shorts: the Shorts timeline of the source chosen for the mode, one short per page, newest first and
 * older ones below; with [together], every account's merged, each short acting as the account that holds
 * it. Sound stays off until the person turns it on, and that choice holds across sessions and modes.
 */
@HiltViewModel(assistedFactory = ShortsViewModel.Factory::class)
internal class ShortsViewModel @AssistedInject constructor(
    @Assisted together: Boolean,
    feeds: ModeFeeds,
    private val modes: ModeSources,
    private val preferences: ModePreferences,
    private val interactions: StatusInteractions,
    private val cache: RichTextCache,
    private val conditions: DeviceConditions,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(together: Boolean): ShortsViewModel
    }

    private val feed = feeds.of(viewModelScope, FeedMode.Shorts, together)
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val failed = MutableStateFlow(false)

    /** Each short shown, by the id it shows under, as the account it acts as holds it. */
    @Volatile private var owners: Map<String, ModeFeed.Post> = emptyMap()

    val uiState: StateFlow<ShortsUiState> = combine(feed.pages, colors.filterNotNull()) { page, colors ->
        val mapper = StatusRowMapper(cache, colors)
        val shorts = page.posts.mapNotNull { post -> short(mapper, post)?.let { it to post } }
        owners = shorts.associate { (short, post) -> short.row.statusId to post }
        ShortsUiState(
            shorts = shorts.map { it.first },
            source = page.source,
            sources = page.sources,
            loadedOnce = page.loadedOnce,
            trouble = page.trouble,
        )
    }
        .combine(preferences.videosMuted) { state, muted -> state.copy(muted = muted) }
        .combine(preferences.loopShorts) { state, loop -> state.copy(loop = loop) }
        // ponytail: the network as the choice is read; a move to mobile data counts from the next change
        .combine(preferences.autoplayOnMobileData) { state, autoplay ->
            state.copy(waitForTap = !autoplay && conditions.metered)
        }
        .combine(failed) { state, failed -> state.copy(actionFailed = failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ShortsUiState())

    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    /** The pager neared the last short it holds: the next page below. */
    fun onNearEnd() = feed.older()

    fun onSource(source: TimelineSource) {
        viewModelScope.launch { modes.choose(FeedMode.Shorts, source) }
    }

    fun onMuted(muted: Boolean) {
        viewModelScope.launch { preferences.setVideosMuted(muted) }
    }

    /** [toggle] on the short shown as [statusId], as the account that holds it, on its own id there. */
    fun onToggle(statusId: String, toggle: Toggle) {
        val post = owners[statusId] ?: return
        viewModelScope.launch {
            if (interactions.toggle(post.account, post.status.displayed, toggle) != null) failed.value = true
        }
    }

    fun onActionFailureShown() = failed.update { false }

    /**
     * [post] as a short, with its owner's ids, or, held by another account than the one in use, under that
     * account's server's name; none for a post without a video.
     */
    private fun short(mapper: StatusRowMapper, post: ModeFeed.Post): ShortUi? {
        val (account, status) = post
        val clip = status.displayed.mediaAttachments
            .firstOrNull { it.type == AttachmentKind.Video || it.type == AttachmentKind.Gifv } ?: return null
        val row = if (post.inUse) {
            mapper.map(status, account.serverAccountId)
        } else {
            mapper.map(status.namingFrom(account.host), account.serverAccountId).heldBy(account.host)
        }
        val sources = VideoSources.ladder(status, clip, account.capabilities.apiBase)
        val seenBy = post.seenBy.map { SeenBy(it.reader.qualifiedHandle, it.reader.avatarUrl, it.colour) }
        return ShortUi(row, ShortSource(row.statusId, sources), clip, seenBy)
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
