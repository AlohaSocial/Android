// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import social.aloha.core.designsystem.AlohaMotion

/**
 * Whether motion is to be left out: the reader turned on Reduce motion in Reading, or the system asks for
 * no animation (the developer option or the accessibility setting sets the animator duration scale to zero).
 */
@Composable
public fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val system = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    return system || LocalReadingStyle.current.reduceMotion
}

/** [spec], or a jump straight to the end where motion is reduced. */
@Composable
public fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (rememberReducedMotion()) snap() else spec

/** Shrinks to [SQUISH] while [interactions] report a press, and springs back; still where motion is reduced. */
@Composable
public fun Modifier.squish(interactions: InteractionSource): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) SQUISH else 1f, motion(spring()), label = "squish")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pull to refresh, with a tick under the finger the moment the pull is far enough to refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = rememberHaptics()
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .filter { it }
            .collect { haptics(HapticFeedbackType.GestureThresholdActivate) }
    }
    PullToRefreshBox(refreshing, onRefresh, modifier, state = state, content = content)
}

/**
 * A list row's arrival, move and departure, as rows are revealed, filled in or taken away; none where
 * motion is reduced. [item] is the row's scope in its lazy list.
 */
@Composable
public fun Modifier.itemMotion(item: LazyItemScope): Modifier = if (rememberReducedMotion()) {
    this
} else {
    with(item) {
        animateItem(
            fadeInSpec = tween(AlohaMotion.MEDIUM, easing = AlohaMotion.EmphasizedDecelerate),
            placementSpec = tween(AlohaMotion.MEDIUM, easing = AlohaMotion.Emphasized),
            fadeOutSpec = tween(AlohaMotion.SHORT, easing = AlohaMotion.EmphasizedAccelerate),
        )
    }
}

/**
 * A shake from side to side, with a tick, each time [trigger] goes up: an action that failed, or a link
 * to what is already open. Nothing moves where motion is reduced; the tick stays.
 */
@Composable
public fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    val haptics = rememberHaptics()
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        haptics(HapticFeedbackType.Reject)
        if (!reduced) {
            offset.snapTo(0f)
            offset.animateTo(
                0f,
                spring(dampingRatio = SHAKE_DAMPING, stiffness = Spring.StiffnessMedium),
                SHAKE_VELOCITY,
            )
        }
    }
    return graphicsLayer { translationX = offset.value }
}

/**
 * A row the reader has not seen yet, washed in the primary colour at a quarter and fading out over two
 * seconds; where motion is reduced it stays for the two seconds and then goes at once.
 */
@Composable
public fun Modifier.freshHighlight(fresh: Boolean): Modifier {
    val alpha = remember { Animatable(if (fresh) FRESH_ALPHA else 0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(fresh) {
        if (!fresh) return@LaunchedEffect
        alpha.snapTo(FRESH_ALPHA)
        if (reduced) {
            delay(FRESH_MILLIS.toLong())
            alpha.snapTo(0f)
        } else {
            alpha.animateTo(0f, tween(FRESH_MILLIS, easing = AlohaMotion.Standard))
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    return drawBehind { if (alpha.value > 0f) drawRect(primary.copy(alpha = alpha.value)) }
}

private const val SHAKE_DAMPING = 0.25f
private const val SHAKE_VELOCITY = 2_400f
private const val FRESH_ALPHA = 0.25f
private const val FRESH_MILLIS = 2_000

/** How far a pressed button shrinks. */
private const val SQUISH: Float = 0.85f
