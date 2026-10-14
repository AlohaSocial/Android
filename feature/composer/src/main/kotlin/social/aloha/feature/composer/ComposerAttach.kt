// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/** One way to attach something: what the tile shows, and what a tap does. */
private data class Source(val icon: ImageVector, val label: String, val onPick: () -> Unit)

/**
 * The one button for everything that attaches, while the post has room for more: it opens a sheet of
 * large, named tiles rather than a row of bare icons.
 */
@Composable
internal fun AttachButton(state: ComposerUiState, actions: ComposerActions) {
    var open by remember { mutableStateOf(false) }
    val room = state.poll == null && state.attachments.any { it.size < state.maxAttachments }
    IconButton(onClick = { open = true }, enabled = room && !state.posting) {
        Icon(AlohaIcons.AttachFile, stringResource(R.string.composer_attach))
    }
    if (open) AttachSheet(sources(state, actions), onDismiss = { open = false })
}

/** What attaches here: the device's own sources, the camera, and what the server offers of its own. */
@Composable
private fun sources(state: ComposerUiState, actions: ComposerActions): List<Source> = listOf(
    Source(AlohaIcons.AddMedia, stringResource(R.string.composer_attach_gallery), actions::onPickMedia),
    Source(AlohaIcons.File, stringResource(R.string.composer_attach_file), actions::onPickFiles),
    Source(AlohaIcons.Camera, stringResource(R.string.composer_attach_photo)) { actions.onCapture(Capture.Photo) },
    Source(AlohaIcons.RecordVideo, stringResource(R.string.composer_attach_video)) {
        actions.onCapture(Capture.Video)
    },
) + Capture.SHORT_SECONDS.map { seconds ->
    Source(AlohaIcons.Shorts, pluralStringResource(R.plurals.composer_attach_short, seconds, seconds)) {
        actions.onCapture(Capture.Short(seconds))
    }
} + listOfNotNull(
    Source(AlohaIcons.Paste, stringResource(R.string.composer_attach_paste), actions::onPaste),
    Source(AlohaIcons.Gif, stringResource(R.string.composer_gifs), actions::onGifs).takeIf { state.gifLibrary },
    Source(AlohaIcons.Nextcloud, stringResource(R.string.composer_attach_nextcloud), actions::onNextcloudFile)
        .takeIf { state.nextcloudFiles },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(sources: List<Source>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        // at a large font, or on a phone on its side, the tiles run past the sheet: they scroll
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AlohaSpacing.m)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.composer_attach),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = AlohaSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
            ) {
                sources.forEach { source ->
                    Tile(source) {
                        // the sheet slides away, then the source opens; a composer gone meanwhile opens nothing
                        scope.launch {
                            sheet.hide()
                            onDismiss()
                            source.onPick()
                        }
                    }
                }
            }
        }
    }
}

/**
 * A large icon on a tinted square, named beneath it; the whole tile is the one thing to tap. It widens with
 * the font, so a larger text breaks between words, never inside one.
 */
@Composable
private fun Tile(source: Source, onClick: () -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(
        Modifier
            .width(TILE * scale)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AlohaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(source.icon, contentDescription = null, Modifier.padding(AlohaSpacing.m).size(ICON))
        }
        Text(source.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

private val TILE = 80.dp
private val ICON = 28.dp
