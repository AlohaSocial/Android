// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.model.SwipeAction
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.rememberHaptics

/**
 * Swiping a post across does what the settings chose for each direction (favouriting towards the end
 * and boosting towards the start until chosen otherwise); a direction set to nothing stays still. Much
 * as Talk Android's conversation list does it: the swipe counts once it has gone three tenths of the
 * row's width, where it ticks and the icon behind pops; it goes no further than nine twentieths; on
 * release it acts if it counted, and the post springs straight back without overshooting.
 */
@Composable
internal fun SwipeRow(
    row: StatusRowUi,
    selected: Boolean,
    towardsEnd: SwipeAction,
    towardsStart: SwipeAction,
    onSwipe: (StatusRowUi, SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val current by rememberUpdatedState(row)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val threshold = width * THRESHOLD
        val limit = width * LIMIT
        val ends by rememberUpdatedState(towardsEnd to towardsStart)
        Box(
            Modifier.pointerInput(rtl, width) {
                var dragged = 0f
                var counted = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragged = offset.value
                        counted = false
                    },
                    onDragEnd = {
                        val action = actionAt(dragged, threshold, ends)
                        dragged = 0f
                        if (action != SwipeAction.None) onSwipe(current, action)
                        scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onDragCancel = {
                        dragged = 0f
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                ) { change, dx ->
                    val next = within(dragged + if (rtl) -dx else dx, limit, ends)
                    dragged = next
                    scope.launch { offset.snapTo(next) }
                    val past = abs(next) >= threshold
                    if (past && !counted) {
                        haptics(HapticFeedbackType.GestureThresholdActivate)
                        scope.launch {
                            pop.snapTo(POP)
                            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                    counted = past
                    change.consume()
                }
            },
        ) {
            if (offset.value != 0f) {
                SwipeBackground(if (offset.value > 0f) towardsEnd else towardsStart, offset.value > 0f, pop.value)
            }
            val shift = if (rtl) -offset.value else offset.value
            Post(selected, Modifier.offset { IntOffset(shift.roundToInt(), 0) }, content)
        }
    }
}

/** The action a drag of [dragged] acts on once let go: towards the end, towards the start, or none. */
private fun actionAt(dragged: Float, threshold: Float, ends: Pair<SwipeAction, SwipeAction>): SwipeAction = when {
    dragged >= threshold -> ends.first
    dragged <= -threshold -> ends.second
    else -> SwipeAction.None
}

/** [offset] held to [limit] each way, and to nothing towards a side whose action is none. */
private fun within(offset: Float, limit: Float, ends: Pair<SwipeAction, SwipeAction>): Float = offset.coerceIn(
    if (ends.second == SwipeAction.None) 0f else -limit,
    if (ends.first == SwipeAction.None) 0f else limit,
)

/** What shows behind the post as it moves: the action's icon at the side the post left. */
@Composable
private fun BoxScope.SwipeBackground(action: SwipeAction, end: Boolean, scale: Float) {
    Row(
        Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = AlohaSpacing.l),
        horizontalArrangement = if (end) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) { SwipeIcon(action, scale) }
}

/** The post itself; the one the keyboard selected stands out by a bar along its start as well as by its colour. */
@Composable
private fun Post(selected: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val bar = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.run { if (selected) surfaceContainerHigh else background },
        modifier = modifier.drawWithContent {
            drawContent()
            if (selected) {
                val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - SELECTED_BAR.toPx()
                drawRect(bar, Offset(x, 0f), Size(SELECTED_BAR.toPx(), size.height))
            }
        },
    ) { content() }
}

/** The icon behind a swiped post, popped by [scale] as the swipe starts to count. */
@Composable
private fun SwipeIcon(action: SwipeAction, scale: Float) {
    val semantic = LocalAlohaSemanticColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, tint) = when (action) {
        SwipeAction.Favourite -> AlohaIcons.Favourited to semantic.favourite
        SwipeAction.Boost -> AlohaIcons.Boosted to semantic.boost
        SwipeAction.Bookmark -> AlohaIcons.Bookmarked to semantic.bookmark
        SwipeAction.Reply -> AlohaIcons.Reply to muted
        SwipeAction.None -> return
    }
    Icon(
        icon,
        stringResource(swipeLabel(action)),
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        tint = tint,
    )
}

/** What each swipe choice is called, here and in the settings. */
internal fun swipeLabel(action: SwipeAction): Int = when (action) {
    SwipeAction.Favourite -> R.string.timeline_swipe_favourite
    SwipeAction.Boost -> R.string.timeline_swipe_boost
    SwipeAction.Bookmark -> R.string.timeline_swipe_bookmark
    SwipeAction.Reply -> R.string.timeline_swipe_reply
    SwipeAction.None -> R.string.timeline_swipe_none
}

private const val THRESHOLD = 0.3f
private const val LIMIT = 0.45f
private const val POP = 1.35f
private val SELECTED_BAR = 3.dp
