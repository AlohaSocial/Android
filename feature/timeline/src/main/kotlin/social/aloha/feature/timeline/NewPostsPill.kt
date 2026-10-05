// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaMotion
import social.aloha.core.designsystem.LocalBlackTheme
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.StackedAvatars
import social.aloha.core.ui.rememberReducedMotion
import social.aloha.core.ui.squish

/**
 * How many new posts wait, and who wrote them: it arrives decelerating and leaves accelerating, squishes
 * under the finger, and a flick upwards sends it away until more arrive.
 */
@Composable
internal fun NewPostsPill(count: Int, avatars: List<String?>, onReveal: () -> Unit, modifier: Modifier) {
    var dismissed by remember { mutableStateOf<Int?>(null) }
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = count > 0 && count != dismissed,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else pillEnter,
        exit = if (reduced) ExitTransition.None else pillExit,
    ) {
        val label = pluralStringResource(R.plurals.timeline_new_posts, count, count)
        val dismiss = stringResource(UiR.string.list_new_posts_dismiss)
        val interactions = remember { MutableInteractionSource() }
        val flick = with(LocalDensity.current) { FLICK.toPx() }
        // who posted says it at a glance, beside how many
        Button(
            onClick = onReveal,
            interactionSource = interactions,
            colors = pillColours(),
            border = pillBorder(),
            modifier = Modifier
                .squish(interactions)
                .draggable(
                    rememberDraggableState {},
                    Orientation.Vertical,
                    onDragStopped = { velocity -> if (velocity < -flick) dismissed = count },
                )
                .semantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(CustomAccessibilityAction(dismiss) { true.also { dismissed = count } })
                },
            contentPadding = if (avatars.isEmpty()) {
                ButtonDefaults.ContentPadding
            } else {
                ButtonDefaults.ButtonWithIconContentPadding
            },
        ) {
            if (avatars.isEmpty()) {
                Text(label)
            } else {
                Icon(AlohaIcons.NewPosts, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                StackedAvatars(avatars, PILL_AVATAR, ring = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(label)
            }
        }
    }
}

/** Black keeps the pill to an outline, as it keeps every filled surface off an OLED screen. */
@Composable
private fun pillColours() =
    if (LocalBlackTheme.current) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()

@Composable
private fun pillBorder() = if (LocalBlackTheme.current) ButtonDefaults.outlinedButtonBorder(enabled = true) else null

private val PILL_AVATAR = 24.dp
private val FLICK = 600.dp
private val pillEnter =
    slideInVertically(tween(AlohaMotion.MEDIUM, easing = AlohaMotion.EmphasizedDecelerate)) { -it } +
        fadeIn(tween(AlohaMotion.MEDIUM, easing = AlohaMotion.EmphasizedDecelerate))
private val pillExit = slideOutVertically(tween(AlohaMotion.SHORT, easing = AlohaMotion.EmphasizedAccelerate)) { -it } +
    fadeOut(tween(AlohaMotion.SHORT, easing = AlohaMotion.EmphasizedAccelerate))
