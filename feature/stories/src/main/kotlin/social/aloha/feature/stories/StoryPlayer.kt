// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Story

/** Whether the reader holds the story still: pressing on it, or paused through a screen reader. */
@Stable
private class Hold {
    var pressed by mutableStateOf(false)
    var stilled by mutableStateOf(false)
    val held: Boolean get() = pressed || stilled
}

/** Where the player is: which reel, which of its stories; kept when the phone turns. */
private class Position(reel: Int, story: Int = 0) {
    var reel by mutableIntStateOf(reel)
    var story by mutableIntStateOf(story)

    companion object {
        val Saver = listSaver<Position, Int>(save = { listOf(it.reel, it.story) }, restore = { Position(it[0], it[1]) })
    }
}

/**
 * The stories of [reels] from [start] on, one at a time, full screen: a tap on the right goes on and on
 * the left goes back, holding pauses, a swipe down closes, and bars across the top count them out. Each
 * is marked seen as it shows. The reader's own say how many watched, and who, and can be ended early;
 * anybody else's take an emoji or a reply, which only the poster sees. Every gesture is also an action
 * for a screen reader.
 */
@Composable
internal fun StoryPlayer(
    reels: List<StoryReel>,
    start: Int,
    actions: StoryActions,
    onProfile: (accountId: String) -> Unit,
    onClose: () -> Unit,
) {
    val at = rememberSaveable(saver = Position.Saver) { Position(start) }
    // a story the reader deleted goes at once, so the one after takes its place rather than being skipped
    var deleted by remember { mutableStateOf(emptySet<String>()) }
    val shown = remember(reels, deleted) {
        reels.map { reel -> reel.copy(stories = reel.stories.filterNot { it.id in deleted }) }
            .filter { it.stories.isNotEmpty() }
    }
    val navigate = remember(shown) { Navigate(shown, at, onClose) }
    val reel = shown.getOrNull(at.reel)
    val story = reel?.stories?.getOrNull(at.story)
    if (reel == null || story == null) {
        // past the end, or the last of a reel deleted: the next reel, or closing
        LaunchedEffect(shown, at.reel, at.story) { navigate.settle() }
        return
    }
    val hold = remember { Hold() }
    var busy by rememberSaveable { mutableStateOf(false) }
    val paused = hold.held || busy
    val progress = remember(story.id) { Animatable(0f) }
    LaunchedEffect(story.id) { if (!reel.own) actions.onSeen(story) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StoryMedia(story, paused, progress, navigate, Modifier.storyGestures(navigate, story, reel, hold))
        // the bars, the name and close read on a bright picture as on a dark one
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM), Color.Transparent)))
                .systemBarsPadding().padding(AlohaSpacing.xs),
        ) {
            Segments(reel.stories.size, at.story) { progress.value }
            Header(reel, onProfile, onClose)
        }
        Footer(reel, story, actions, onBusy = { busy = it }, onGone = { deleted = deleted + story.id })
    }
}

/** Forward and back through the stories, closing past the last. */
private class Navigate(private val reels: List<StoryReel>, private val at: Position, private val onClose: () -> Unit) {
    fun next() {
        when {
            at.story + 1 < reels[at.reel].stories.size -> at.story++

            at.reel + 1 < reels.size -> {
                at.reel++
                at.story = 0
            }

            else -> onClose()
        }
    }

    fun previous() {
        when {
            at.story > 0 -> at.story--

            at.reel > 0 -> {
                at.reel--
                at.story = 0
            }

            else -> Unit
        }
    }

    fun close() = onClose()

    /** Where the position points past its reel, the next reel's first story; past the last reel, closed. */
    fun settle() {
        if (at.reel < reels.size) {
            at.reel++
            at.story = 0
        }
        if (at.reel >= reels.size) onClose()
    }
}

