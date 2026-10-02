// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.hashtags

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import social.aloha.core.data.tags.TagGroup
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Tag

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HashtagsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = HashtagsActions(
        onTag = { asked += "tag:$it" },
        onGroup = { asked += "group:${it.name}" },
        onFollow = { name, follow -> asked += (if (follow) "follow:" else "unfollow:") + name },
        onSaveGroup = { name, tags, previous -> asked += "save:$name:${tags.joinToString()}:$previous" },
        onDeleteGroup = { asked += "delete:$it" },
        onBack = {},
    )

    private val state = HashtagsUiState(
        followed = listOf(Tag("surf"), Tag("nextcloud")),
        loading = false,
        groups = listOf(TagGroup("Sea", listOf("surf", "reef"))),
    )

    @Test
    fun `a hashtag is followed by name, and a followed one opens its timeline`() {
        compose.setContent { AlohaTheme { HashtagsScreen(state, actions, {}, {}) } }
        compose.onNodeWithText("#surf").performClick()
        compose.onNodeWithText("Hashtag to follow").performTextInput("#Reef")
        compose.onNodeWithText("Follow").performClick()
        assertEquals(listOf("tag:surf", "follow:#Reef"), asked)
    }

    // the group dialog is not driven here: under Robolectric a dialog holding a text field never goes idle
    @Test
    fun `a tag group's hashtags are written apart by spaces, commas or lines`() {
        assertEquals(listOf("#surf", "reef", "#kite"), groupTags("#surf, reef\n #kite "))
    }

    @Test
    fun hashtags() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { HashtagsScreen(state, actions, {}, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/hashtags.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun hashtagsLargeFont() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { HashtagsScreen(state, actions, {}, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/hashtags-font200.png")
    }
}
