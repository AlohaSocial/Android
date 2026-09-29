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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Card
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Poll
import social.aloha.core.model.SensitiveMediaPolicy

/**
 * The options, as choices until the reader votes or the poll closes, then as results: showing the
 * results first would tell people how to vote.
 */
@Composable
internal fun PollView(poll: Poll, chosen: List<Int>, onChosen: (List<Int>) -> Unit, onVote: (List<Int>) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        poll.options.forEachIndexed { index, option ->
            if (poll.showsResults) {
                val share = poll.shareOfOptionAt(index).toFloat()
                val percent = NumberFormat.getPercentInstance().format(share)
                val mine = index in poll.ownVotes
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
                        ) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyMedium.contentDirection(),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // the reader's own choice carries a mark, not only a stronger bar
                            if (mine) {
                                Icon(
                                    AlohaIcons.Voted,
                                    stringResource(R.string.status_poll_your_vote),
                                    Modifier.size(VOTED_MARK),
                                )
                            }
                        }
                        Text(percent, style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { share },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            } else {
                val selected = index in chosen
                val choose = { onChosen(toggled(chosen, index, poll.multiple)) }
                val control = if (poll.multiple) {
                    Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { choose() })
                } else {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = choose)
                }
                Row(Modifier.fillMaxWidth().then(control), verticalAlignment = Alignment.CenterVertically) {
                    if (poll.multiple) {
                        Checkbox(
                            selected,
                            onCheckedChange = null,
                        )
                    } else {
                        RadioButton(selected, onClick = null)
                    }
                    Text(
                        option.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = AlohaSpacing.xs),
                    )
                }
            }
        }
        val voters = poll.votersCount ?: poll.votesCount
        val footer =
            pluralStringResource(
                R.plurals.status_poll_voters,
                voters,
                NumberFormat.getIntegerInstance().format(voters),
            ) +
                if (poll.expired) " · " + stringResource(R.string.status_poll_closed) else ""
        Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!poll.showsResults) {
            Button(onClick = {
                onVote(chosen)
            }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.status_poll_vote)) }
        }
    }
}

internal fun toggled(chosen: List<Int>, index: Int, multiple: Boolean): List<Int> = when {
    !multiple -> listOf(index)
    index in chosen -> chosen - index
    else -> (chosen + index).sorted()
}

private val VOTED_MARK = 16.dp
