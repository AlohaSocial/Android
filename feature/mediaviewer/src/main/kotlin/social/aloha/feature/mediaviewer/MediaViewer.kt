// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.media.LadderPlayback
import social.aloha.core.media.Sound
import social.aloha.core.media.mediaPlayer
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Status
import social.aloha.core.model.VideoSource
import social.aloha.core.ui.LocalOnMobileData
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.rememberBlurHashPainter
import social.aloha.core.ui.rememberReducedMotion

/**
 * The shared media viewer, edge to edge over a dimmed backdrop, from any mode: [attachments] from
 * [start], swiped between with the place shown. A picture pinches up to five times its size, pans
 * while zoomed and double-taps between fit and closer; dragging it up or down, unzoomed, lets it go,
 * the backdrop fading as it does. Zooming in and out and closing are buttons too, for whoever cannot
 * pinch or drag. An ALT badge, only where the author described it, shows the description. A video
 * plays from the best source its server has, given in [videoSources]; a GIF loops without sound.
 */
@Composable
internal fun MediaViewer(
    attachments: List<MediaAttachment>,
    start: Int,
    videoSources: (MediaAttachment) -> List<VideoSource>,
    actions: MediaViewerActions,
    modifier: Modifier = Modifier,
    status: Status? = null,
) {
    val pager = rememberPagerState(start.coerceIn(0, (attachments.size - 1).coerceAtLeast(0))) { attachments.size }
    var drag by remember { mutableFloatStateOf(0f) }
    val zooms = remember { mutableStateOf(mapOf<Int, Float>()) }
    val title = stringResource(R.string.viewer_title)
    val motion = rememberViewerMotion(actions::onClose) { drag = it }
    BackHandler(onBack = motion::close)
    // read while drawing, so a swipe or a drag repaints the colours without composing the viewer anew
    val backdrop = { backdropWhilePaging(pager, attachments) }
    val bars = { barsOf(backdrop()) }
    Box(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = motion.shown }
            .drawBehind {
                drawRect(backdrop().copy(alpha = (1f - abs(drag) / DISMISS_DISTANCE).coerceIn(MINIMUM_BACKDROP, 1f)))
            }
            .semantics { paneTitle = title },
    ) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            userScrollEnabled = (zooms.value[pager.currentPage] ?: 1f) <= 1f,
        ) { page ->
            val attachment = attachments[page]
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationY = if (page == pager.currentPage) drag else 0f
                    scaleX = OPENED_FROM + (1f - OPENED_FROM) * motion.shown
                    scaleY = scaleX
                },
            ) {
                when (attachment.type) {
                    AttachmentKind.Video, AttachmentKind.Gifv, AttachmentKind.Audio ->
                        Clip(attachment, videoSources(attachment), playing = page == pager.settledPage)

                    else -> Picture(
                        attachment,
                        zoom = zooms.value[page] ?: 1f,
                        onZoom = { zooms.value = zooms.value + (page to it) },
                        onDrag = { drag = it },
                        onDismiss = motion::fling,
                    )
                }
            }
        }
        val current = attachments.getOrNull(pager.currentPage)
        TopBar(current, pager.currentPage, attachments.size, actions.closingWith(motion::close), bars)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().drawBehind {
                drawRect(bars())
            }.navigationBarsPadding(),
        ) {
            BottomBar(current?.description?.takeIf { it.isNotBlank() }, status, actions) {
                if (current != null && current.type == AttachmentKind.Image) {
                    ZoomButtons(
                        zoom = zooms.value[pager.currentPage] ?: 1f,
                        onZoom = { zooms.value = zooms.value + (pager.currentPage to it) },
                    )
                }
            }
        }
    }
}

/** [this], closing through [close], so the close button springs the viewer away as back and a drag do. */
private fun MediaViewerActions.closingWith(close: () -> Unit): MediaViewerActions =
    object : MediaViewerActions by this {
        override fun onClose() = close()
    }

/**
 * A picture: pinch to zoom up to five times, pan while zoomed, double-tap between fit and closer. Drawn
 * at fit, a mostly vertical drag lets it go and a sideways one is left to the pager.
 */
