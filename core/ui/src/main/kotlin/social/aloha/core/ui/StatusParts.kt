// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Card
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SensitiveMediaPolicy

@Composable
internal fun StatusBody(
    row: StatusRowUi,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    canReact: Boolean,
    animateEmoji: Boolean,
    focused: Boolean = false,
    controls: CardControls = CardControls(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        if (row.body.isNotEmpty()) {
            // the post a thread is about reads larger than the replies around it
            val style = if (focused) {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
            } else {
                MaterialTheme.typography.bodyLarge
            }
            Text(
                row.body,
                inlineContent = rememberEmojiContent(row.emojis, animateEmoji),
                style = style.contentDirection(),
            )
        }
        if (row.media.isNotEmpty()) MediaGrid(row.media, row.sensitive, policy) { actions.onMedia(row, it) }
        row.poll?.let {
            PollView(it, controls.pollChoice, controls.onPollChoice) { choices -> actions.onVote(row, choices) }
        }
        row.card?.let { card ->
            val context = LocalContext.current
            LinkCard(card) { openCard(context, card, actions) }
        }
        row.quote?.let { QuoteCard(it) { actions.onOpen(it.statusId) } }
        StatusExtras(row, canReact, actions)
    }
}

/** What follows the body: a withdrawn quote, the place and archive chips, and the reactions. */
@Composable
private fun StatusExtras(row: StatusRowUi, canReact: Boolean, actions: StatusActions) {
    if (row.quoteWithdrawn) {
        Text(
            stringResource(R.string.status_quote_withdrawn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    MetaChips(row)
    if (canReact || row.reactions.isNotEmpty()) ReactionRow(row, canReact, actions)
}

/**
 * Laid out by count: one at its own shape within bounds, two side by side, three or four in a grid. What
 * is sensitive is covered while the policy says so, and is not even downloaded until revealed.
 */
@Composable
internal fun MediaGrid(
    media: List<MediaAttachment>,
    sensitive: Boolean,
    policy: SensitiveMediaPolicy,
    onOpen: (Int) -> Unit,
) {
    val covered = sensitive && !policy.allowsAutomaticReveal
    if (covered && !policy.drawsAtAll) {
        Text(
            stringResource(R.string.status_sensitive_hidden),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var revealed by rememberSaveable(media.firstOrNull()?.id) { mutableStateOf(false) }
    val shown = media.take(MAX_TILES)
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        when (shown.size) {
            1 -> Tile(
                shown[0],
                Modifier.fillMaxWidth().aspectRatio(
                    shown[0].displayAspectRatio.toFloat().coerceIn(MIN_ASPECT, MAX_ASPECT),
                ),
                covered && !revealed,
            ) { onOpen(0) }

            else -> Grid(shown, covered && !revealed, onOpen)
        }
        if (covered && !revealed) SensitiveCover(Modifier.matchParentSize()) { revealed = true }
    }
}

@Composable
private fun Grid(media: List<MediaAttachment>, hidden: Boolean, onOpen: (Int) -> Unit) {
    val rows = if (media.size == 2) listOf(media.indices.toList()) else media.indices.chunked(2)
    val aspect = if (media.size == 2) PAIR_ASPECT else 1f
    Column(verticalArrangement = Arrangement.spacedBy(GUTTER)) {
        rows.forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(GUTTER)) {
                indices.forEach { index ->
                    Tile(media[index], Modifier.weight(1f).aspectRatio(aspect), hidden) { onOpen(index) }
                }
            }
        }
    }
}

@Composable
private fun Tile(attachment: MediaAttachment, modifier: Modifier, hidden: Boolean, onOpen: () -> Unit) {
    Box(modifier.clickable(enabled = !hidden, onClick = onOpen)) {
        if (hidden) {
            // the cover shows the blurhash only: nothing is fetched from a sensitive post until it is revealed
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                rememberBlurHashPainter(attachment.blurhash)?.let {
                    androidx.compose.foundation.Image(
                        it,
                        null,
                        Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        } else {
            MediaImage(attachment, attachment.description, Modifier.matchParentSize(), fitToAspect = false)
            if (attachment.type.isPlayable &&
                attachment.type != AttachmentKind.Audio
            ) {
                PlayBadge(Modifier.align(Alignment.Center))
            }
            if (attachment.hasAltText) AltBadge(Modifier.align(Alignment.BottomStart).padding(AlohaSpacing.xs))
        }
    }
}

@Composable
private fun SensitiveCover(modifier: Modifier, onReveal: () -> Unit) {
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = COVER_ALPHA)).clickable(onClick = onReveal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(AlohaIcons.Sensitive, contentDescription = null, tint = Color.White)
        Text(
            stringResource(R.string.status_sensitive_cover),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Text(
            stringResource(R.string.status_sensitive_show),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayBadge(modifier: Modifier) {
    Surface(
        modifier.size(PLAY_BADGE),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = COVER_ALPHA),
    ) {
        Icon(
            AlohaIcons.Play,
            contentDescription = stringResource(R.string.status_media_video),
            tint = Color.White,
            modifier = Modifier.padding(AlohaSpacing.xs),
        )
    }
}

@Composable
private fun AltBadge(modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraSmall, color = Color.Black.copy(alpha = COVER_ALPHA)) {
        Text(
            stringResource(R.string.status_alt_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = AlohaSpacing.xxs),
        )
    }
}

@Composable
internal fun LinkCard(card: Card, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            card.image?.let { image ->
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        placeholder = rememberBlurHashPainter(card.blurhash),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(
                            if (card.playable) VIDEO_ASPECT else CARD_ASPECT,
                        ),
                    )
                    // the server's own copy of the thumbnail; nothing is asked of the video's site before a tap
                    if (card.playable) PlayBadge(Modifier)
                }
            }
            Column(Modifier.padding(AlohaSpacing.s), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs)) {
                if (card.displayProvider.isNotEmpty()) {
                    Text(
                        card.displayProvider,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (card.title.isNotBlank()) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.description.isNotBlank()) {
                    Text(
                        card.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuoteCard(quote: StatusRowUi.QuoteUi, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(AlohaSpacing.s), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
            ) {
                Avatar(quote.author.avatarUrl, QUOTE_AVATAR)
                Text(
                    quote.author.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    quote.author.handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val spoiler = quote.spoiler
            if (spoiler != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AlohaIcons.ContentWarning, contentDescription = null, modifier = Modifier.size(QUOTE_ICON))
                    Text(spoiler, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    quote.excerpt,
                    style = MaterialTheme.typography.bodyMedium.contentDirection(),
                    maxLines = QUOTE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                quote.firstMedia?.let {
                    MediaImage(
                        it,
                        it.description,
                        Modifier.fillMaxWidth().heightIn(max = QUOTE_MEDIA).clip(MaterialTheme.shapes.small),
                        fitToAspect = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(row: StatusRowUi, canReact: Boolean, actions: StatusActions) =
    Reactions(row.reactions, canReact) { name, add -> actions.onReact(row, name, add) }

/**
 * What people wrote takes its direction from its own first strong character, not from the interface: an
 * English post in an Arabic interface keeps its punctuation at the end, and an Arabic one in English at the start.
 */
public fun TextStyle.contentDirection(): TextStyle = copy(textDirection = TextDirection.Content)

private const val MAX_TILES = 4
private const val MIN_ASPECT = 0.8f
private const val MAX_ASPECT = 1.91f
private const val PAIR_ASPECT = 0.9f
private const val CARD_ASPECT = 1.91f
private const val VIDEO_ASPECT = 16f / 9f
private const val COVER_ALPHA = 0.55f
private const val QUOTE_LINES = 4
private val GUTTER = 2.dp
private val PLAY_BADGE = 48.dp
private val QUOTE_AVATAR = 24.dp
private val QUOTE_ICON = 16.dp
private val QUOTE_MEDIA = 160.dp
