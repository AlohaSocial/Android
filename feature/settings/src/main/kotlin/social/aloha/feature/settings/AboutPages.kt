// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.openInBrowser

/** A page over Settings, full screen, with a way back: the terms, the privacy statement, the licences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutPage(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.semantics { paneTitle = title },
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(AlohaIcons.Back, stringResource(R.string.settings_page_back))
                        }
                    },
                    actions = actions,
                )
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

/** A page of text, at reading width. */
@Composable
internal fun TextPage(title: String, onClose: () -> Unit, text: @Composable () -> Unit) {
    AboutPage(title, onClose) { modifier ->
        Box(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = READING).fillMaxWidth().padding(AlohaSpacing.l)) { text() }
        }
    }
}

/**
 * What the app does with what it learns about the reader, which is to keep it on the device; with
 * [intelligence], the build's own paragraph on its on-device features and who learns of them.
 */
@Composable
internal fun PrivacyStatement(intelligence: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l)) {
        listOf(R.string.privacy_nothing, R.string.privacy_servers, R.string.privacy_notifications)
            .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        intelligence?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.privacy_posts), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Every library the app is built from, its version and licence; one opens its own page. */
@Composable
internal fun LicencesPage(onClose: () -> Unit) {
    val context = LocalContext.current
    // gathered at build time into the app's resources, so the list is always the one this build ships
    val libraries = remember(context) {
        Libs.Builder().withContext(context).build().libraries.sortedBy { it.name.lowercase() }
    }
    AboutPage(stringResource(R.string.settings_licences), onClose) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(libraries, key = { it.uniqueId }) { library ->
                val page = library.website ?: library.licenses.firstOrNull()?.url
                ListItem(
                    modifier = Modifier.clickable(enabled = page != null, role = Role.Button) {
                        page?.let { openInBrowser(context, it) }
                    },
                    headlineContent = { Text(library.name) },
                    supportingContent = {
                        Text(
                            listOfNotNull(library.artifactVersion, library.licenses.joinToString { it.name })
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private val READING = 640.dp
