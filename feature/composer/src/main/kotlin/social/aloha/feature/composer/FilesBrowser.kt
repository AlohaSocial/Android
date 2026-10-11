// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.nextcloud.NextcloudFile
import social.aloha.core.nextcloud.Preview
import social.aloha.core.sync.FilesUpload
import social.aloha.core.sync.FilesUploadState
import social.aloha.core.ui.fullDate

/** The writer's Nextcloud Files, which attach what is picked into the post in focus, up to its room. */
@Composable
internal fun FilesPicker(
    viewModel: ComposerViewModel,
    readerId: String,
    onTypePath: () -> Unit,
    onConnect: () -> Unit,
    onClose: () -> Unit,
) {
    val files = hiltViewModel<FilesViewModel, FilesViewModel.Factory>(key = "files:$readerId") { it.create(readerId) }
    val state by files.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(files) { files.onShown(viewModel.room) }
    FilesDialog(
        state,
        files,
        files::preview,
        viewModel.acceptedTypes,
        onAttach = { paths ->
            onClose()
            paths.forEach(viewModel.library::onNextcloudFile)
        },
        onTypePath = onTypePath,
        onConnect = onConnect,
        onClose = onClose,
    )
}

/** Opens [FilesBrowser] over the composer, as a page of its own that the system back goes up through. */
@Composable
internal fun FilesDialog(
    state: FilesUiState,
    actions: FilesActions,
    preview: suspend (NextcloudFile, Int) -> Preview?,
    uploadTypes: Array<String>,
    onAttach: (List<String>) -> Unit,
    onTypePath: () -> Unit,
    onConnect: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FilesBrowser(state, actions, preview, uploadTypes, onAttach, onTypePath, onConnect, onClose)
    }
}

/**
 * The writer's Nextcloud Files: a breadcrumb back up, folders to go through, and files to pick up to the
 * post's room, each one it cannot take shown greyed with why. A device file can be uploaded into the
 * folder shown. Back goes up a folder before it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesBrowser(
    state: FilesUiState,
    actions: FilesActions,
    preview: suspend (NextcloudFile, Int) -> Preview?,
    uploadTypes: Array<String>,
    onAttach: (List<String>) -> Unit,
    onTypePath: () -> Unit,
    onConnect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.files_title)
    val snackbars = remember { SnackbarHostState() }
    val failed = stringResource(R.string.files_upload_failed)
    LaunchedEffect(state.uploadFailed) {
        if (state.uploadFailed) {
            try {
                snackbars.showSnackbar(failed)
            } finally {
                actions.onUploadFailureShown()
            }
        }
    }
    BackHandler(enabled = state.folder.isNotEmpty(), onBack = actions::onUp)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.onUpload(it.toString()) }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(
                                R.plurals.files_picked,
                                state.selected.size,
                                state.selected.size,
                                state.room,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AlohaIcons.Close, stringResource(R.string.files_close)) }
                },
                actions = { MoreMenu(state.sort, actions::onSort, onTypePath) },
            )
        },
        bottomBar = {
            // below the list rather than over it, and wrapping rather than cut off at a large text size
            Surface(tonalElevation = BAR_ELEVATION) {
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(AlohaSpacing.s),
                    horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                ) {
                    if (state.canUpload) {
                        OutlinedButton(onClick = { pick.launch(uploadTypes) }) {
                            Icon(AlohaIcons.Upload, contentDescription = null)
                            Text(stringResource(R.string.files_upload), Modifier.padding(start = AlohaSpacing.s))
                        }
                    }
                    Button(onClick = { onAttach(state.selected) }, enabled = state.selected.isNotEmpty()) {
                        Text(stringResource(R.string.files_attach))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Breadcrumbs(state.folder, actions::onOpen)
            when (state.status) {
                FilesStatus.Ready -> Listing(state, actions, preview)

                FilesStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> Problem(state.status, actions, onConnect)
            }
        }
    }
}

/** The way back up: the root of the files, then each folder down to the one shown, which is not a button. */
@Composable
private fun Breadcrumbs(folder: String, onOpen: (String) -> Unit) {
    val segments = folder.split('/').filter(String::isNotEmpty)
    val crumbs = listOf("" to stringResource(R.string.files_root)) +
        segments.indices.map { index -> segments.take(index + 1).joinToString("/") to segments[index] }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AlohaSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (path, name) ->
            if (index > 0) {
                Text("›", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index == crumbs.lastIndex) {
                Text(
                    name,
                    Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                TextButton(onClick = { onOpen(path) }) { Text(name) }
            }
        }
    }
}

