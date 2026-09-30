// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.openInBrowser

/** The app itself: its version and where its source lives. Licences and contact follow with the rest. */
internal object AboutSection : SettingsSection {
    private const val SOURCE = "https://github.com/AlohaSocial/Android"

    override val key: String = "about"
    override val order: Int = 1_000
    override val title: Int = R.string.settings_about
    override val icon: ImageVector = AlohaIcons.About

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val version = remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }
        Column {
            ListItem(headlineContent = { Text(stringResource(R.string.settings_version, version)) })
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { openInBrowser(context, SOURCE) },
                headlineContent = { Text(stringResource(R.string.settings_source)) },
                supportingContent = { Text(SOURCE) },
            )
        }
    }
}
