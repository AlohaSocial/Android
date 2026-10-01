// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import social.aloha.core.model.Tag

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TagHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    @Test
    fun `a hashtag not followed is followed from its timeline, and the tags used with it open theirs`() {
        compose.setContent {
            AlohaTheme {
                Surface {
                    TagHeader(
                        TagHeaderState(following = false, related = listOf(Tag("reef"))),
                        { asked += "follow" },
                        {},
                    ) {
                        asked += "tag:$it"
                    }
                }
            }
        }
        compose.onNodeWithText("Follow hashtag").performClick()
        compose.onNodeWithText("#reef").performClick()
        assertEquals(listOf("follow", "tag:reef"), asked)
        compose.onRoot().captureRoboImage("src/test/screenshots/tag-header.png")
    }

    @Test
    fun `something that is no hashtag says so, rather than offering to follow it`() {
        compose.setContent { AlohaTheme { Surface { TagHeader(TagHeaderState(notATag = true), {}, {}) {} } } }
        compose.onNodeWithText("That isn’t a hashtag.").assertExists()
        compose.onNodeWithText("Follow hashtag").assertDoesNotExist()
    }

    @Test
    fun `a hashtag that could not be loaded offers to try again`() {
        compose.setContent {
            AlohaTheme { Surface { TagHeader(TagHeaderState(failed = true), {}, { asked += "retry" }) {} } }
        }
        compose.onNodeWithText("Couldn’t load this hashtag. Try again").performClick()
        assertEquals(listOf("retry"), asked)
    }
}
