// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.ui.AppTerms
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.openInBrowser

/** The app itself: its version, terms, privacy, licences, and where its source and issue tracker live. */
internal object AboutSection : SettingsSection {
    private const val SOURCE = "https://github.com/AlohaSocial/Android"
    private const val ISSUES = "$SOURCE/issues"

    override val key: String = "about"
    override val order: Int = 1_000
    override val title: Int = R.string.settings_about
    override val icon: ImageVector = AlohaIcons.About

    /** The pages About opens over Settings. */
    private enum class Page { Terms, Privacy, Licences, Diagnostics }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val version = remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }
        var page by rememberSaveable { mutableStateOf<Page?>(null) }
        Column {
            ListItem(headlineContent = { Text(stringResource(R.string.settings_version, version)) })
            PageRow(R.string.settings_terms) { page = Page.Terms }
            PageRow(R.string.settings_privacy) { page = Page.Privacy }
            PageRow(R.string.settings_licences) { page = Page.Licences }
            LinkRow(R.string.settings_source, SOURCE) { openInBrowser(context, SOURCE) }
            LinkRow(R.string.settings_issues, ISSUES) { openInBrowser(context, ISSUES) }
            PageRow(R.string.settings_diagnostics) { page = Page.Diagnostics }
        }
        val close = { page = null }
        when (page) {
            Page.Terms -> TextPage(stringResource(R.string.settings_terms), close) { AppTerms() }

            Page.Privacy -> {
                val notice = hiltViewModel<IntelligenceViewModel>().privacy?.let { stringResource(it) }
                TextPage(stringResource(R.string.settings_privacy), close) { PrivacyStatement(notice) }
            }

            Page.Licences -> LicencesPage(close)

            Page.Diagnostics -> DiagnosticsPage(close)

            null -> Unit
        }
    }

    @Composable
    private fun PageRow(title: Int, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            headlineContent = { Text(stringResource(title)) },
        )
    }

    @Composable
    private fun LinkRow(title: Int, url: String, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { Text(url) },
        )
    }
}
