// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.saved

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
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
        Inert,
        onMore = {},
        onUnarchive = if (archive) ({ asked += "unarchive:$it" }) else null,
    )

    /** [kind]'s rows as the reader's own profile lists them, under the Saved switch on that tab. */
    private fun show(kind: SavedKind, shown: SavedPostsUiState = state) {
        val rows = shown.posts.map { mapper.map(it, shown.viewer) }
        val listActions = actions(archive = kind == SavedKind.Archived)
        compose.setContent {
            AlohaTheme {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn {
                        if (kind != SavedKind.Archived) item { SavedSwitch(kind) { asked += "switch:$it" } }
                        savedRows(SavedPage(kind, shown, rows, StatusSamples.NOW), listActions)
                    }
                }
            }
        }
    }

    @Test
    fun `an archived post is put back on the profile from the archive`() {
        compose.enableAccessibilityChecks()
        show(SavedKind.Archived)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/saved-archived.png")
        compose.onNodeWithText("Put back on profile").performClick()
        assertEquals(listOf("unarchive:${StatusSamples.post().id}"), asked)
    }

    @Test
    @Config(fontScale = 2f)
    fun `an archived post is put back on the profile from the archive, at twice the font size`() {
        compose.enableAccessibilityChecks()
        show(SavedKind.Archived)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/saved-archived-font200.png")
        compose.onNodeWithText("Put back on profile").performClick()
        assertEquals(listOf("unarchive:${StatusSamples.post().id}"), asked)
    }

    @Test
    fun `bookmarks offer nothing to put back, and say when there are none`() {
        show(SavedKind.Bookmarks, state.copy(posts = emptyList()))
        compose.onNodeWithText("Put back on profile").assertDoesNotExist()
        compose.onNodeWithText("Nothing bookmarked yet.", substring = true).assertExists()
    }

    @Test
    fun `the Saved tab switches between the liked posts and the bookmarks`() {
        compose.enableAccessibilityChecks()
        show(SavedKind.Favourites)
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/saved-favorites.png")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("Bookmarks").performClick()
        assertEquals(listOf("switch:${SavedKind.Bookmarks}"), asked)
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
