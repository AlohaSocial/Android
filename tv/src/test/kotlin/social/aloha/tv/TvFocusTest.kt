// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Surface
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.tv.home.HomeLinks
import social.aloha.tv.home.HomeScreen
import social.aloha.tv.signin.TvSignInScreen
import social.aloha.tv.signin.TvSignInState
import social.aloha.tv.watch.ReportReason
import social.aloha.tv.watch.ShortsScreen
import social.aloha.tv.watch.WatchScreen

/** Every action on the TV is reached and pressed with the D-pad alone, no touch. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Television1080p)
class TvFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()

    @After
    fun release() = player.release()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TvTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        // the topmost window: a dialog, where one is open
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun press(keys: List<Key>) = keys.forEach(::press)

    /** What has focus is named [label], or is inside or around what is: a card, its title, a button's icon. */
    private fun focusOn(label: String) =
        hasContentDescription(label, substring = true).or(hasText(label, substring = true)).let { named ->
            isFocused().and(named.or(hasAnyAncestor(named)).or(hasAnyDescendant(named)))
        }

    private fun focused(label: String) = compose.onNode(focusOn(label)).assertExists("$label has no focus")

    @Test
    fun `every card on every shelf is reached by the D-pad, and the top row above them`() {
        show { HomeScreen(Samples.home, Samples.nowhere) {} }
        focused("The harbour tour")
        press(Key.DirectionDown)
        focused("Sailing at dawn")
        press(Key.DirectionRight)
        focused("Night market")
        press(Key.DirectionRight)
        focused("Reef")
        press(listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionLeft))
        focused("@bob")
        press(Key.DirectionRight)
        focused("@jane")
        press(listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionLeft))
        focused("Volcano walk")
        press(Key.DirectionRight)
        focused("Ukulele lesson")
        press(listOf(Key.DirectionLeft, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
        focused("The harbour tour")
        press(Key.DirectionUp)
        focused("Search")
        press(Key.DirectionRight)
        focused("Account")
    }

    /**
     * From the warning a sensitive video waits behind: the player's own view takes the D-pad out of Compose,
     * which a JVM test cannot follow; leaving it downwards is checked on a TV emulator.
     */
    @Test
    fun `the watch page's follow and report are reached below the video, and a report is sent by the D-pad`() {
        var reported: ReportReason? = null
        show { WatchScreen(Samples.watch.copy(covered = true), player, Samples.actions { reported = it }) }
        focused("Watch anyway")
        press(Key.DirectionDown)
        focused("Report")
        press(Key.DirectionLeft)
        focused("Follow")
        press(Key.DirectionRight)
        focused("Report")
        press(Key.DirectionCenter)
        focused("spam")
        press(listOf(Key.DirectionDown, Key.DirectionCenter))
        assertEquals(ReportReason.Legal, reported)
    }

    @Test
    fun `shorts move with up and down, and a covered one shows on centre`() {
        var revealed: String? = null
        show { ShortsScreen(Samples.shorts, startId = "s1", onReveal = { revealed = it }, onNearEnd = {}) }
        compose.onNode(hasText("Wave")).assertExists()
        compose.onNode(hasText("Gecko")).assertDoesNotExist()
        press(Key.DirectionDown)
        compose.onNode(hasText("Gecko")).assertExists()
        press(Key.DirectionCenter)
        assertEquals("s2", revealed)
        press(Key.DirectionUp)
        compose.onNode(hasText("Wave")).assertExists()
    }

    @Test
    fun `a click with a mouse or a touch works too, on a card and on a button`() {
        val opened = mutableListOf<String>()
        val links = HomeLinks(
            onVideo = { opened += it.title },
            onShort = {},
            onPerson = {},
            onSearch = { opened += "search" },
            onAccount = {},
        )
        show { HomeScreen(Samples.home, links) {} }
        compose.onNode(hasContentDescription("Search")).performClick()
        compose.onNode(hasContentDescription("Sailing at dawn", substring = true)).performClick()
        assertEquals(listOf("search", "Sailing at dawn"), opened)
    }

    @Test
    fun `the sign-in's field and buttons are reached by the D-pad`() {
        show { TvSignInScreen(TvSignInState.Code("https://cloud.example.org/x", "cloud.example.org"), {}, {}, {}) }
        compose.onNode(isFocused().and(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)))
            .assertExists()
        press(Key.DirectionDown)
        focused("Use another server")
        press(Key.DirectionLeft)
        focused("Sign in")
    }
}
