// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.icu.text.DateFormat
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import java.time.Instant
import java.util.Date
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.StackedAvatars

/**
 * The handle, which a long press copies, and its server as a chip that explains what a server is. On
 * the fediverse the server is half of who someone is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HandleRow(handle: String) {
    val context = LocalContext.current
    var explaining by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.profile_handle_copied)
    val server = handle.substringAfterLast('@', "").takeIf { handle.count { it == '@' } > 1 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        Text(
            // the server shows as the chip beside it; a copy takes the whole handle
            if (server != null) handle.substringBeforeLast('@') else handle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.combinedClickable(
                onClickLabel = null,
                onLongClickLabel = stringResource(R.string.profile_handle_copy),
                onLongClick = {
                    context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(handle, handle))
                    // Android 13 and later confirm a copy themselves
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
                onClick = {},
            ),
        )
        server?.let { SuggestionChip(onClick = { explaining = true }, label = { Text(it) }) }
    }
    if (explaining && server != null) ServerSheet(server) { explaining = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSheet(server: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.m, bottom = AlohaSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            Text(
                server,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.profile_server_explainer), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** "Followed by A, B and 2 others", with up to three of their faces; a tap lists them all. */
@Composable
internal fun FamiliarFollowers(people: List<Familiar>, onProfile: (String) -> Unit) {
    if (people.isEmpty()) return
    var listing by remember { mutableStateOf(false) }
    val names = people.take(NAMED).map { it.name }
    val others = people.size - names.size
    val text = if (others > 0) {
        pluralStringResource(R.plurals.profile_familiar_others, others, names.joinToString(), others)
    } else {
        stringResource(R.string.profile_familiar, names.joinToString())
    }
    Row(
        Modifier.fillMaxWidth().clickable { listing = true }.padding(vertical = AlohaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        StackedAvatars(people.take(FACES).map { it.avatarUrl }, FACE)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (listing) FamiliarSheet(people, onProfile) { listing = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamiliarSheet(people: List<Familiar>, onProfile: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.profile_familiar_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        people.forEach { person ->
            ListItem(
                modifier = Modifier.clickable {
                    onDismiss()
                    onProfile(person.id)
                },
                leadingContent = { Avatar(person.avatarUrl, FAMILIAR_AVATAR) },
                headlineContent = { Text(person.name) },
            )
        }
    }
}

/** When the account joined, as a month and a year. */
@Composable
internal fun Joined(at: Instant) {
    val month = remember(at) { DateFormat.getInstanceForSkeleton("MMMMyyyy").format(Date.from(at)) }
    Text(
        stringResource(R.string.profile_joined, month),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** An account that moved, with the way to where it went, or one kept in memory of someone who died. */
@Composable
internal fun Notices(header: ProfileHeader, onProfile: (String) -> Unit) {
    header.movedTo?.let { moved ->
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.fillMaxWidth().padding(start = AlohaSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.profile_moved, moved.handle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onProfile(moved.id) }) { Text(stringResource(R.string.profile_moved_open)) }
            }
        }
    }
    if (header.memorial) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Text(
                stringResource(R.string.profile_memorial),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
            )
        }
    }
}

/** The reader's private note, written in place; it is saved when the field loses focus. */
@Composable
internal fun NoteField(saved: String?, onSave: (RelationshipChange.Note) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.profile_note_label)) },
        supportingText = { Text(stringResource(R.string.profile_note_private)) },
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (focused && !it.isFocused && text.trim() != saved.orEmpty()) onSave(RelationshipChange.Note(text.trim()))
            focused = it.isFocused
        },
    )
}

/** The profile's shape while it loads, with the handle where it is already known. */
@Composable
internal fun ProfileSkeleton(handle: String?) {
    val loading = stringResource(R.string.profile_loading)
    val shade = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = loading }) {
        Box {
            Box(Modifier.fillMaxWidth().aspectRatio(SKELETON_BANNER).background(shade))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = AlohaSpacing.m, y = SKELETON_AVATAR / 2)
                    .size(SKELETON_AVATAR)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            )
        }
        Column(
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.m, top = SKELETON_AVATAR / 2 + AlohaSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Box(Modifier.width(SKELETON_NAME).height(SKELETON_LINE).background(shade, MaterialTheme.shapes.extraSmall))
            handle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().height(SKELETON_LINE).background(shade, MaterialTheme.shapes.extraSmall))
        }
    }
}

private const val NAMED = 2
private const val FACES = 3
private const val SKELETON_BANNER = 3f
private val FACE = 28.dp
private val FAMILIAR_AVATAR = 40.dp
private val SKELETON_AVATAR = 80.dp
private val SKELETON_NAME = 160.dp
private val SKELETON_LINE = 16.dp
