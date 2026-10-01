// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.html.RichTextCache
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class StatusRoutingTest {
    private val opened = mutableListOf<String>()

    private val navigation = object : StatusNavigation {
        override fun openThread(statusId: String) {
            opened += "thread $statusId"
        }

        override fun openMedia(statusId: String, index: Int) {
            opened += "media $statusId $index"
        }

        override fun openProfile(accountId: String?, acct: String?) = Unit
        override fun openTag(name: String) = Unit
        override fun openWeb(url: String) = Unit
        override fun openComposer(replyToId: String?) = Unit
        override fun editPost(statusId: String, redraft: Boolean) = Unit
        override fun report(accountId: String, handle: String, statusId: String?) = Unit
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
}
