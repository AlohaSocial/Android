// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class QuoteAccessTest {
    @get:Rule
    val compose = createComposeRule()

    private val post = StatusSamples.post()

    @Test
    fun `the reader's own answer from the server comes first, then the post's policy`() {
        assertEquals(QuoteAccess.Request, quoteAccess(post.copy(quoteApproval = "manual"), own = false))
        assertEquals(QuoteAccess.Denied, quoteAccess(post.copy(quoteApproval = "denied"), own = false))
        assertEquals(QuoteAccess.Quote, quoteAccess(post.copy(quoteApproval = "automatic"), own = false))
        assertEquals(QuoteAccess.Denied, quoteAccess(post.copy(quoteApprovalPolicy = "nobody"), own = false))
        assertEquals(QuoteAccess.Quote, quoteAccess(post.copy(quoteApprovalPolicy = "followers"), own = false))
        assertEquals(QuoteAccess.Link, quoteAccess(post, own = false))
        assertEquals(QuoteAccess.Quote, quoteAccess(post.copy(quoteApprovalPolicy = "nobody"), own = true))
        assertEquals(QuoteAccess.Denied, quoteAccess(post.copy(visibility = Visibility.Direct), own = true))
    }

    private val asked = mutableListOf<String>()

    private val quoting = object : StatusActions {
        override val quotes: Boolean get() = true
        override fun onQuote(row: StatusRowUi) {
            asked += "quote"
        }
        override fun onBoost(row: StatusRowUi) {
            asked += "boost"
        }
        override fun onOpen(statusId: String) = Unit
        override fun onProfile(accountId: String) = Unit
        override fun onLink(target: RichLinkTarget) = Unit
        override fun onMedia(row: StatusRowUi, index: Int) = Unit
        override fun onReply(row: StatusRowUi) = Unit
        override fun onFavourite(row: StatusRowUi) = Unit
        override fun onBookmark(row: StatusRowUi) = Unit
        override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit
        override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit
        override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
    }

    private fun showBoost(status: Status) {
        compose.setContent {
            AlohaTheme {
                // read by someone other than its author, who may always quote their own
                val row = StatusRowMapper(RichTextCache(), RichTextColors.fromTheme()).map(status, "9")
                BoostButton(row, quoting) { onClick -> Button(onClick) { Text("boost button") } }
            }
        }
        compose.onNodeWithText("boost button").performClick()
    }

    @Test
    fun `where the screen quotes, boost asks whether to boost or to quote`() {
        showBoost(StatusSamples.boost.copy(reblog = post.copy(quoteApproval = "manual")))
        compose.onNodeWithText("Request to quote").performClick()
        compose.onNodeWithText("boost button").performClick()
        compose.onNodeWithText("Boost").performClick()
        assertEquals(listOf("quote", "boost"), asked)
    }

    @Test
    fun `a post that allows no quote says why, and its quote cannot be chosen`() {
        showBoost(post.copy(quoteApprovalPolicy = "nobody"))
        compose.onNode(hasText("Quote") and hasText("The author allows no quotes of this post")).assertIsNotEnabled()
    }
}
