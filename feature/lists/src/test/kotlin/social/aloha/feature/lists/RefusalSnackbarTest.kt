// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import android.app.Application
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.designsystem.AlohaTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RefusalSnackbarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a refusal stays on screen while it is said, though the reader clears it once shown`() {
        var refusal: Refusal? by mutableStateOf(Refusal.Said("Not allowed"))
        compose.setContent {
            AlohaTheme {
                val snackbars = remember { SnackbarHostState() }
                RefusalSnackbar(refusal, snackbars) { refusal = null }
                SnackbarHost(snackbars)
            }
        }
        compose.onNodeWithText("Not allowed").assertIsDisplayed()
    }
}
