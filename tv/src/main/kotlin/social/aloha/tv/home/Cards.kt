// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import androidx.tv.material3.WideClassicCard
import coil3.compose.AsyncImage
import social.aloha.tv.R
import social.aloha.tv.tap
import social.aloha.tv.watch.PlayerSurface

internal val CARD_WIDTH = 220.dp
private const val WIDE = 16f / 9
private val AVATAR_SIZE = 64.dp

/**
 * A video's card: its still, or the [player]'s muted preview while it is the one [previewing]; a covered
 * one says so in place of its still. Its title and author under it, and how far the reader got over it.
 */
@Composable
internal fun VideoCard(
    card: VideoCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewing: Boolean = false,
    player: Player? = null,
) {
    val described = listOfNotNull(
        card.title,
        card.author,
        stringResource(R.string.tv_sensitive).takeIf { card.covered },
    ).joinToString(", ")
    StandardCardContainer(
        modifier = modifier.width(CARD_WIDTH).semantics(mergeDescendants = true) { contentDescription = described },
        imageCard = { interaction ->
            Card(
                onClick = onClick,
                interactionSource = interaction,
                modifier = Modifier.aspectRatio(WIDE).tap(onClick),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Still(card)
                    if (previewing && player != null) PlayerSurface(player, controls = false, Modifier.fillMaxSize())
                    card.progress?.let { Progress(it, Modifier.align(Alignment.BottomStart)) }
                }
            }
        },
        title = {
            Text(
                card.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        },
        subtitle = {
            Text(
                card.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun Still(card: VideoCardUi) {
    if (card.covered) {
        val colour = card.coverColour?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant
        Box(Modifier.fillMaxSize().background(colour), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.tv_sensitive), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        AsyncImage(
            model = card.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun Progress(fraction: Float, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/** Someone followed, as a round avatar with their name, which opens their channel. */
@Composable
internal fun PersonCard(person: PersonCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WideClassicCard(
        onClick = onClick,
        modifier = modifier.width(CARD_WIDTH).tap(onClick),
        image = {
            AsyncImage(
                model = person.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(12.dp).size(AVATAR_SIZE).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        title = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text("@${person.handle}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
