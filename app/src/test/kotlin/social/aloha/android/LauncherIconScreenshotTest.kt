// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon's layers as launchers mask them: the mark on its sand in a circle and a squircle,
 * and the one-colour layer a themed icon tints. Kept as a picture, so a change to either layer shows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LauncherIconScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the launcher icon, as launchers mask it and as a themed icon`() {
        compose.setContent {
            Row(
                Modifier.background(Color.DarkGray).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Layer(CircleShape, colorResource(R.color.aloha_sand), R.drawable.ic_launcher_foreground, null)
                Layer(
                    RoundedCornerShape(32.dp),
                    colorResource(R.color.aloha_sand),
                    R.drawable.ic_launcher_foreground,
                    null,
                )
                // a themed icon: the system's pale tint on its dark container
                Layer(
                    CircleShape,
                    Color(0xFF3A4A50),
                    R.drawable.ic_launcher_monochrome,
                    ColorFilter.tint(Color(0xFFCFE6EE)),
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/launcher-icon.png")
    }

    @Composable
    private fun Layer(shape: Shape, background: Color, drawable: Int, tint: ColorFilter?) {
        Box(Modifier.size(108.dp).clip(shape).background(background)) {
            Image(painterResource(drawable), contentDescription = null, Modifier.size(108.dp), colorFilter = tint)
        }
    }
}
