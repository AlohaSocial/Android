// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.TriedAddress
import social.aloha.core.data.TriedBecause
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings

/** Every sign-in step on a phone, the card also dark, on a tablet, at 200 % font and right to left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SignInScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val card = InstanceCard(
        title = "Nextcloud Social",
        domain = "cloud.example",
        description = "a safe home for your data",
        userCount = 10_188,
        rules = listOf("Be kind.", "No spam, no scams."),
        isNextcloudSocial = true,
    )

    @Test
    fun enterServer() = capture("signin-enter-server", SignInUiState(server = "cloud.example"))

    @Test
    fun invalidServer() = capture(
        "signin-invalid",
        SignInUiState(server = "not a host", step = SignInStep.EnterServerWith(AddressProblem.NotAnAddress)),
    )

    @Test
    fun insecureServer() = capture(
        "signin-not-secure",
        SignInUiState(server = "http://cloud.example", step = SignInStep.EnterServerWith(AddressProblem.NotSecure)),
    )

    @Test
    fun probing() = capture("signin-probing", SignInUiState(server = "cloud.example", step = SignInStep.Probing))

    @Test
    fun instanceCard() = capture("signin-card", SignInUiState(step = SignInStep.Instance(card)))

    @Test
    fun instanceCardDark() =
        capture("signin-card-dark", SignInUiState(step = SignInStep.Instance(card)), ThemeMode.Dark)

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    fun instanceCardTablet() = capture("signin-card-tablet", SignInUiState(step = SignInStep.Instance(card)))

    @Test
    @Config(fontScale = 2f)
    fun instanceCardLargeFont() = capture("signin-card-font200", SignInUiState(step = SignInStep.Instance(card)))

    @Test
    @Config(qualifiers = "+ar-rXB-ldrtl")
    fun instanceCardRightToLeft() =
        capture("signin-card-rtl", SignInUiState(step = SignInStep.Instance(card)), direction = LayoutDirection.Rtl)

    @Test
    fun waitingForBrowser() = capture("signin-waiting", SignInUiState(step = SignInStep.WaitingForBrowser))

    @Test
    fun nothingAnswered() = capture(
        "signin-failed",
        SignInUiState(
            server = "cloud.example",
            step = SignInStep.Failed(
                SignInFailure.NothingAnswered(
                    listOf(
                        TriedAddress("https://cloud.example/", TriedBecause.DomainRoot),
                        TriedAddress("https://cloud.example/index.php/apps/social/", TriedBecause.AppPath),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun untrustedCertificate() = capture(
        "signin-certificate",
        SignInUiState(
            step = SignInStep.UntrustedCertificate(
                CertificateSummary(
                    host = "cloud.example",
                    subject = "CN=cloud.example",
                    issuer = "CN=cloud.example",
                    validUntil = "28 September 2027",
                    sha256 = List(32) { "AB" }.joinToString(":"),
                ),
            ),
        ),
    )

    @Test
    fun terms() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { TermsScreen(onAccept = {}, onDecline = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/signin-terms.png")
    }

    @Test
    fun welcome() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { WelcomeScreen(onAddAccount = {}, onFindServer = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/signin-welcome.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun welcomeLargeFont() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { WelcomeScreen(onAddAccount = {}, onFindServer = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/signin-welcome-font200.png")
    }

    private fun capture(
        name: String,
        state: SignInUiState,
        mode: ThemeMode = ThemeMode.Light,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            // Robolectric does not apply the locale's direction to Compose by itself
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlohaTheme(ThemeSettings(mode = mode)) { SignInScreen(state, NoActions) }
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private object NoActions : SignInActions {
        override fun onServerChange(text: String) = Unit

        override fun onContinue() = Unit

        override fun onSignIn() = Unit

        override fun onOtherServer() = Unit

        override fun onOpenBrowserAgain() = Unit

        override fun onTryAgain() = Unit

        override fun onCopyInstructions() = Unit

        override fun onToggleManualApiAddress() = Unit

        override fun onManualApiAddressChange(text: String) = Unit

        override fun onUseManualApiAddress() = Unit

        override fun onTrustCertificate() = Unit

        override fun onRejectCertificate() = Unit

        override fun onChooseClientCertificate() = Unit
    }
}
