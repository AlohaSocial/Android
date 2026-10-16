// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/** [text] as a QR code's modules, with no quiet zone of its own: the code's padding gives it one. */
public fun qrOf(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

/**
 * [text] as a QR code's dark modules, each a square of whole pixels, centred in [modifier]'s box, filled as
 * one shape: squares drawn one by one leave hairline seams where their edges meet. The caller gives it a
 * light background and padding, its quiet zone, as scanners read dark on white best.
 */
@Composable
public fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrOf(text) }
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
