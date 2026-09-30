// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.sync.notificationsAllowed

/**
 * Asks to show notifications where they would help, on the notifications tab, with why above the button.
 * The first tap asks; once the person was asked, the system no longer shows its dialog, so a tap opens
 * the app's notification settings instead. Gone as soon as notifications are allowed.
 */
@Composable
internal fun PermissionBanner(asked: Boolean, onAsked: () -> Unit, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(context) {
        granted = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    PermissionCard(modifier) {
        if (asked) {
            openSettings(context)
        } else {
            onAsked()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
internal fun PermissionCard(modifier: Modifier = Modifier, onTurnOn: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            Text(
                stringResource(R.string.notifications_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onTurnOn) { Text(stringResource(R.string.notifications_permission_turn_on)) }
        }
    }
}

/** The app's notification settings, where channels and the permission itself are. */
internal fun openSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // a device without the screen: the permission can still be given from the app's info page
    }
}
