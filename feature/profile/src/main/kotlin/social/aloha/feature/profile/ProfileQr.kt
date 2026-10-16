// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.QrCode
import social.aloha.core.ui.shareLink

/**
 * The reader's own profile as a QR code, for someone beside them to scan and follow: dark on white whatever
 * the theme, as scanners read best, with the handle under it and a way to share the link instead.
 */
@Composable
internal fun ProfileQrDialog(url: String, handle: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val described = stringResource(R.string.profile_qr_described, handle)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_qr_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
            ) {
                QrCode(
                    url,
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                        .background(Color.White).padding(AlohaSpacing.m)
                        .semantics { contentDescription = described },
                )
                Text(handle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = { shareLink(context, url) }) { Text(stringResource(R.string.profile_qr_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_qr_close)) } },
    )
}
