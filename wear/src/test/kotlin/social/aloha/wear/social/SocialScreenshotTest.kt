// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.social

import android.app.Application
import androidx.compose.runtime.Composable
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
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.Visibility

/** The watch's timeline, notifications and a post on a large round watch; home on a square one; 200 % font. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
class SocialScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val links = SocialLinks(onPost = {}, onNotifications = {}, onAccount = {})

    private fun post(id: String, author: String, text: String, warning: String = "", favourited: Boolean = false) =
        WatchPost(id, author, author.lowercase(), null, text, warning, Visibility.Public, favourited, false, false)

    private val posts = listOf(
        post("3", "Kai", "Surf’s up at Waikīkī this morning, glassy and chest high."),
        post("2", "Leilani", "", warning = "Spoilers for the season finale"),
        post("1", "Noa", "Shave ice after work, anyone?"),
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            // no clock: it would put the minute the test ran into every image
            MaterialTheme { AppScaffold(timeText = {}) { content() } }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun home() = capture("wear-home") { HomeScreen(Rows.Loaded(posts), links, onRetry = {}) }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.WearOSSquare)
    fun homeSquare() = capture("wear-home-square") { HomeScreen(Rows.Loaded(posts), links, onRetry = {}) }

    @Test
    fun homeFailed() = capture("wear-home-failed") { HomeScreen(Rows.Failed, links, onRetry = {}) }

    @Test
    fun notifications() = capture("wear-notifications") {
        NotificationsScreen(
            Rows.Loaded(
                listOf(
                    WatchNotice("9", NotificationKind.Favourite, "Kai", posts[0]),
                    WatchNotice("8", NotificationKind.Follow, "Noa", null),
                ),
            ),
            onPost = {},
            onRetry = {},
        )
    }

    @Test
    fun post() = capture("wear-post") {
        PostScreen(PostUiState(posts[0].copy(favourited = true), loading = false), onToggle = {}, onReply = {})
    }

    @Test
    fun postBehindWarning() = capture("wear-post-warning") {
        PostScreen(PostUiState(posts[1], loading = false), onToggle = {}, onReply = {})
    }

    @Test
    @Config(fontScale = 2f)
    fun postLargeFont() = capture("wear-post-font200") {
        PostScreen(PostUiState(posts[0], loading = false, sent = true), onToggle = {}, onReply = {})
    }
}
