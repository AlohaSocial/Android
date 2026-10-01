// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.FilterKeyword

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FiltersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val filters = listOf(
        Filter(
            "1",
            "Spoilers",
            listOf(FilterContext.Home, FilterContext.Public),
            filterAction = FilterAction.Hide,
            keywords = listOf(FilterKeyword("1", "finale"), FilterKeyword("2", "ending")),
        ),
        Filter("2", "Election", listOf(FilterContext.Home), expiresAt = now.minusSeconds(60)),
    )

    private val asked = mutableListOf<String>()

    private val actions = object : FilterEditActions {
        override fun onTitle(title: String) {
            asked += "title:$title"
        }

        override fun onAddKeyword(keyword: String, wholeWord: Boolean) {
            asked += "add:$keyword:$wholeWord"
        }

        override fun onWholeWord(index: Int, wholeWord: Boolean) {
            asked += "whole:$index:$wholeWord"
        }

        override fun onRemoveKeyword(index: Int) {
            asked += "remove:$index"
        }

        override fun onContext(context: FilterContext, on: Boolean) {
            asked += "context:${context.wire}:$on"
        }

        override fun onAction(action: FilterAction) {
            asked += "action:${action.wire}"
        }

        override fun onExpiry(expiry: Expiry) {
            asked += "expiry:$expiry"
        }

        override fun onSave() {
            asked += "save"
        }

        override fun onDelete() {
            asked += "delete"
        }

        override fun onFailureShown() = Unit
    }

    @Test
    fun `each filter says what it does where, and an expired one says so`() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme {
                FiltersScreen(
                    FiltersUiState(filters, loading = false),
                    onEdit = { asked += "edit:$it" },
                    onHideStrangers = { asked += "strangers:$it" },
                    onBack = {},
                    now = now,
                )
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Hides · Home and lists, Public timelines · 2 keywords").assertExists()
        compose.onNodeWithText("Expired").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/filters.png")
        compose.onNodeWithText("Spoilers").performClick()
        compose.onNodeWithText("Only people I follow in public timelines").performClick()
        assertEquals(listOf("edit:1", "strangers:true"), asked)
    }

    @Test
    fun `the editor adds keywords, and chooses where, what and how long`() {
        val state = filters[0].editing()
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { FilterEditScreen(state, actions, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/filter-edit.png")
        compose.onNodeWithText("Keyword or phrase").performTextInput("twist")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("add:twist:true", "context:notifications:true", "save"), asked)
    }
}
