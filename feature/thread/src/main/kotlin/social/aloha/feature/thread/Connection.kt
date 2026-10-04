// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.ApplicationSummary
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.motion
import social.aloha.core.ui.openInBrowser

/** Whether a post's line runs up to the post it answers and down to the reply under it. */
internal data class Connection(val up: Boolean, val down: Boolean)

/**
 * The lines between a post and the one it answers. Ancestors and the focused post form one chain; a
 * reply follows its parent directly in the list, one level deeper. The focused post sends no line down:
 * its content runs the full width, where a line would cross it.
 */
internal fun connections(items: List<ThreadItem>): List<Connection> = items.mapIndexed { index, item ->
    Connection(up = linked(items.getOrNull(index - 1), item), down = linked(item, items.getOrNull(index + 1)))
}

private fun linked(upper: ThreadItem?, lower: ThreadItem?): Boolean =
    upper is ThreadItem.Post && lower is ThreadItem.Post && !upper.focused &&
        ((upper.depth == 0 && lower.depth == 0) || lower.depth == upper.depth + 1)

/**
 * Draws a post's share of the line: from its top to its avatar, at the avatar of the post it answers
 * ([parentDepth]), and from its avatar to its bottom, at its own avatar ([depth]).
 */
@Composable
internal fun Modifier.connector(connection: Connection, depth: Int, parentDepth: Int, indent: (Int) -> Dp): Modifier {
    if (!connection.up && !connection.down) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    val top = if (LocalReadingStyle.current.compact) AlohaSpacing.xs else AlohaSpacing.s
    return drawBehind {
        fun x(level: Int): Float {
            val start = (indent(level) + AlohaSpacing.m + AVATAR / 2).toPx()
            return if (layoutDirection == LayoutDirection.Rtl) size.width - start else start
        }
        val width = LINE.toPx()
        val avatarTop = top.toPx()
        if (connection.up) drawLine(color, Offset(x(parentDepth), 0f), Offset(x(parentDepth), avatarTop), width)
        if (connection.down) {
            drawLine(color, Offset(x(depth), avatarTop + AVATAR.toPx()), Offset(x(depth), size.height), width)
        }
    }
}

private val AVATAR = 44.dp
private val LINE = 2.dp
