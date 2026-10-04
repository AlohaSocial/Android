// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.profile.RelationshipChange
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.MediaCollection
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowCache
import social.aloha.core.ui.StatusRowUi

/** A profile's tabs: reachable however far down the reader is, and changed by a swipe across. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ProfileTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<ProfileTab>()

    private val actions = object : StatusActions, ProfileScreenActions {
        override fun onTab(tab: ProfileTab) {
            chosen += tab
        }

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
        override fun onBack() = Unit
        override fun onRefresh() = Unit
        override fun onNearEnd() = Unit
        override fun onFillGap(gapId: String) = Unit
        override fun onChange(change: RelationshipChange) = Unit
        override fun onBlockDomain(block: Boolean) = Unit
        override fun onLists() = Unit
        override fun onListed(listId: String, add: Boolean) = Unit
        override fun onEditProfile() = Unit
        override fun onReport() = Unit
        override fun onPeople(followers: Boolean) = Unit
        override fun onOpenInBrowser(url: String) = Unit
        override fun onAlbum(album: MediaCollection) = Unit
    }

    private val list = LazyListState()

    @Composable
    private fun profile(tab: ProfileTab): ProfileUiState {
        val colors = RichTextColors.fromTheme()
        val cache = RichTextCache()
        val rows = StatusRowCache(cache).apply { use(colors) }
        return ProfileUiState(
            header = ProfilePresentation.header(StatusSamples.bob, cache, colors, isSelf = false),
            relation = Relation(),
            tab = tab,
            items = (0 until POSTS).map {
                ProfileItem.Post(rows.rowFor(StatusSamples.post().copy(id = "s$it"), "1", null))
            },
            loading = false,
            now = StatusSamples.NOW,
        )
    }

    private fun show(tab: ProfileTab = ProfileTab.Posts) {
        compose.setContent { AlohaTheme { ProfileScreen(profile(tab), actions, actions, listState = list) } }
    }

    /** A profile whose tab follows what the reader chooses, as the ViewModel's would. */
    private fun showFollowing() {
        compose.setContent {
            var shown by remember { mutableStateOf(ProfileTab.Posts) }
            val follow = object : ProfileScreenActions by actions {
                override fun onTab(tab: ProfileTab) {
                    actions.onTab(tab)
                    shown = tab
                }
            }
            AlohaTheme { ProfileScreen(profile(shown), follow, actions, listState = list) }
        }
    }

    @Test
    fun `the tabs stay in view however far down the posts the reader is`() {
        show()
        runBlocking { list.scrollToItem(POSTS) }
        compose.waitForIdle()
        compose.onNodeWithText("Posts & replies").assertIsDisplayed()
    }

    @Test
    fun `a swipe across moves to the next tab, and back`() {
        show(ProfileTab.Replies)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(ProfileTab.Media, ProfileTab.Posts), chosen)
    }

    @Test
    fun `a tab chosen while the tabs are pinned keeps them pinned, the new tab from its start`() {
        showFollowing()
        runBlocking { list.scrollToItem(POSTS / 2) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(ProfileTab.Replies), chosen)
        assertEquals(TABS_AT, list.firstVisibleItemIndex)
        compose.onNodeWithText("Posts & replies").assertIsDisplayed()
    }

    @Test
    // a phone's height, so the tab row shows under a header that has the reader's note field
    @Config(qualifiers = "w411dp-h891dp")
    fun `a tab chosen with the header in view leaves the list where it is`() {
        showFollowing()
        compose.onNodeWithText("Media").performClick()
        compose.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
    }

    private companion object {
        const val POSTS = 30

        const val TABS_AT = 1
    }
}
