// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Card
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class StatusRoutingTest {
    private val opened = mutableListOf<String>()
    private var windows: ((String?, String?) -> Unit)? = null

    private val navigation = object : StatusNavigation {
        override fun openThread(statusId: String, history: Boolean) {
            opened += "thread $statusId"
        }

        override fun openMedia(statusId: String, index: Int) {
            opened += "media $statusId $index"
        }

        override fun openProfile(accountId: String?, acct: String?) = Unit
        override fun openTag(name: String) = Unit
        override fun openWeb(url: String) {
            opened += "web $url"
        }

        override fun openComposer(replyToId: String?) = Unit
        override fun editPost(statusId: String, redraft: Boolean) = Unit
        override fun report(accountId: String, handle: String, statusId: String?) = Unit
        override val newWindow get() = windows
    }

    private val actions = object : RoutedStatusActions(
        ApplicationProvider.getApplicationContext<Context>(),
        navigation = { navigation },
        onCopied = {},
        onDeleteAsked = {},
    ) {
        override fun onMute(row: StatusRowUi) = Unit
        override fun onPin(row: StatusRowUi) = Unit
        override fun onBoost(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
    }

    @Test
    fun `a picture opens in the viewer at that picture, and the rest of the row opens the post`() {
        val row = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
            .map(StatusSamples.gallery, viewerAccountId = "2")
        actions.onMedia(row, 2)
        actions.onOpen(row.statusId)
        assertEquals(listOf("media ${row.statusId} 2", "thread ${row.statusId}"), opened)
    }

    @Test
    fun `a new window is offered only where one makes sense, and opens the post`() {
        assertFalse(StatusMenuItem.OpenInNewWindow in actions.menu)
        windows = { statusId, accountId -> opened += "window $statusId $accountId" }
        assertTrue(StatusMenuItem.OpenInNewWindow in actions.menu)
        val row = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
            .map(StatusSamples.post(), viewerAccountId = "2")
        actions.onMenu(row, StatusMenuItem.OpenInNewWindow)
        assertEquals(listOf("window ${row.statusId} null"), opened)
    }

    @Test
    fun `a video's card goes to an app that plays it, never a browser, and any other card opens as a link`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val video = Card(url = "https://www.youtube.com/watch?v=abc", title = "Reef", type = "video")
        openCard(context, video, actions)
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals("https://www.youtube.com/watch?v=abc", started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        openCard(context, video.copy(type = "link"), actions)
        assertEquals(listOf("web https://www.youtube.com/watch?v=abc"), opened)
    }
}
