// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.readingColumn

/** The list of sections and [destinations]; on a wide screen it stays beside the one opened. */
@Composable
public fun SettingsRoute(
    onBack: () -> Unit,
    onSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<SettingsDestination> = emptyList(),
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    SettingsScreen(viewModel.sections, onBack, onSection, modifier, destinations)
}

/** One section, opened from the list. */
@Composable
public fun SettingsSectionRoute(key: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    viewModel.section(key)?.let { SectionScreen(it, onBack, modifier) }
}

/** The pane beside the list before a section is opened. */
@Composable
public fun SettingsPlaceholder() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.settings_choose),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    sections: List<SettingsSection>,
    onBack: () -> Unit,
    onSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<SettingsDestination> = emptyList(),
) {
    val title = stringResource(R.string.settings_title)
    // one list, sections and destinations alike in order
    val rows = (
        sections.map { Row(it.key, it.order, it.title, it.icon) { onSection(it.key) } } +
            destinations.map { Row(it.key, it.order, it.title, it.icon, it.onOpen) }
        ).sortedBy { it.order }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).readingColumn()) {
            items(rows, key = { it.key }) { row ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = row.onOpen),
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    headlineContent = { Text(stringResource(row.title)) },
                )
            }
        }
    }
}

/** A row of the settings list. */
private class Row(val key: String, val order: Int, val title: Int, val icon: ImageVector, val onOpen: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionScreen(section: SettingsSection, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(section.title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).readingColumn().verticalScroll(rememberScrollState())) { section.Content() }
    }
}
