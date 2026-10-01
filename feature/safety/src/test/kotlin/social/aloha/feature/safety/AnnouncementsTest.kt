// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Announcement
import social.aloha.core.model.AnnouncementReaction
import social.aloha.core.ui.fullDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AnnouncementsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun zone() {
        // an announcement shows its full date, which would otherwise follow the machine's zone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private val heart = "❤️"
    private val party = "🎉"

    private val announcement = Announcement(
        "1",
        content = "<p>Maintenance on <strong>Sunday</strong>: the server is down from 2 to 3.</p>",
        publishedAt = Instant.parse("2026-10-01T09:00:00Z"),
        reactions = listOf(AnnouncementReaction(heart, count = 2, me = true), AnnouncementReaction(party, count = 1)),
    )

    @Test
    fun `a reaction adds the reader's, a new one starts at one, and taking the last back removes it`() {
        val added = announcement.reacted(party, add = true).reactions.first { it.name == party }
        assertEquals(2 to true, added.count to added.me)
        assertEquals(listOf(1, 1), announcement.reacted("👍", add = true).reactions.drop(1).map { it.count })
        val single = announcement.copy(reactions = listOf(AnnouncementReaction(heart, count = 1, me = true)))
        assertEquals(emptyList<AnnouncementReaction>(), single.reacted(heart, add = false).reactions)
        // the reader's own cannot be added twice
        assertEquals(announcement, announcement.reacted(heart, add = true))
    }

    @Test
    fun `an announcement shows when, whether it is new, what it says, and takes reactions`() {
        val asked = mutableListOf<String>()
        val state = AnnouncementsUiState(listOf(announcement), fresh = setOf("1"), loading = false)
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { AnnouncementsScreen(state, { id, name, add -> asked += "$id:$name:$add" }, {}, {}) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("New").assertExists()
        compose.onNodeWithText(fullDate(Instant.parse("2026-10-01T09:00:00Z"))).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/announcements.png")
        compose.onNodeWithText(heart).performClick()
        assertEquals(listOf("1:$heart:false"), asked)
    }
}
