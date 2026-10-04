// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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

/** How far a pressed button shrinks. */
public const val SQUISH: Float = 0.85f