/** Taps to either side, holding to pause and a swipe down to close; and the same as actions to read out. */
@Composable
private fun Modifier.storyGestures(navigate: Navigate, story: Story, reel: StoryReel, hold: Hold): Modifier {
    val dismiss = with(LocalDensity.current) { DISMISS.toPx() }
    val next = stringResource(R.string.stories_next)
    val previous = stringResource(R.string.stories_previous)
    // holding a story still has no gesture a screen reader can make; this is its way
    val still = stringResource(if (hold.stilled) R.string.stories_resume else R.string.stories_pause)
    val label = storyLabel(story, reel)
    val current by rememberUpdatedState(navigate)
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    hold.pressed = true
                    tryAwaitRelease()
                    hold.pressed = false
                },
                onTap = { if (it.x < size.width / BACK_SHARE) current.previous() else current.next() },
            )
        }
        .pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = { if (dragged > dismiss) current.close() },
            ) { _, amount -> dragged += amount }
        }
        .semantics {
            contentDescription = label
            customActions = listOf(
                CustomAccessibilityAction(next) { current.next().let { true } },
                CustomAccessibilityAction(previous) { current.previous().let { true } },
                CustomAccessibilityAction(still) { true.also { hold.stilled = !hold.stilled } },
            )
        }
}

/** What a screen reader says of a story: whose it is, and what it says where it says something. */
@Composable
private fun storyLabel(story: Story, reel: StoryReel): String = story.caption?.takeIf { it.isNotBlank() }
    ?.let { stringResource(R.string.stories_story_caption, reel.account.bestDisplayName, it) }
    ?: stringResource(R.string.stories_story, reel.account.bestDisplayName)

/** The picture for as long as it asks, or the clip to its end, [progress] counting either out. */
@Composable
private fun StoryMedia(
    story: Story,
    paused: Boolean,
    progress: Animatable<Float, *>,
    navigate: Navigate,
    modifier: Modifier,
) {
    val next by rememberUpdatedState(navigate)
    val clip = story.type == AttachmentKind.Video || story.type == AttachmentKind.Gifv
    LaunchedEffect(story.id, paused) {
        if (paused || clip) return@LaunchedEffect
        val millis = (story.duration * MILLIS).toInt()
        progress.animateTo(1f, tween(((1f - progress.value) * millis).toInt(), easing = LinearEasing))
        next.next()
    }
    Box(modifier.fillMaxSize()) {
        if (clip) {
            StoryClip(story, paused, onProgress = { progress.snapTo(it) }, onDone = navigate::next)
        } else {
            AsyncImage(
                model = story.url ?: story.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A bar per story of the reel: those before full, the one showing filling, those after empty. */
@Composable
private fun Segments(count: Int, index: Int, progress: () -> Float) {
    // read out once as where the reader is, rather than as a bar per story
    val where = stringResource(R.string.stories_position, index + 1, count)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = where },
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
    ) {
        repeat(count) { i ->
            // the bar showing reads the progress as it draws, so a frame of it recomposes nothing
            val value: () -> Float = when {
                i < index -> FULL
                i > index -> EMPTY
                else -> progress
            }
            LinearProgressIndicator(
                progress = value,
                color = Color.White,
                trackColor = Color.White.copy(alpha = TRACK),
                drawStopIndicator = {},
                modifier = Modifier.weight(1f).height(SEGMENT),
            )
        }
    }
}

@Composable
private fun Header(reel: StoryReel, onProfile: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = AlohaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onProfile(reel.account.id) }, modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = reel.account.avatar,
                contentDescription = null,
                modifier = Modifier.size(AVATAR).clip(CircleShape).background(Color.White.copy(alpha = TRACK)),
            )
            Text(
                reel.account.bestDisplayName,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AlohaSpacing.xs).weight(1f),
            )
        }
        IconButton(onClick = onClose) {
            Icon(AlohaIcons.Close, stringResource(R.string.stories_close), tint = Color.White)
        }
    }
}

private val DISMISS = 120.dp
private val SEGMENT = 2.dp
private val AVATAR = 32.dp
private const val TRACK = 0.4f
private const val SCRIM = 0.5f
private val FULL = { 1f }
private val EMPTY = { 0f }
private const val MILLIS = 1_000
private const val BACK_SHARE = 3
