// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.rememberReducedMotion

/**
 * The sound playing, docked above the navigation wherever the reader is: what it is and who posted it,
 * play and pause, and a way to stop it. [onOpen] goes to its post. Nothing at all while nothing plays.
 */
@Composable
public fun MiniPlayer(onOpen: (statusId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<MiniPlayerViewModel>()
    val now by viewModel.nowPlaying.collectAsStateWithLifecycle()
    MiniPlayer(now, onOpen, viewModel::onToggle, viewModel::onStop, modifier)
}

@Composable
internal fun MiniPlayer(
    now: NowPlaying?,
    onOpen: (String) -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        now != null,
        modifier,
        enter = if (reduced) EnterTransition.None else fadeIn() + expandIn(),
        exit = if (reduced) ExitTransition.None else shrinkOut() + fadeOut(),
    ) {
        val playing = now ?: return@AnimatedVisibility
        Surface(tonalElevation = ELEVATION, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.clickable { onOpen(playing.item.statusId) }.padding(horizontal = AlohaSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    playing.item.artwork,
                    Modifier.padding(AlohaSpacing.xs).size(ARTWORK).clip(RoundedCornerShape(CORNER)),
                )
                Column(Modifier.weight(1f).padding(horizontal = AlohaSpacing.s)) {
                    Text(
                        playing.item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playing.item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggle) {
                    if (playing.playing) {
                        Icon(AlohaIcons.Pause, stringResource(R.string.audio_pause, playing.item.title))
                    } else {
                        Icon(AlohaIcons.Play, stringResource(R.string.audio_resume, playing.item.title))
                    }
                }
                IconButton(onClick = onStop) { Icon(AlohaIcons.Close, stringResource(R.string.audio_stop)) }
            }
        }
    }
}

private val ARTWORK = 40.dp
private val CORNER = 6.dp
private val ELEVATION = 3.dp
