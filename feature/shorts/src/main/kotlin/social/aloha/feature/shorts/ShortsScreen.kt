// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.rememberBlurHashPainter
import social.aloha.core.ui.rememberReducedMotion

/**
 * One short, edge to edge. A double tap favourites it, with a heart that skips its burst where motion
 * is reduced; a long press pauses it and shows where it is; a swipe in from the right edge opens who
 * posted it. Each of those is also on the rail or in its menu, and among the screen reader's actions,
 * for whoever cannot make the gesture. A sensitive short waits behind its blur for a tap.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun ShortPage(
    short: ShortUi,
    player: Player?,
    muted: Boolean,
    actions: ShortsActions,
    modifier: Modifier = Modifier,
) {
    // only "show all" plays a sensitive short at once; a short has no way to be left out of the pager
    val covered = short.row.sensitive && !LocalSensitiveMediaPolicy.current.allowsAutomaticReveal
    val page = remember(short.row.statusId) { PageState(covered) }
    // the page alone says whether it plays: in front, asked for if sensitive, and not paused by the reader
    val shown by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val front = shown.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(player, page.revealed, page.paused, front) {
        player ?: return@LaunchedEffect
        if (front && page.revealed && !page.paused) player.play() else player.pause()
    }
    Box(
        modifier.fillMaxSize().background(
            Color.Black,
        ).gestures(short, page, actions).readAloud(spoken(short, page), short, page, actions),
    ) {
        Still(short, page.revealed)
        if (page.revealed && player != null) Video(player)
        if (!page.revealed) {
            Text(
                stringResource(R.string.shorts_sensitive),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Heart(page.hearts, rememberReducedMotion(), Modifier.align(Alignment.Center))
        Overlay(short, page, player, muted, actions)
    }
}

/** What one page remembers: whether its sensitive short was asked for, whether it is paused, its hearts. */
@Stable
private class PageState(sensitive: Boolean) {
    var revealed by mutableStateOf(!sensitive)
    var paused by mutableStateOf(false)
    var hearts by mutableIntStateOf(0)
}

/** The gestures: a tap reveals a sensitive short, a double tap favourites, a long press pauses, the edge opens who. */
private fun Modifier.gestures(short: ShortUi, page: PageState, actions: ShortsActions): Modifier = this
    .pointerInput(short.row.statusId) {
        detectTapGestures(
            onTap = { page.revealed = true },
            onDoubleTap = {
                if (!short.row.state.favourited) actions.onFavourite(short)
                page.hearts++
            },
            onLongPress = { page.paused = true },
        )
    }
    .pointerInput(short.row.statusId) {
        // a swipe in from the right edge, past a fifth of the width, opens who posted it
        var fromEdge = false
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { start ->
                fromEdge = start.x > size.width - EDGE.toPx()
                travelled = 0f
            },
            onHorizontalDrag = { _, amount -> travelled += amount },
            onDragEnd = { if (fromEdge && -travelled > size.width / EDGE_SHARE) actions.onProfile(short) },
        )
    }

/** What a screen reader says of a short, and calls its actions. */
private class Spoken(val label: String, val favourite: String, val pause: String, val profile: String, val show: String)

@Composable
private fun spoken(short: ShortUi, page: PageState): Spoken = Spoken(
    label = stringResource(R.string.shorts_page, short.row.author.plainName, short.row.plainText),
    favourite = stringResource(
        if (short.row.state.favourited) R.string.shorts_unfavourite else R.string.shorts_favourite,
    ),
    pause = stringResource(if (page.paused) R.string.shorts_play else R.string.shorts_pause),
    profile = stringResource(R.string.shorts_profile, short.row.author.plainName),
    show = stringResource(R.string.shorts_show),
)

/** The short as a screen reader hears it, with each gesture's action among its own. */
private fun Modifier.readAloud(spoken: Spoken, short: ShortUi, page: PageState, actions: ShortsActions): Modifier =
    semantics {
        contentDescription = spoken.label
        customActions = listOfNotNull(
            // a sensitive short is asked for with a tap, which a screen reader asks for here
            CustomAccessibilityAction(spoken.show) { true.also { page.revealed = true } }.takeUnless { page.revealed },
            CustomAccessibilityAction(spoken.favourite) { actions.onFavourite(short).let { true } },
            CustomAccessibilityAction(spoken.pause) {
                page.paused = !page.paused
                true
            },
            CustomAccessibilityAction(spoken.profile) { actions.onProfile(short).let { true } },
        )
    }

/** What lies over the video: the caption and the rail on a scrim, the scrubber when paused, the sound. */
@Composable
private fun BoxScope.Overlay(short: ShortUi, page: PageState, player: Player?, muted: Boolean, actions: ShortsActions) {
    // the caption and rail read on a bright frame as on a dark one
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(SCRIM_HEIGHT)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM)))),
    )
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        if (page.paused && player != null) Scrubber(player) { page.paused = false }
        Row(verticalAlignment = Alignment.Bottom) {
            Caption(short, Modifier.weight(1f))
            Rail(short, page.paused, onPause = { page.paused = !page.paused }, actions)
        }
    }
    IconButton(
        onClick = { actions.onMuted(!muted) },
        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(AlohaSpacing.s),
    ) {
        Icon(
            if (muted) AlohaIcons.Muted else AlohaIcons.Unmuted,
            stringResource(if (muted) R.string.shorts_unmute else R.string.shorts_mute),
            tint = Color.White,
        )
    }
}

