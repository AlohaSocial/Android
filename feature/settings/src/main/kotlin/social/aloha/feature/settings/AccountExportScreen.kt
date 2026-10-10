// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.MigrationListKind
import social.aloha.core.navigation.AccountExportKey
import social.aloha.core.ui.readingColumn

private val ITEMS = listOf(ExportItem()) + MigrationListKind.entries.map { ExportItem(it) }

/** The account, whole or a list at a time, saved where the reader chooses. */
@Composable
public fun AccountExportRoute(
    key: AccountExportKey,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AccountExportViewModel, AccountExportViewModel.Factory>(key = key.toString()) {
        it.create(key.readerId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // the item being saved, while the reader picks where; it outlives the picker if the activity goes
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = chosen@{ uri: Uri? ->
        // a result whose item was lost with the activity writes nothing, rather than a zip into a .csv
        val key = picking ?: return@chosen
        picking = null
        val item = ITEMS.firstOrNull { (it.list?.wire ?: ARCHIVE) == key }
        if (uri != null && item != null) viewModel.onExport(item, uri.toString())
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP), chosen)
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV), chosen)
    AccountExportScreen(
        state,
        viewModel,
        onSave = { item ->
            picking = item.list?.wire ?: ARCHIVE
            val name = item.fileName(state.handle, LocalDate.now())
            if (item.isArchive) saveZip.launch(name) else saveCsv.launch(name)
        },
        onConnect,
        onBack,
        modifier,
    )
}

@Composable
internal fun AccountExportScreen(
    state: AccountExportUiState,
    actions: AccountExportActions,
    onSave: (ExportItem) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val saved = stringResource(R.string.export_saved)
    val failed = stringResource(R.string.export_failed)
    LaunchedEffect(state.saved, state.failed) {
        if (state.saved != null || state.failed) {
            actions.onResultShown()
            snackbars.showSnackbar(if (state.failed) failed else saved)
        }
    }
    NextcloudPage(
        stringResource(R.string.export_title),
        state.status,
        onBack,
        onConnect,
        actions::onRetry,
        modifier,
        snackbars,
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().readingColumn()) {
            item(key = "about") {
                Text(
                    stringResource(R.string.export_about),
                    Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.l),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ITEMS, key = { it.list?.wire ?: ARCHIVE }) { item ->
                ExportRow(item, working = state.working == item, enabled = state.working == null) { onSave(item) }
            }
        }
    }
}

@Composable
private fun ExportRow(item: ExportItem, working: Boolean, enabled: Boolean, onSave: () -> Unit) {
    val title = stringResource(item.title())
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(stringResource(if (item.isArchive) R.string.export_archive_about else R.string.export_list_about))
        },
        trailingContent = {
            if (working) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                val label = stringResource(R.string.export_save_for, title)
                TextButton(
                    onClick = onSave,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Text(stringResource(R.string.export_save))
                }
            }
        },
    )
}

private fun ExportItem.title(): Int = when (list) {
    null -> R.string.export_archive
    MigrationListKind.Following -> R.string.export_following
    MigrationListKind.Followers -> R.string.export_followers
    MigrationListKind.Blocks -> R.string.export_blocks
    MigrationListKind.Mutes -> R.string.export_mutes
    MigrationListKind.Lists -> R.string.export_lists
}

private const val ARCHIVE = "archive"
private const val ZIP = "application/zip"
private const val CSV = "text/csv"
