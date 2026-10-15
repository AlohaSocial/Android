// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.social

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import social.aloha.core.model.NotificationKind
import social.aloha.wear.Centred
import social.aloha.wear.Heading
import social.aloha.wear.R
import social.aloha.wear.WideButton

private const val REPLY_KEY = "reply"
private const val CARD_LINES = 3

/** Where a screen leads: a post, the notifications, the account. */
class SocialLinks(val onPost: (String) -> Unit, val onNotifications: () -> Unit, val onAccount: () -> Unit)

@Composable
fun HomeRoute(links: SocialLinks, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, links, viewModel::reload)
}

@Composable
internal fun HomeScreen(state: Rows<WatchPost>, links: SocialLinks, onRetry: () -> Unit) = Screen { spec ->
    item { Heading(spec, stringResource(R.string.wear_home)) }
    item { WideButton(spec, stringResource(R.string.wear_notifications), links.onNotifications) }
    rows(state, spec, onRetry) { post -> PostCard(spec, post) { links.onPost(post.id) } }
    item { WideButton(spec, stringResource(R.string.wear_account), links.onAccount) }
}

@Composable
fun NotificationsRoute(onPost: (String) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(state, onPost, viewModel::reload)
}

@Composable
internal fun NotificationsScreen(state: Rows<WatchNotice>, onPost: (String) -> Unit, onRetry: () -> Unit) =
    Screen { spec ->
        item { Heading(spec, stringResource(R.string.wear_notifications)) }
        rows(state, spec, onRetry) { notice ->
            TitleCard(
                onClick = { notice.post?.let { onPost(it.id) } },
                enabled = notice.post != null,
                title = { Text(stringResource(notice.kind.verb(), notice.who), maxLines = 2) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
            ) {
                notice.post?.let { Text(it.shownText(), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }

@Composable
fun PostRoute(id: String) {
    val viewModel = hiltViewModel<PostViewModel, PostViewModel.Factory>(key = id) { it.create(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val answer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.let(RemoteInput::getResultsFromIntent)?.getCharSequence(REPLY_KEY)
            ?.let { viewModel.onReply(it.toString()) }
    }
    val label = stringResource(R.string.wear_reply)
    val quick = stringArrayResource(R.array.wear_quick_replies)
    PostScreen(
        state,
        onToggle = viewModel::onToggle,
        onReply = {
            // the system's own answer screen: these ready answers, the voice and the keyboard
            val input = RemoteInput.Builder(REPLY_KEY).setLabel(label).setChoices(quick).build()
            answer.launch(
                RemoteInputIntentHelper.createActionRemoteInputIntent().also {
                    RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(input))
                },
            )
        },
    )
}

@Composable
internal fun PostScreen(state: PostUiState, onToggle: (WatchSocial.Mark) -> Unit, onReply: () -> Unit) {
    val post = state.post
    // a content warning hides the text until the reader chooses to see past it
    var revealed by rememberSaveable(post?.id) { mutableStateOf(post?.warning.isNullOrBlank()) }
    Screen { spec ->
        when {
            state.loading -> item { CircularProgressIndicator() }

            post == null -> item { Centred(stringResource(R.string.wear_load_failed)) }

            else -> {
                item { Heading(spec, post.author) }
                post.boostedBy?.let { item { Centred(stringResource(R.string.wear_boosted_by, it)) } }
                if (revealed) {
                    item { Centred(post.text) }
                } else {
                    item { Centred(stringResource(R.string.wear_content_warning, post.warning)) }
                    item { WideButton(spec, stringResource(R.string.wear_show_post)) { revealed = true } }
                }
                item { Mark(spec, R.string.wear_favourite, post.favourited) { onToggle(WatchSocial.Mark.Favourite) } }
                item { Mark(spec, R.string.wear_boost, post.boosted) { onToggle(WatchSocial.Mark.Boost) } }
                item { Mark(spec, R.string.wear_bookmark, post.bookmarked) { onToggle(WatchSocial.Mark.Bookmark) } }
                item { WideButton(spec, stringResource(R.string.wear_reply), onReply) }
                if (state.sent) item { Centred(stringResource(R.string.wear_reply_sent)) }
                if (state.failed) item { Centred(stringResource(R.string.wear_action_failed)) }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.Mark(
    spec: TransformationSpec,
    label: Int,
    on: Boolean,
    onToggle: () -> Unit,
) {
    SwitchButton(
        checked = on,
        onCheckedChange = { onToggle() },
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    )
}

@Composable
private fun TransformingLazyColumnItemScope.PostCard(spec: TransformationSpec, post: WatchPost, onClick: () -> Unit) {
    TitleCard(
        onClick = onClick,
        title = { Text(post.author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(post.shownText(), maxLines = CARD_LINES, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WatchPost.shownText(): String =
    if (warning.isBlank()) text else stringResource(R.string.wear_content_warning, warning)

/** One scrolling list on the round screen, the way every screen of the watch app is laid out. */
@Composable
private fun Screen(content: TransformingLazyColumnScope.(TransformationSpec) -> Unit) {
    val list = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = list) { padding ->
        TransformingLazyColumn(state = list, contentPadding = padding) { content(spec) }
    }
}

private fun <T> TransformingLazyColumnScope.rows(
    state: Rows<T>,
    spec: TransformationSpec,
    onRetry: () -> Unit,
    row: @Composable TransformingLazyColumnItemScope.(T) -> Unit,
) {
    when (state) {
        Rows.Loading -> item { CircularProgressIndicator() }

        Rows.Failed -> {
            item { Centred(stringResource(R.string.wear_load_failed)) }
            item { WideButton(spec, stringResource(R.string.wear_retry), onRetry) }
        }

        is Rows.Loaded -> if (state.items.isEmpty()) {
            item { Centred(stringResource(R.string.wear_nothing_yet)) }
        } else {
            items(state.items) { row(it) }
        }
    }
}

private fun NotificationKind.verb(): Int = when (this) {
    NotificationKind.Favourite -> R.string.wear_notice_favourite
    NotificationKind.Reblog -> R.string.wear_notice_boost
    NotificationKind.Mention -> R.string.wear_notice_mention
    NotificationKind.Follow -> R.string.wear_notice_follow
    NotificationKind.FollowRequest -> R.string.wear_notice_follow_request
    NotificationKind.Status -> R.string.wear_notice_status
    NotificationKind.Update -> R.string.wear_notice_update
    NotificationKind.Poll -> R.string.wear_notice_poll
    else -> R.string.wear_notice_other
}
