// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Surface
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.tv.account.AccountScreen
import social.aloha.tv.browse.SearchScreen
import social.aloha.tv.browse.SearchUiState
import social.aloha.tv.home.HomeScreen
import social.aloha.tv.home.HomeUiState
import social.aloha.tv.signin.TvSignInProblem
import social.aloha.tv.signin.TvSignInScreen
import social.aloha.tv.signin.TvSignInState
import social.aloha.tv.watch.ShortsScreen
import social.aloha.tv.watch.WatchScreen

/** Every TV screen at 1080p, as the TV draws it, each checked by the Accessibility Test Framework. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Television1080p)
class TvScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()

    @After
    fun release() = player.release()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent { TvTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun signInServer() = capture("tv-sign-in") { TvSignInScreen(TvSignInState.Server(), {}, {}, {}) }

    @Test
    fun signInProblem() = capture("tv-sign-in-problem") {
        TvSignInScreen(TvSignInState.Server(TvSignInProblem.Unreachable), {}, {}, {})
    }

    @Test
    fun signInCode() = capture("tv-sign-in-code") {
        val url = "https://cloud.example.org/index.php/apps/social/oauth/authorize?client_id=tv&redirect_uri=" +
            "urn%3Aietf%3Awg%3Aoauth%3A2.0%3Aoob&response_type=code&state=s&code_challenge=c"
        TvSignInScreen(TvSignInState.Code(url, "cloud.example.org", TvSignInProblem.CodeRefused), {}, {}, {})
    }

    @Test
    fun home() = capture("tv-home") { HomeScreen(Samples.home, Samples.nowhere) {} }

    @Test
    fun homeEmpty() = capture("tv-home-empty") { HomeScreen(HomeUiState(loadedOnce = true), Samples.nowhere) {} }

    @Test
    @Config(fontScale = 2f)
    fun homeLargeFont() = capture("tv-home-font200") { HomeScreen(Samples.home, Samples.nowhere) {} }

    @Test
    fun watchCovered() = capture("tv-watch-covered") {
        WatchScreen(Samples.watch.copy(covered = true, warning = "Flashing lights"), player, Samples.actions())
    }

    @Test
    fun watchFailed() = capture("tv-watch-failed") {
        WatchScreen(Samples.watch.copy(failed = true), player, Samples.actions())
    }

    @Test
    fun shortCovered() = capture("tv-short-covered") {
        ShortsScreen(Samples.shorts, startId = "s2", onReveal = {}, onNearEnd = {})
    }

    @Test
    fun search() = capture("tv-search") {
        SearchScreen(
            SearchUiState(videos = Samples.home.shelves[1].videos, people = Samples.people, searched = true),
            onSearch = {},
            onVideo = {},
            onPerson = {},
        )
    }

    @Test
    fun account() = capture("tv-account") { AccountScreen("alice", "cloud.example.org") {} }
}
