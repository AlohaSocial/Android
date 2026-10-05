// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileQrTest {
    @Test
    fun `the code reads back as the profile's address`() {
        val url = "https://cloud.example/apps/social/@alice"
        val matrix = qrOf(url)
        // drawn at four pixels a module with a quiet zone, as the dialog draws it
        val scale = 4
        val quiet = 4 * scale
        val side = matrix.width * scale + 2 * quiet
        val pixels = IntArray(side * side) { at ->
            val x = (at % side - quiet).floorDiv(scale)
            val y = (at / side - quiet).floorDiv(scale)
            val inside = x in 0 until matrix.width && y in 0 until matrix.height
            if (inside && matrix[x, y]) BLACK else WHITE
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
