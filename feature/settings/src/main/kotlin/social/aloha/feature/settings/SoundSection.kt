// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.ReadingStyle
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** The app makes no sound of its own: notifications sound as Android's settings say, which this opens. */
internal object SoundSection : SettingsSection {
    override val key: String = "sound"
    override val order: Int = 350
    override val title: Int = R.string.sound_title
    override val icon: ImageVector = AlohaIcons.Unmuted

    @Composable
    override fun Content() {
        val viewModel: ReadingViewModel = hiltViewModel()
        val style by viewModel.style.collectAsStateWithLifecycle()
        val context = LocalContext.current
        SoundContent(style, viewModel::change) { openNotificationSettings(context) }
    }
}

@Composable
internal fun SoundContent(
    style: ReadingStyle,
    onChange: ((ReadingStyle) -> ReadingStyle) -> Unit,
    onNotificationSounds: () -> Unit,
) {
    Column {
        SwitchRow(
            stringResource(R.string.sound_haptics),
            style.haptics,
            { on -> onChange { it.copy(haptics = on) } },
            stringResource(R.string.sound_haptics_summary),
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onNotificationSounds),
            headlineContent = { Text(stringResource(R.string.sound_notifications)) },
            supportingContent = { Text(stringResource(R.string.sound_notifications_summary)) },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // a device without the page: there is nowhere to send the reader
    }
}
