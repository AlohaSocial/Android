// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.designsystem.AlohaSpacing

/**
 * The stories rail atop Photos: the reader's own first, then the people they follow as the server
 * ranked them, a ring on whoever has one not yet seen. Nothing at all where there are none, since a
 * Nextcloud hears of few: Pixelfed sends stories only to servers it knows as Pixelfed.
 */
@Composable
public fun StoriesRail(onOpen: (accountId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<StoriesRailViewModel>()
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    StoriesRail(reels, onOpen, modifier)
}

@Composable
internal fun StoriesRail(reels: List<StoryReel>, onOpen: (accountId: String) -> Unit, modifier: Modifier = Modifier) {
    if (reels.isEmpty()) return
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
    ) {
        items(reels, key = { it.account.id }) { reel -> Reel(reel, onOpen) }
    }
}

@Composable
private fun Reel(reel: StoryReel, onOpen: (String) -> Unit) {
    val name = if (reel.own) stringResource(R.string.stories_yours) else reel.account.bestDisplayName
    val label = if (reel.unseen) {
        stringResource(R.string.stories_reel_new, name)
    } else {
        stringResource(R.string.stories_reel, name)
    }
    // a ring in the accent colour says there is something new; a seen reel's is quiet, not missing
    val ring = if (reel.unseen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.width(REEL).clickable { onOpen(reel.account.id) }.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        AsyncImage(
            model = reel.account.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(AVATAR).border(RING, ring, CircleShape).padding(RING_GAP).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val REEL = 72.dp
private val AVATAR = 64.dp
private val RING = 3.dp
private val RING_GAP = 5.dp
