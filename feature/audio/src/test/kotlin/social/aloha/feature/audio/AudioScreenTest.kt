// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.SeenBy

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AudioScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val swim = AudioItem("1", "https://cloud.example/1.mp3", "Night swim", "Alice Example", null)
    private val waves = AudioItem("2", "https://cloud.example/2.mp3", "Waves at the point", "Bob", null)

    private val state = AudioUiState(
        posts = listOf(swim, waves),
        sources = listOf(TimelineSource.Home, TimelineSource.Local, TimelineSource.Federated),
        nowPlaying = NowPlaying(swim, playing = true),
        loadedOnce = true,
    )

    private val actions = AudioScreenActions(
        onPlay = { asked += "play:$it" },
        onOpen = { asked += "open:${it.statusId}" },
        onSource = { asked += "source:$it" },
        onNearEnd = {},
    )

    @Test
    fun `each sound plays from its button, the one playing pauses, and the rest of the row opens the post`() {
        compose.setContent { AlohaTheme { AudioScreen(state, actions, accountButton = {}) } }
        compose.onNodeWithContentDescription("Pause Night swim").performClick()
        compose.onNodeWithContentDescription("Play Waves at the point").performClick()
        compose.onNodeWithText("Bob").performClick()
        compose.onNodeWithText("This server").performClick()
        assertEquals(listOf("play:1", "play:2", "open:2", "source:Local"), asked)
    }

    @Test
    fun audio() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { AudioScreen(state, actions, accountButton = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/audio.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun audioLargeFont() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { AudioScreen(state, actions, accountButton = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/audio-font200.png")
    }

    @Test
    fun audioTogether() {
        val held = waves.copy(
            statusId = "remote:two.example:2",
            seenBy = listOf(SeenBy("@alice@two.example", null, PINK)),
        )
        val both = swim.copy(seenBy = listOf(SeenBy("@alice@one.example", null, null), held.seenBy.single()))
        compose.enableAccessibilityChecks()
        val together = state.copy(posts = listOf(both, held), sources = emptyList())
        compose.setContent { AlohaTheme { AudioScreen(together, actions, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithContentDescription("Seen by @alice@two.example").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/audio-together.png")
    }

    @Test
    fun miniPlayer() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { MiniPlayer(NowPlaying(waves, playing = false), { asked += "open:${it.statusId}" }, {}, {}) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithContentDescription("Resume Waves at the point").performClick()
        compose.onNodeWithText("Waves at the point").performClick()
        assertEquals(listOf("open:2"), asked)
        compose.onRoot().captureRoboImage("src/test/screenshots/audio-mini-player.png")
    }

    private companion object {
        const val PINK = 0xFFE91E63.toInt()
    }
}
