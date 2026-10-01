// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.saved

import android.app.Application
import androidx.compose.ui.graphics.Color
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
import social.aloha.core.html.RichTextCache
import social.aloha.core.navigation.SavedKind
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SavedPostsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))

    private val state = SavedPostsUiState(posts = listOf(StatusSamples.post()), loading = false, done = true)

    private fun actions(archive: Boolean) = SavedActions(
        onMore = {},
        onUnarchive = if (archive) ({ asked += "unarchive:$it" }) else null,
        onUnarchiveFailureShown = {},
        onBack = {},
    )

    @Test
    fun `an archived post is put back on the profile from the archive`() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme {
                SavedPostsScreen(
                    SavedKind.Archived,
                    state,
                    mapper,
                    Inert,
                    actions(archive = true),
                    now = StatusSamples.NOW,
                )
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/saved-archived.png")
        compose.onNodeWithText("Put back on profile").performClick()
        assertEquals(listOf("unarchive:${StatusSamples.post().id}"), asked)
    }

    @Test
    fun `bookmarks offer nothing to put back, and say when there are none`() {
        val none = state.copy(posts = emptyList())
        compose.setContent {
            AlohaTheme { SavedPostsScreen(SavedKind.Bookmarks, none, mapper, Inert, actions(archive = false)) }
        }
        compose.onNodeWithText("Put back on profile").assertDoesNotExist()
        compose.onNodeWithText("Nothing bookmarked yet.", substring = true).assertExists()
    }
}

private object Inert : StatusActions {
    override fun onOpen(statusId: String) = Unit

    override fun onProfile(accountId: String) = Unit

    override fun onLink(target: RichLinkTarget) = Unit

    override fun onMedia(row: StatusRowUi, index: Int) = Unit

    override fun onReply(row: StatusRowUi) = Unit

    override fun onBoost(row: StatusRowUi) = Unit

    override fun onFavourite(row: StatusRowUi) = Unit

    override fun onBookmark(row: StatusRowUi) = Unit

    override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit

    override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit

    override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
}
