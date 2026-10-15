// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch's sign-in on a large round watch, in each state, at 200 % font, and on a square watch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
class SignInScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: SignInUiState) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            MaterialTheme {
                // no clock: it would put the minute the test ran into every image
                AppScaffold(timeText = {}) { SignInScreen(state, onEnterServer = {}, onUsePhone = {}, onSignOut = {}) }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun signedOut() = capture("wear-sign-in", SignInUiState.SignedOut())

    @Test
    fun phoneInReach() = capture("wear-sign-in-phone", SignInUiState.SignedOut(phoneInReach = true))

    @Test
    fun declined() = capture("wear-sign-in-declined", SignInUiState.SignedOut(WatchSignInProblem.Refused))

    @Test
    fun waiting() = capture("wear-sign-in-waiting", SignInUiState.Waiting("cloud.example.org"))

    @Test
    fun signedIn() = capture("wear-signed-in", SignInUiState.SignedIn("alice@cloud.example.org"))

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("wear-sign-in-font200", SignInUiState.SignedOut(phoneInReach = true))

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSSquare)
    fun square() = capture("wear-sign-in-square", SignInUiState.SignedOut(phoneInReach = true))
}
