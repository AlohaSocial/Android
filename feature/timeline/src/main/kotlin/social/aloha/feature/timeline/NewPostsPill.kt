// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.content.res.Configuration
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaMotion
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalBlackTheme
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.CaughtUpDivider
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.R as UiR
import social.aloha.core.ui.RefreshBox
import social.aloha.core.ui.StackedAvatars
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.TopBarTitle
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.itemMotion
import social.aloha.core.ui.readingColumn
import social.aloha.core.ui.rememberReducedMotion
import social.aloha.core.ui.rememberTopScroll
import social.aloha.core.ui.scrollToTop
import social.aloha.core.ui.shake
import social.aloha.core.ui.squish
import social.aloha.core.ui.topScrollTail

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
