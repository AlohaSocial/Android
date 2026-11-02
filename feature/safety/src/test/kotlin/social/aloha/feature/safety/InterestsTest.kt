// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.interests.Interests
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.InterestSettings
import social.aloha.core.model.InterestTag
import social.aloha.core.model.InterestsState
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.SignedInFixture

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InterestsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private val actions = object : InterestsActions {
        override fun onLearning(learning: Boolean) {
            asked += "learning:$learning"
        }

        override fun onPaused(paused: Boolean) {
            asked += "paused:$paused"
        }

        override fun onReset() {
            asked += "reset"
        }

        override fun onChangeFailureShown() = Unit
    }

    private val state = InterestsUiState(
        InterestsState(
            interests = listOf(InterestTag("surf"), InterestTag("nextcloud")),
            candidates = listOf(InterestTag("reef")),
        ),
        loading = false,
    )

    @After
    fun close() = main.clear()

    @Test
    fun `the interests the server learnt are shown as it decides them, and learning is paused`() {
        compose.enableAccessibilityChecks()
        compose.setContent { AlohaTheme { InterestsScreen(state, actions, {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onRoot().captureRoboImage("src/test/screenshots/interests.png")
        compose.onNodeWithText("#surf").assertExists()
        compose.onNodeWithText("#reef").assertExists()
        compose.onNodeWithText("Pause learning").performClick()
        compose.onNodeWithText("Forget all interests").performClick()
        compose.onNodeWithText("Forget all interests?").assertExists()
        assertEquals(listOf("paused:true"), asked)
    }

    @Test
    fun `learning the administrator paused says so, keeps the reader's switches and claims no learning`() {
        val settings = InterestSettings(paused = true, instancePaused = true)
        val paused = state.copy(interests = state.interests?.copy(settings = settings, thin = true))
        compose.setContent { AlohaTheme { InterestsScreen(paused, actions, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/interests_instance_paused.png")
        compose.onNodeWithText("Your administrator has paused learning", substring = true).assertExists()
        compose.onNodeWithText("Learn from what I read").assertExists()
        compose.onNodeWithText("Pause learning").assertExists()
        compose.onNodeWithText("Still learning", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a server that keeps no interests says so, rather than failing`() = runBlocking {
        val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
        val server = MockWebServer().apply {
            enqueue(MockResponse.Builder().code(404).build())
            start()
        }
        try {
            val reader = fixture.signIn(server.url("/"))
            val model = main.keep(InterestsViewModel(reader.id, fixture.accounts, Interests(fixture.clients)))
            val shown = withTimeout(10.seconds) { model.uiState.first { !it.loading } }
            assertTrue(shown.unavailable)
        } finally {
            fixture.close()
            server.close()
        }
    }
}
