// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.GifEntry
import social.aloha.core.ui.ListProgress

/**
 * The server's own GIF library, searchable, a page at a time; a tap attaches one. Each GIF is named
 * by its title, which also becomes its description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GifSheet(
    gifs: GifsUi,
    onQuery: (String) -> Unit,
    onMore: () -> Unit,
    onPick: (GifEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val grid = rememberLazyGridState()
    val more by rememberUpdatedState(onMore)
    LaunchedEffect(Unit) { onQuery(gifs.query) }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { last -> last >= grid.layoutInfo.totalItemsCount - NEAR_END }
            .collect { more() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                stringResource(R.string.composer_gifs_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = gifs.query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.composer_gifs_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(GIF_TILE),
                horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                modifier = Modifier.fillMaxWidth().heightIn(max = SHEET),
            ) {
                items(gifs.gifs, key = { it.slug }) { gif ->
                    AsyncImage(
                        gif.previewUrl ?: gif.url,
                        contentDescription = gif.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            // a tile the picture has not reached yet still shows where it will be
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.Button) { onPick(gif) },
                    )
                }
            }
            when {
                gifs.loading -> ListProgress()
                gifs.failed -> Text(stringResource(R.string.composer_gifs_failed))
                gifs.gifs.isEmpty() -> Text(stringResource(R.string.composer_gifs_none))
            }
            gifs.attribution?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A file from the writer's own Nextcloud, by its path from their files' root: the server copies it
 * in, so nothing travels to the phone and back. The paths attached before are offered.
 */
@Composable
internal fun NextcloudFileDialog(recent: List<String>, onAttach: (String) -> Unit, onDismiss: () -> Unit) {
    var path by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composer_file_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.composer_file_path)) },
                    placeholder = { Text(stringResource(R.string.composer_file_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recent.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                    ) {
                        recent.forEach { AssistChip(onClick = { path = it }, label = { Text(it, maxLines = 1) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAttach(path) }, enabled = path.isNotBlank()) {
                Text(stringResource(R.string.composer_file_attach))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_keep_editing)) } },
    )
}

private const val NEAR_END = 8
private val GIF_TILE = 104.dp
private val SHEET = 420.dp
