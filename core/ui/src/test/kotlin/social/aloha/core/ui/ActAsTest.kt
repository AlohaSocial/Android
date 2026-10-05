// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ActAsTest {
    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<Triple<String, String, PostAct>>()

    private val actAs = object : ActAs {
        override val accounts = listOf(
            AccountChoice("a", "Alice", "@alice@cloud.example", null),
            AccountChoice("b", "Bob", "@bob@other.example", null, unread = 2),
        )
        override val current: String = "a"

        override fun act(accountId: String, url: String, act: PostAct) {
            done += Triple(accountId, url, act)
        }
    }

    @Test
    fun `a long press acts as another account, chosen among those not in use`() {
        compose.setContent {
            AlohaTheme {
                CompositionLocalProvider(LocalActAs provides actAs) {
                    val row = StatusRowMapper(
                        RichTextCache(),
                        RichTextColors.fromTheme(),
                    ).map(StatusSamples.post(), "1")
                    ActAsAction(row, PostAct.Favourite, R.string.status_favourite_as) { long ->
                        Text("favourite", Modifier.clickable { long?.invoke() })
                    }
                }
            }
        }
        compose.onNodeWithText("favourite").performClick()
        compose.onNodeWithText("Alice").assertDoesNotExist()
        compose.onNodeWithText("Bob").performClick()
        assertEquals(listOf(Triple("b", StatusSamples.post().url, PostAct.Favourite)), done)
    }
}
