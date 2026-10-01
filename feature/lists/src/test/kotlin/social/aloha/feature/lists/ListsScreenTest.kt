// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import android.app.Application
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import social.aloha.core.model.AccountList
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ListsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val surf = AccountList("1", "Surf")
    private val design = AccountList("2", "Design team", group = "design")

    private val actions = ListsActions(
        onOpen = { asked += "open:${it.id}" },
        onCreate = { asked += "create:$it" },
        onUpdate = { list, title, policy, exclusive -> asked += "update:${list.id}:$title:$policy:$exclusive" },
        onDelete = { asked += "delete:${it.id}" },
        onRetry = {},
        onBack = {},
    )

    private val members = MembersActions(
        onQuery = {},
        onAdd = { asked += "add:${it.id}" },
        onRemove = { asked += "remove:${it.id}" },
        onProfile = {},
        onRetry = {},
        onBack = {},
    )

    @Test
    fun `a list opens its timeline and is deleted only once asked, a group's offers nothing to change`() {
        compose.setContent {
            AlohaTheme { ListsScreen(ListsUiState(listOf(surf, design), loading = false), actions, {}) }
        }
        compose.onNodeWithText("Design team").performClick()
        compose.onNodeWithContentDescription("Options for Surf").performClick()
        compose.onNodeWithText("Delete list").performClick()
        compose.onNodeWithText("Delete Surf?").assertExists()
        assertEquals(listOf("open:2"), asked)
        compose.onNodeWithText("Delete list").performClick()
        compose.onNodeWithContentDescription("Options for Design team").assertDoesNotExist()
        assertEquals(listOf("open:2", "delete:1"), asked)
    }

    @Test
    fun lists() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            AlohaTheme { ListsScreen(ListsUiState(listOf(surf, design), loading = false), actions, {}) }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/lists.png")
    }

    @Test
    fun `a group list's members are shown, and nobody can be added or removed`() {
        compose.enableAccessibilityChecks()
        val state = MembersUiState(members = listOf(StatusSamples.alice), loading = false, group = true)
        compose.setContent { AlohaTheme { MembersScreen("Design team", state, members, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Add people you follow").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Alice Example").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/list-members-group.png")
    }

    @Test
    fun `someone found is added, and a member removed`() {
        val state =
            MembersUiState(
                members = listOf(StatusSamples.alice),
                loading = false,
                query = "b",
                found = listOf(StatusSamples.bob),
            )
        compose.setContent { AlohaTheme { MembersScreen("Surf", state, members, {}) } }
        compose.onNodeWithContentDescription("Add Bob").performClick()
        compose.onNodeWithContentDescription("Remove Alice Example").performClick()
        assertEquals(listOf("add:2", "remove:1"), asked)
        compose.onRoot().captureRoboImage("src/test/screenshots/list-members.png")
    }
}