/** The short's still: its poster frame, or its blur where it is sensitive and not yet asked for. */
@Composable
private fun Still(short: ShortUi, revealed: Boolean) {
    val blur = rememberBlurHashPainter(short.clip.blurhash)
    if (revealed) {
        AsyncImage(
            model = short.clip.previewUrl,
            contentDescription = null,
            placeholder = blur,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        blur?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Video(player: Player) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
                keepScreenOn = true
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/** A heart over the short on each double tap; without motion it simply shows, then goes. */
@Composable
private fun Heart(taps: Int, reduced: Boolean, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        shown = true
        delay(HEART_MILLIS)
        shown = false
    }
    AnimatedVisibility(
        shown,
        modifier,
        enter = if (reduced) androidx.compose.animation.EnterTransition.None else scaleIn(),
        exit = if (reduced) androidx.compose.animation.ExitTransition.None else fadeOut(),
    ) {
        Icon(AlohaIcons.Favourited, contentDescription = null, tint = Color.White, modifier = Modifier.size(HEART))
    }
}

/** Where the paused short is, to move to; the play button goes on from there. */
@Composable
private fun Scrubber(player: Player, onPlay: () -> Unit) {
    var position by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val length = player.duration.takeIf { it > 0 } ?: 1L
            position = player.currentPosition.toFloat() / length
            delay(SCRUB_TICK)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlay) { Icon(AlohaIcons.Play, stringResource(R.string.shorts_play), tint = Color.White) }
        val where = stringResource(R.string.shorts_position)
        Slider(
            value = position,
            onValueChange = {
                position = it
                player.seekTo((it * player.duration.coerceAtLeast(0)).toLong())
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = where },
        )
    }
}

@Composable
private fun Caption(short: ShortUi, modifier: Modifier) {
    var open by remember(short.row.statusId) { mutableStateOf(false) }
    Column(modifier.padding(end = AlohaSpacing.s), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Text(short.row.author.handle, color = Color.White, style = MaterialTheme.typography.titleSmall)
        if (short.row.plainText.isNotBlank()) {
            // two lines, the rest a tap away; its hashtags and mentions are links
            Text(
                short.row.body,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (open) Int.MAX_VALUE else CAPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { open = !open },
            )
        }
    }
}

/** The rail on the trailing edge: who posted it, favourite, boost, the comments, share, and the menu. */
@Composable
private fun Rail(short: ShortUi, paused: Boolean, onPause: () -> Unit, actions: ShortsActions) {
    val row = short.row
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        IconButton(onClick = { actions.onProfile(short) }) {
            AsyncImage(
                model = row.author.avatarUrl,
                contentDescription = stringResource(R.string.shorts_profile, row.author.plainName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(AVATAR).clip(CircleShape).background(Color.DarkGray),
            )
        }
        RailButton(
            if (row.state.favourited) AlohaIcons.Favourited else AlohaIcons.Favourite,
            stringResource(if (row.state.favourited) R.string.shorts_unfavourite else R.string.shorts_favourite),
            row.counts.favourites,
        ) { actions.onFavourite(short) }
        RailButton(
            if (row.state.boosted) AlohaIcons.Boosted else AlohaIcons.Boost,
            stringResource(if (row.state.boosted) R.string.shorts_unboost else R.string.shorts_boost),
            row.counts.boosts,
        ) { actions.onBoost(short) }
        RailButton(
            AlohaIcons.Reply,
            pluralStringResource(R.plurals.shorts_comments, row.counts.replies, row.counts.replies),
            row.counts.replies,
        ) { actions.onComments(short) }
        RailButton(AlohaIcons.Share, stringResource(R.string.shorts_share), count = null) { actions.onShare(short) }
        Menu(short, paused, onPause, actions)
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White) }
        count?.takeIf {
            it > 0
        }?.let { Text(it.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

/** What else a short offers, among them every gesture's action for whoever cannot make it. */
@Composable
private fun Menu(short: ShortUi, paused: Boolean, onPause: () -> Unit, actions: ShortsActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            open = true
        }) { Icon(AlohaIcons.More, stringResource(R.string.shorts_more), tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (paused) R.string.shorts_play else R.string.shorts_pause)) },
                onClick = {
                    open = false
                    onPause()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shorts_profile, short.row.author.plainName)) },
                onClick = {
                    open = false
                    actions.onProfile(short)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shorts_open)) },
                onClick = {
                    open = false
                    actions.onComments(short)
                },
            )
            if (!short.row.isOwn) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shorts_report)) },
                    onClick = {
                        open = false
                        actions.onReport(short)
                    },
                )
            }
        }
    }
}

/**
 * Shorts, one per page. Only the page at rest plays, so a quick run through several starts none of them
 * on the way; with motion reduced the pages cross-fade in place instead of sliding.
 */
@Composable
internal fun ShortsPager(
    state: ShortsUiState,
    pagerState: PagerState,
    player: Player,
    actions: ShortsActions,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    VerticalPager(pagerState, modifier.fillMaxSize(), key = {
        state.shorts[it].row.statusId
    }, beyondViewportPageCount = 1) { page ->
        val short = state.shorts[page]
        ShortPage(
            short,
            player.takeIf { page == pagerState.settledPage },
            state.muted,
            actions,
            Modifier.graphicsLayer {
                if (reduced) {
                    val offset = pagerState.getOffsetDistanceInPages(page)
                    translationY = offset * size.height
                    alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                }
            },
        )
    }
}

private val EDGE = 24.dp
private const val EDGE_SHARE = 5
private val HEART = 96.dp
private val AVATAR = 40.dp
private const val HEART_MILLIS = 700L
private const val SCRUB_TICK = 250L
private const val CAPTION_LINES = 2
private const val SCRIM_HEIGHT = 0.4f
private const val SCRIM = 0.6f
