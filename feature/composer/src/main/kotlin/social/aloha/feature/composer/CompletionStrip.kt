// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.CustomEmoji
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.motion

/**
 * The completions for the word at the cursor, as a strip of chips growing out of the bar under it: three
 * blank chips while the server is asked, a way to search where no one matches a mention, and the whole
 * emoji picker where no emoji matches.
 */
@Composable
internal fun CompletionStrip(
    completions: CompletionsUi,
    emojis: List<CustomEmoji>,
    actions: TypingActions,
    modifier: Modifier = Modifier,
) {
    var browsing by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = completions.shown(hasEmojis = emojis.isNotEmpty()),
        modifier = modifier,
        enter = expandVertically(motion(spring()), expandFrom = Alignment.Bottom) + fadeIn(motion(spring())),
        exit = shrinkVertically(motion(spring()), shrinkTowards = Alignment.Bottom) + fadeOut(motion(spring())),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AlohaSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) { Chips(completions, actions) { browsing = true } }
    }
    if (browsing) {
        // what is picked takes the place of the word typed, as a completion would
        EmojiSheet(emojis, onPick = {
            actions.onSuggestion(Suggestion(":${it.shortcode}:", ":${it.shortcode}:", it.url))
        }, onDismiss = { browsing = false })
    }
}

@Composable
private fun Chips(completions: CompletionsUi, actions: TypingActions, onBrowse: () -> Unit) {
    when {
        completions.loading && completions.items.isEmpty() -> Looking()

        completions.items.isNotEmpty() -> completions.items.forEach { suggestion ->
            AssistChip(
                onClick = { actions.onSuggestion(suggestion) },
                label = { Text(suggestion.label, maxLines = 1) },
                leadingIcon = suggestion.imageUrl?.let { url -> { Avatar(url, CHIP_IMAGE) } },
            )
        }

        completions.kind == CompletionKind.Account -> AssistChip(
            onClick = { actions.onFindPeople(completions.query) },
            label = { Text(stringResource(R.string.composer_find_people)) },
            leadingIcon = { Icon(AlohaIcons.Search, contentDescription = null, Modifier.size(CHIP_IMAGE)) },
        )

        else -> AssistChip(
            onClick = onBrowse,
            label = { Text(stringResource(R.string.composer_browse_emoji)) },
            leadingIcon = { Icon(AlohaIcons.Emoji, contentDescription = null, Modifier.size(CHIP_IMAGE)) },
        )
    }
}

/** Three blank chips while the server is asked, said once to a screen reader. */
@Composable
private fun Looking() {
    val looking = stringResource(R.string.composer_completions_loading)
    Row(
        Modifier.padding(vertical = AlohaSpacing.s).semantics {
            contentDescription = looking
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        BLANKS.forEach { width ->
            Box(
                Modifier.size(width, CHIP_HEIGHT).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private val CHIP_IMAGE = 18.dp
private val CHIP_HEIGHT = 32.dp
private val BLANKS = listOf(96.dp, 72.dp, 112.dp)
