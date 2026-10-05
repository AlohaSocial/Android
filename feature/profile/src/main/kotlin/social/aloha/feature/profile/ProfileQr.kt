// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.shareLink

/**
 * The reader's own profile as a QR code, for someone beside them to scan and follow: dark on white whatever
 * the theme, as scanners read best, with the handle under it and a way to share the link instead.
 */
@Composable
internal fun ProfileQrDialog(url: String, handle: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val matrix = remember(url) { qrOf(url) }
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
                    matrix,
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

/** [text] as a QR code's modules, with no quiet zone of its own: the code's padding gives it one. */
internal fun qrOf(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

/**
 * [matrix]'s dark modules, each a square of whole pixels, centred in [modifier]'s box, filled as one
 * shape: squares drawn one by one leave hairline seams where their edges meet.
 */
@Composable
private fun QrCode(matrix: BitMatrix, modifier: Modifier) {
    Canvas(modifier) {
        val module = floor(size.minDimension / matrix.width)
        val left = floor((size.width - module * matrix.width) / 2)
        val top = floor((size.height - module * matrix.height) / 2)
        val dark = Path()
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) dark.addRect(Rect(Offset(left + x * module, top + y * module), Size(module, module)))
            }
        }
        drawPath(dark, Color.Black)
    }
}