@Composable
private fun Picture(
    attachment: MediaAttachment,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDismiss: (distance: Float, velocity: Float) -> Unit,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    if (zoom <= 1f && pan != Offset.Zero) pan = Offset.Zero
    val zoomNow by rememberUpdatedState(zoom)
    val gestures = remember(onZoom, onDrag, onDismiss) {
        PictureGestures({ zoomNow }, onZoom, onPan = { pan += it }, onDrag, onDismiss)
    }
    val blur = rememberBlurHashPainter(attachment.blurhash)
    // on mobile data, where the reader chose so, the picture comes at the size a list shows it
    val full = LocalReadingStyle.current.fullPicturesOnMobileData || !LocalOnMobileData.current
    AsyncImage(
        model = if (full) attachment.url ?: attachment.previewUrl else attachment.previewUrl ?: attachment.url,
        contentDescription = attachment.description,
        placeholder = blur,
        error = blur,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onZoom(toggledZoom(zoomNow)) }) }
            .zoomAndDismiss(gestures)
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
            },
    )
}

/** Double tap goes from fit to closer, and from any zoom back to fit. */
private fun toggledZoom(zoom: Float): Float = if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM

/**
 * Two fingers, or one on a zoomed picture, zoom and pan it. One finger on an unzoomed picture, moving
 * mostly up or down, drags it away and lets it go past [DISMISS_DISTANCE]; moving sideways, it is left
 * for the pager.
 */
private class PictureGestures(
    val zoom: () -> Float,
    val onZoom: (Float) -> Unit,
    val onPan: (Offset) -> Unit,
    val onDrag: (Float) -> Unit,
    val onDismiss: (distance: Float, velocity: Float) -> Unit,
) {
    private var moved = Offset.Zero
    private var dismissing = false
    val tracker = VelocityTracker()

    fun start() {
        moved = Offset.Zero
        dismissing = false
        tracker.resetTracking()
    }

    fun pinch(event: PointerEvent) {
        onZoom((zoom() * event.calculateZoom()).coerceIn(1f, MAXIMUM_ZOOM))
        onPan(event.calculatePan())
        event.changes.forEach { it.consume() }
    }

    fun slide(event: PointerEvent, slop: Float) {
        moved += event.calculatePan()
        dismissing = dismissing || (abs(moved.y) > slop && abs(moved.y) > abs(moved.x))
        if (!dismissing) return
        onDrag(moved.y)
        event.changes.forEach { it.consume() }
    }

    /** Let go: far enough, or flung hard enough in the drag's direction, and the picture leaves with the fling. */
    fun end() {
        val velocity = tracker.calculateVelocity().y
        val flung = abs(velocity) > FLING_VELOCITY && velocity * moved.y > 0
        if (dismissing && (abs(moved.y) > DISMISS_DISTANCE || flung)) onDismiss(moved.y, velocity) else onDrag(0f)
    }
}

private fun Modifier.zoomAndDismiss(gestures: PictureGestures): Modifier = pointerInput(gestures) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        gestures.start()
        do {
            val event = awaitPointerEvent()
            event.changes.firstOrNull()?.let { gestures.tracker.addPosition(it.uptimeMillis, it.position) }
            if (event.changes.size > 1 ||
                gestures.zoom() > 1f
            ) {
                gestures.pinch(event)
            } else {
                gestures.slide(event, viewConfiguration.touchSlop)
            }
        } while (event.changes.any { it.pressed })
        gestures.end()
    }
}

