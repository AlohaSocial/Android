// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PictureFirstTapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tap on a picture shown first opens it`() {
        val opened = mutableListOf<Int>()
        val actions = object : StatusActions {
            override fun onOpen(statusId: String) = Unit
            override fun onProfile(accountId: String) = Unit
            override fun onLink(target: RichLinkTarget) = Unit
            override fun onMedia(row: StatusRowUi, index: Int) {
                opened += index
            }
            override fun onReply(row: StatusRowUi) = Unit
            override fun onBoost(row: StatusRowUi) = Unit
            override fun onFavourite(row: StatusRowUi) = Unit
            override fun onBookmark(row: StatusRowUi) = Unit
            override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
            override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
            override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
        }
        compose.setContent {
            AlohaTheme {
                val row = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme())
                    .map(StatusSamples.post().copy(mediaAttachments = listOf(StatusSamples.image("a"))), "1", null)
                StatusCard(row, StatusSamples.NOW, SensitiveMediaPolicy.ShowAll, actions, pictureFirst = true)
            }
        }
        // below the author's line, in the middle of the picture
        compose.onRoot().performTouchInput { click(Offset(width / 2f, 150.dp.toPx())) }
        assertEquals(listOf(0), opened)
    }
}
