// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.shorts

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import social.aloha.core.designsystem.ThemeMode
import social.aloha.core.designsystem.ThemeSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ShortsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = object : ShortsActions {
        override fun onFavourite(short: ShortUi) {
            asked += "favourite"
        }

        override fun onBoost(short: ShortUi) {
            asked += "boost"
        }

        override fun onComments(short: ShortUi) {
            asked += "comments"
        }

        override fun onShare(short: ShortUi) {
            asked += "share"
        }

        override fun onCopyLink(short: ShortUi) {
            asked += "copy"
        }

        override fun onProfile(short: ShortUi) {
            asked += "profile"
        }

        override fun onMuted(muted: Boolean) {
            asked += "muted:$muted"
        }

        override fun onReport(short: ShortUi) {
            asked += "report"
        }
    }

    private val clip =
        MediaAttachment(
            "m",
            AttachmentKind.Video,
            url = "https://cloud.example/m.mp4",
            blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
        )

    @Composable
    private fun short(sensitive: Boolean = false): ShortUi {
        val post = StatusSamples.post("<p>Waves at the point #surf</p>").copy(
            id = "s1",
            sensitive = sensitive,
            mediaAttachments = listOf(clip),
        )
        val row = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme()).map(post, "2")
        return ShortUi(row, ShortSource("s1", emptyList()), clip)
    }

    private fun show(sensitive: Boolean = false) {
        compose.setContent {
            AlohaTheme(ThemeSettings(mode = ThemeMode.Dark)) {
                ShortPage(short(sensitive), player = null, muted = true, actions = actions)
            }
        }
    }

    @Test
    fun `every gesture has an equivalent on the rail, in the menu and among the screen reader's actions`() {
        show()
        // the double tap's favourite and the edge swipe's profile are on the rail
        compose.onNodeWithContentDescription("Favourite").performClick()
        compose.onNodeWithContentDescription("View Alice Example's profile").performClick()
        // the long press's pause, and the profile again, are in the menu
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.onNodeWithText("View Alice Example's profile").assertIsDisplayed()
        // and all three are actions a screen reader offers on the short itself
        val labels = compose.onNodeWithContentDescription("Short by Alice Example", substring = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }.toSet()
        assertEquals(setOf("Favourite", "Pause", "View Alice Example's profile"), labels)
        assertEquals(listOf("favourite", "profile"), asked)
    }

    @Test
    fun `a tap on share shares the link, a long press copies it`() {
        show()
        compose.onNodeWithContentDescription("Share").performClick()
        compose.onNodeWithContentDescription("Share").performTouchInput { longClick() }
        assertEquals(listOf("share", "copy"), asked)
    }

    @Test
    fun short() {
        compose.enableAccessibilityChecks()
        show()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/shorts-page.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun shortLargeFont() {
        compose.enableAccessibilityChecks()
        show()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/shorts-page-font200.png")
    }

    @Test
    fun sensitiveShort() {
        show(sensitive = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/shorts-sensitive.png")
    }
}