/** A video plays with its controls; a GIF loops without sound and without controls, as GIFs do. */
@OptIn(UnstableApi::class)
@Composable
private fun Clip(attachment: MediaAttachment, sources: List<VideoSource>, playing: Boolean) {
    val context = LocalContext.current
    val gif = attachment.type == AttachmentKind.Gifv
    val player = remember { mediaPlayer(context, if (gif) Sound.Silent else Sound.Film) }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(player, sources) {
        val ladder = LadderPlayback(player, sources)
        ladder.start()
        player.repeatMode = if (gif) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (gif) player.volume = 0f
        onDispose { ladder.release() }
    }
    DisposableEffect(playing) {
        player.playWhenReady = playing
        onDispose { }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = !gif
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BoxScope.TopBar(
    current: MediaAttachment?,
    index: Int,
    count: Int,
    actions: MediaViewerActions,
    bars: () -> Color,
) {
    val top = Modifier.align(Alignment.TopCenter).fillMaxWidth()
    Row(
        top.drawBehind { drawRect(bars()) }.statusBarsPadding().padding(AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::onClose) {
            Icon(AlohaIcons.Close, stringResource(R.string.viewer_close), tint = Color.White)
        }
        if (count > 1) {
            Text(
                stringResource(R.string.viewer_position, index + 1, count),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        current?.let { attachment ->
            IconButton(onClick = { actions.onShare(attachment) }) {
                Icon(AlohaIcons.Share, stringResource(R.string.viewer_share), tint = Color.White)
            }
            More(attachment, actions)
        }
    }
}

@Composable
private fun More(attachment: MediaAttachment, actions: MediaViewerActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            open = true
        }) { Icon(AlohaIcons.More, stringResource(R.string.viewer_more), tint = Color.White) }
        // dark whatever the app's theme, as everything over the picture is
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MENU) {
            listOf(
                R.string.viewer_save to { actions.onSave(attachment) },
                R.string.viewer_copy to { actions.onCopy(attachment) },
                R.string.viewer_open_in_browser to { actions.onOpenInBrowser(attachment) },
                R.string.viewer_report to { actions.onReport() },
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action()
                    },
                    colors = MenuDefaults.itemColors(textColor = Color.White),
                )
            }
        }
    }
}

/** Zooming in and out as buttons, for Switch Access and anyone who cannot pinch. */
@Composable
private fun ZoomButtons(zoom: Float, onZoom: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        IconButton(onClick = { onZoom((zoom / ZOOM_STEP).coerceAtLeast(1f)) }, enabled = zoom > 1f) {
            Icon(AlohaIcons.ZoomOut, stringResource(R.string.viewer_zoom_out), tint = onDark(zoom > 1f))
        }
        IconButton(onClick = { onZoom((zoom * ZOOM_STEP).coerceAtMost(MAXIMUM_ZOOM)) }, enabled = zoom < MAXIMUM_ZOOM) {
            Icon(AlohaIcons.ZoomIn, stringResource(R.string.viewer_zoom_in), tint = onDark(zoom < MAXIMUM_ZOOM))
        }
    }
}

/** The viewer's menu: a neutral near-black, never tinted by the app's colours. */
private val MENU = Color(0xFF212121)

/** White on the dark backdrop, dimmed where the button cannot be used now. */
private fun onDark(enabled: Boolean): Color = if (enabled) Color.White else Color.White.copy(alpha = DISABLED)

private const val DISABLED = 0.38f
private const val MAXIMUM_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_STEP = 1.5f
private const val DISMISS_DISTANCE = 400f
private const val MINIMUM_BACKDROP = 0.3f
private const val FLING_VELOCITY = 1_500f
private const val OPENED_FROM = 0.92f

/**
 * How the viewer comes and goes: it springs in, scaling up from a little smaller as it fades in; it
 * springs away the same way when closed, and a fling carries the picture off at its own speed. All of
 * it at once where motion is reduced.
 */
@Stable
private class ViewerMotion(
    private val scope: CoroutineScope,
    private val reduced: Boolean,
    private val height: Float,
    private val onClosed: () -> Unit,
    private val onDrag: (Float) -> Unit,
) {
    private val presence = Animatable(if (reduced) 1f else 0f)

    val shown: Float get() = presence.value

    fun open() {
        if (!reduced) scope.launch { presence.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    fun close() {
        scope.launch {
            if (!reduced) presence.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
            onClosed()
        }
    }

    fun fling(distance: Float, velocity: Float) {
        scope.launch {
            if (!reduced) {
                val away = if (distance < 0) -height else height
                animate(distance, away, initialVelocity = velocity, animationSpec = spring()) { value, _ ->
                    onDrag(value)
                }
            }
            onClosed()
        }
    }
}

@Composable
private fun rememberViewerMotion(onClosed: () -> Unit, onDrag: (Float) -> Unit): ViewerMotion {
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val height = LocalWindowInfo.current.containerSize.height.toFloat()
    val closed by rememberUpdatedState(onClosed)
    return remember(reduced) { ViewerMotion(scope, reduced, height, { closed() }, onDrag) }.also {
        LaunchedEffect(it) { it.open() }
    }
}
