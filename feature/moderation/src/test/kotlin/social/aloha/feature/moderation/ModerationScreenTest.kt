// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Account
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.model.AdminLink
import social.aloha.core.model.AdminReport
import social.aloha.core.model.AdminTag
import social.aloha.core.model.ModeratorRole
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ModerationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val spammer = Account(id = "9", username = "spam", acct = "spam@bad.example", displayName = "Cheap Pills")

    private val state = ModerationState(
        role = ModeratorRole(ModeratorRole.ADMINISTRATOR),
        reports = listOf(
            AdminReport(
                id = "1",
                category = "spam",
                comment = "Posts the same link to everyone who mentions a pharmacy.",
                account = Account(id = "2", username = "bob", acct = "bob"),
                targetAccount = spammer,
                statuses = listOf(StatusSamples.post("<p>Cheap pills, today only: example.test</p>")),
            ),
        ),
        accounts = listOf(AdminAccount(id = "9", username = "spam", domain = "bad.example", account = spammer)),
        tags = listOf(AdminTag(id = "37", name = "aloha", uses = 12, accounts = 5, requiresReview = true)),
        posts = emptyList(),
        links = listOf(AdminLink("12", "https://news.example/aloha", "Aloha Social ships", "News Example")),
    )

    @Test
    fun `a report is taken, resolved or acted on, and acting asks first`() {
        val acted = mutableListOf<AdminAccountAction>()
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme {
                ModerationScreen(state, ModerationActions(onActOnReport = { _, action -> acted += action }), {}, {})
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-reports.png")
        compose.onNodeWithText("Suspend").performClick()
        compose.onNodeWithText("Suspend @spam@bad.example?").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-suspend.png")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<AdminAccountAction>(), acted)
    }

    @Test
    @Config(fontScale = 2f)
    fun `a report is taken, resolved or acted on, at twice the font size`() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { ModerationScreen(state, ModerationActions(), {}, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-reports-font200.png")
    }

    @Test
    fun `accounts and trends each have their tab`() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { ModerationScreen(state, ModerationActions(), {}, {}, initialTab = ModerationTab.Accounts) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-accounts.png")
        compose.onNodeWithText("Trends").performClick()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-trends.png")
    }

    @Test
    fun `a token without the admin scopes asks for them`() {
        var allowed = false
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme {
                ModerationScreen(state.copy(consent = true), ModerationActions(onAllow = { allowed = true }), {}, {})
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/moderation-consent.png")
        compose.onNodeWithText("Allow moderation").performClick()
        assertEquals(true, allowed)
    }
}
