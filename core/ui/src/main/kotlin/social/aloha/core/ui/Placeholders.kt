// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing

/**
 * Grey shapes where the first rows are on their way, read as one [label] element: [rows] of an avatar of
 * [avatar] beside a name and two lines, the shape of a post, an account or a notification alike.
 */
@Composable
public fun Skeleton(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.list_loading),
    rows: Int = SKELETON_ROWS,
    avatar: Dp = AVATAR,
) {
    val shade = MaterialTheme.colorScheme.surfaceContainerHigh
    val line = MaterialTheme.shapes.extraSmall
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
        repeat(rows) {
            Row(Modifier.padding(AlohaSpacing.m), horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                Box(Modifier.size(avatar).background(shade, avatarShape()))
                Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
                    Box(Modifier.width(NAME).height(LINE).background(shade, line))
                    Box(Modifier.fillMaxWidth().height(LINE).background(shade, line))
                    Box(Modifier.width(SHORT).height(LINE).background(shade, line))
                }
            }
        }
    }
}

/** What an empty screen says: a [title] read as a heading, a [body] that explains, and an [action] to take. */
@Composable
public fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: EmptyAction? = null,
) {
    Column(
        modifier.fillMaxSize().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.let { Button(onClick = it.onClick) { Text(it.label) } }
    }
}

/** The one thing an empty screen offers to do about it. */
public class EmptyAction(public val label: String, public val onClick: () -> Unit)

private const val SKELETON_ROWS = 5
private val NAME = 120.dp
private val SHORT = 180.dp
private val LINE = 14.dp