@Composable
private fun Listing(state: FilesUiState, actions: FilesActions, preview: suspend (NextcloudFile, Int) -> Preview?) {
    if (state.rows.isEmpty() && state.uploads.isEmpty()) {
        Text(
            stringResource(R.string.files_empty),
            Modifier.padding(AlohaSpacing.l),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.uploads, key = { "upload:${it.id}" }) { upload -> UploadRow(upload, actions::onCancelUpload) }
        items(state.rows, key = { it.file.path }) { row ->
            if (row.file.isFolder) {
                FolderRow(row.file, preview) { actions.onOpen(row.file.path) }
            } else {
                val selected = row.file.path in state.selected
                FileRowItem(row, selected, enabled = row.unavailable == null && (selected || !state.full), preview) {
                    actions.onToggle(row.file.path)
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: NextcloudFile, preview: suspend (NextcloudFile, Int) -> Preview?, onOpen: () -> Unit) {
    ListItem(
        leadingContent = { Thumbnail(folder, preview) },
        headlineContent = { Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = folder.modified?.let { { Text(fullDate(it)) } },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.files_open), onClick = onOpen),
    )
}

@Composable
private fun FileRowItem(
    row: FileRow,
    selected: Boolean,
    enabled: Boolean,
    preview: suspend (NextcloudFile, Int) -> Preview?,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val details = row.unavailable?.let { reason(it) }
        ?: listOfNotNull(Formatter.formatShortFileSize(context, row.file.size), row.file.modified?.let(::fullDate))
            .joinToString(" · ")
    ListItem(
        leadingContent = { Thumbnail(row.file, preview) },
        headlineContent = { Text(row.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details) },
        trailingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
    )
}

@Composable
private fun reason(unavailable: Unavailable): String = when (unavailable) {
    Unavailable.Encrypted -> stringResource(R.string.files_encrypted)

    Unavailable.Unreadable -> stringResource(R.string.files_unreadable)

    Unavailable.Type -> stringResource(R.string.files_type)

    is Unavailable.TooLarge -> stringResource(
        R.string.files_too_large,
        stringResource(R.string.composer_size_mb, Attachments.megabytes(unavailable.limitBytes)),
    )
}

@Composable
private fun UploadRow(upload: FilesUpload, onCancel: (FilesUpload) -> Unit) {
    val sending = upload.state as? FilesUploadState.Sending
    ListItem(
        leadingContent = {
            Box(Modifier.size(THUMB), contentAlignment = Alignment.Center) { Icon(AlohaIcons.Upload, null) }
        },
        headlineContent = { Text(upload.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
                Text(stringResource(if (sending == null) R.string.files_upload_waiting else R.string.files_uploading))
                val fraction = sending?.fraction
                if (fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { onCancel(upload) }) {
                Icon(AlohaIcons.Close, stringResource(R.string.files_upload_cancel, upload.fileName))
            }
        },
    )
}

/** The server's preview of [file] where it has one, its kind's icon until then and otherwise. */
@Composable
private fun Thumbnail(file: NextcloudFile, preview: suspend (NextcloudFile, Int) -> Preview?) {
    val edge = with(LocalDensity.current) { THUMB.roundToPx() }
    val source by produceState<Preview?>(null, file.path, file.etag) {
        value = if (file.hasPreview) preview(file, edge) else null
    }
    val context = LocalContext.current
    Box(Modifier.size(THUMB).clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        Icon(if (file.isFolder) AlohaIcons.Folder else AlohaIcons.File, contentDescription = null)
        source?.let {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(it.url)
                    .httpHeaders(NetworkHeaders.Builder().set("Authorization", it.authorization).build())
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** What went wrong with the folder, and the one thing that can be done about it. */
@Composable
private fun Problem(status: FilesStatus, actions: FilesActions, onConnect: () -> Unit) {
    val (text, action, onAction) = when (status) {
        FilesStatus.SignedOut -> Triple(R.string.files_signed_out, R.string.files_connect, onConnect)
        FilesStatus.Gone -> Triple(R.string.files_gone, R.string.files_to_root) { actions.onOpen("") }
        FilesStatus.Forbidden -> Triple(R.string.files_forbidden, R.string.files_to_root) { actions.onOpen("") }
        FilesStatus.Offline -> Triple(R.string.files_offline, R.string.files_retry, actions::onRetry)
        else -> Triple(R.string.files_failed, R.string.files_retry, actions::onRetry)
    }
    Column(
        Modifier.fillMaxSize().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}

/** How the files are ordered, and typing a path instead; one menu, so the title keeps its room. */
@Composable
private fun MoreMenu(sort: FileSort, onSort: (FileSort) -> Unit, onTypePath: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.files_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.files_sort),
                Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FileSort.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(choice.label())) },
                    leadingIcon = { RadioButton(selected = choice == sort, onClick = null) },
                    onClick = {
                        open = false
                        onSort(choice)
                    },
                    modifier = Modifier.semantics { selected = choice == sort },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_type_path)) },
                onClick = {
                    open = false
                    onTypePath()
                },
            )
        }
    }
}

private fun FileSort.label(): Int = when (this) {
    FileSort.Name -> R.string.files_sort_name
    FileSort.Newest -> R.string.files_sort_newest
    FileSort.Largest -> R.string.files_sort_largest
}

private val THUMB = 48.dp
private val BAR_ELEVATION = 3.dp
