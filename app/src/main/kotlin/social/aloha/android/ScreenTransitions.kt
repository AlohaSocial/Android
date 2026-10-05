// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaMotion
import social.aloha.core.ui.rememberReducedMotion

/**
 * How one screen gives way to the next: Material's shared axis along x, the new screen arriving from the
 * end as the old one leaves towards the start, and back the other way. [slide] is how far each moves, in
 * pixels, negative in a right-to-left layout so the axis mirrors. Where motion is [reduced], screens
 * change at once.
 */
internal class ScreenTransitions(private val slide: Int, private val reduced: Boolean) {
    fun forward(): ContentTransform = axis(towardsStart = true)

    fun back(): ContentTransform = axis(towardsStart = false)

    /** A back gesture in progress: the screen being left shrinks after the finger, the one below fades in. */
    fun predictiveBack(): ContentTransform = if (reduced) {
        NONE
    } else {
        ContentTransform(
            fadeIn(tween(AlohaMotion.MEDIUM)) + slideInHorizontally(tween(AlohaMotion.MEDIUM)) { -slide },
            scaleOut(targetScale = PREDICTIVE_SCALE) + fadeOut(tween(AlohaMotion.MEDIUM)),
        )
    }

    private fun axis(towardsStart: Boolean): ContentTransform {
        if (reduced) return NONE
        val offset = if (towardsStart) slide else -slide
        val move = tween<IntOffset>(AlohaMotion.MEDIUM, easing = AlohaMotion.Emphasized)
        return ContentTransform(
            fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = AlohaMotion.EmphasizedDecelerate)) +
                slideInHorizontally(move) { offset },
            fadeOut(tween(FADE_OUT, easing = AlohaMotion.EmphasizedAccelerate)) +
                slideOutHorizontally(move) { -offset },
        )
    }

    private companion object {
        val NONE = ContentTransform(EnterTransition.None, ExitTransition.None)

        // the old screen is gone within the first third, the new one fades in over the rest
        const val FADE_OUT = 90
        const val FADE_IN = AlohaMotion.MEDIUM - FADE_OUT
        const val PREDICTIVE_SCALE = 0.9f
    }
}

@Composable
internal fun rememberScreenTransitions(): ScreenTransitions {
    val distance = with(LocalDensity.current) { SLIDE.roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = rememberReducedMotion()
    return remember(distance, rtl, reduced) { ScreenTransitions(if (rtl) -distance else distance, reduced) }
}

private val SLIDE = 30.dp
