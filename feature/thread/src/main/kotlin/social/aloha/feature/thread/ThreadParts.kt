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

/** "Reply to {name}", always at hand above the bottom inset, with the reader's own face. */
@Composable
internal fun ReplyBar(focused: StatusRowUi, readerAvatar: String?, onReply: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(role = Role.Button, onClick = onReply)
                .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Avatar(readerAvatar, BAR_AVATAR)
            Text(
                stringResource(R.string.thread_reply_to, focused.author.plainName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "Back to the post", once the focused post has scrolled out of view, pointing the way it lies. */
@Composable
internal fun BackToPost(listState: LazyListState, focusedIndex: Int, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val direction by remember(focusedIndex) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            when {
                focusedIndex < 0 || visible.isEmpty() || visible.any { it.index == focusedIndex } -> 0
                focusedIndex < visible.first().index -> -1
                else -> 1
            }
        }
    }
    AnimatedVisibility(
        visible = direction != 0,
        modifier = modifier,
        enter = fadeIn(motion(tween())),
        exit = fadeOut(motion(tween())),
    ) {
        AssistChip(
            onClick = { scope.launch { listState.animateScrollToItem(focusedIndex) } },
            label = { Text(stringResource(R.string.thread_back_to_post)) },
            leadingIcon = {
                Icon(
                    if (direction < 0) AlohaIcons.ArrowUpward else AlohaIcons.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
    }
}

/** When the focused post was made, to the minute, or to the second once tapped, and the app it came from. */
@Composable
internal fun PostedLine(createdAt: Instant, application: ApplicationSummary?) {
    var seconds by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Row(
        Modifier.padding(horizontal = AlohaSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            fullDate(createdAt, withSeconds = seconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.thread_date_seconds)) {
                seconds = !seconds
            },
        )
        application?.let { app ->
            val website = app.website
            Text(
                stringResource(R.string.thread_via, app.name),
                style = MaterialTheme.typography.labelMedium,
                color = if (website !=
                    null
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = if (website != null) Modifier.clickable { openInBrowser(context, website) } else Modifier,
            )
        }
    }
}

/** A count's label with the number in bold, "**123** boosts", as the footer's chips read. */
internal fun boldNumber(label: String, count: Int?): AnnotatedString = buildAnnotatedString {
    append(label)
    val number = count?.toString() ?: return@buildAnnotatedString
    val at = label.indexOf(number)
    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + number.length)
}

private val BAR_AVATAR = 32.dp
