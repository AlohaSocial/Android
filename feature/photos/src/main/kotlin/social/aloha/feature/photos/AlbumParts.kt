// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Status
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.MediaImage
import social.aloha.core.ui.rememberBlurHashPainter

/**
 * Asks for an album's name, and for a new one what it holds too. [confirm] stays off until there is a
 * name, which is the one thing an album needs.
 */
@Composable
internal fun AlbumNameDialog(
    title: String,
    initial: String,
    askDescription: Boolean,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                OutlinedTextField(
                    name,
                    onValueChange = { name = it.take(MAXIMUM_TITLE) },
                    label = { Text(stringResource(R.string.albums_name)) },
                    singleLine = true,
                )
                if (askDescription) {
                    OutlinedTextField(
                        description,
                        onValueChange = { description = it.take(MAXIMUM_DESCRIPTION) },
                        label = { Text(stringResource(R.string.albums_description)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(name, description)
                },
                enabled = name.isNotBlank(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.albums_cancel)) } },
    )
}

/** Under a list: a spinner while it loads, a retry when it could not, or what an empty one says. */
@Composable
internal fun ListFooter(loading: Boolean, failed: Boolean, empty: Boolean, emptyText: String, onRetry: () -> Unit) {
    when {
        failed -> Column(
            Modifier.fillMaxWidth().padding(AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.albums_failed), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text(stringResource(R.string.albums_retry)) }
        }

        loading -> ListProgress()

        empty -> Box(Modifier.fillMaxWidth().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Pixelfed's limits on an album's title and description
private const val MAXIMUM_TITLE = 50
private const val MAXIMUM_DESCRIPTION = 500

/**
 * A post as a square of its first picture, or of its blur alone where it is marked sensitive. A tap
 * opens it; with [onRemove], a long press or the screen reader's action takes it out of the album it is in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoSquare(post: Status, onOpen: (String) -> Unit, onRemove: ((String) -> Unit)?) {
    val shown = post.displayed
    val first = shown.mediaAttachments.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    val label = listOfNotNull(
        stringResource(R.string.album_post, shown.account.bestDisplayName),
        first?.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.album_post_no_alt),
        stringResource(R.string.album_post_sensitive).takeIf { shown.sensitive },
    ).joinToString(", ")
    val open = stringResource(R.string.album_open)
    val remove = stringResource(R.string.album_remove)
    Box(
        Modifier.aspectRatio(1f).padding(GAP)
            .combinedClickable(onClick = { onOpen(post.id) }, onLongClick = onRemove?.let { { menu = true } })
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(open) {
                    onOpen(post.id)
                    true
                }
                if (onRemove != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(remove) {
                            onRemove(post.id)
                            true
                        },
                    )
                }
            },
    ) {
        when {
            first == null -> Unit

            shown.sensitive && !LocalSensitiveMediaPolicy.current.allowsAutomaticReveal -> {
                val blur = rememberBlurHashPainter(first.blurhash)
                val fill = Modifier.fillMaxSize()
                blur?.let { Image(it, null, fill, contentScale = ContentScale.Crop) }
                    ?: Box(fill.background(MaterialTheme.colorScheme.surfaceContainerHigh))
                Icon(AlohaIcons.Sensitive, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            }

            else -> MediaImage(first, contentDescription = null, modifier = Modifier.fillMaxSize(), fitToAspect = false)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(remove) },
                leadingIcon = { Icon(AlohaIcons.Remove, contentDescription = null) },
                onClick = {
                    menu = false
                    onRemove?.invoke(post.id)
                },
            )
        }
    }
}

/** The side of a square in the albums' and Explore's grids: three across on a phone. */
internal val CELL = 112.dp
private val GAP = 1.dp
