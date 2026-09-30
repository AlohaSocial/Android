// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Card
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Poll
import social.aloha.core.model.Reaction
import social.aloha.core.model.SensitiveMediaPolicy

/** The place and archive marks under a post: facts about it, not controls. */
@Composable
internal fun MetaChips(row: StatusRowUi) {
    val place = row.place
    if (place == null && !row.archived) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        // facts about the post, not controls: labels rather than chips that do nothing when tapped
        if (place != null) MetaLabel(AlohaIcons.Place, place.label)
        if (row.archived) MetaLabel(AlohaIcons.Archived, stringResource(R.string.status_archived))
    }
}

@Composable
private fun MetaLabel(icon: ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(horizontal = AlohaSpacing.s, vertical = AlohaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(META_ICON))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val META_ICON = 18.dp
