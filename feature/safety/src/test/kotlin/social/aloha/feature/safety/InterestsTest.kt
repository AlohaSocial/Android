// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import social.aloha.core.data.interests.Interests
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.ActivityLearning
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

        override fun onActivityLearning(learning: Boolean) {
            asked += "activity:$learning"
        }

        override fun onReset() {
            asked += "reset"
        }

        override fun onChangeFailureShown() = Unit
    }

    private val state = InterestsUiState(
        InterestsState(
            settings = InterestSettings(learning = true),
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
        compose.onNodeWithText("Learn from what I do").assertDoesNotExist()
        compose.onNodeWithText("Pause learning").performClick()
        compose.onNodeWithText("Forget all interests").performClick()
        compose.onNodeWithText("Forget all interests?").assertExists()
        assertEquals(listOf("paused:true"), asked)
    }

    @Test
    fun `learning the administrator paused says so, keeps the reader's switches and claims no learning`() {
        val settings = InterestSettings(learning = true, paused = true, instancePaused = true)
        val paused = state.copy(interests = state.interests?.copy(settings = settings, thin = true))
        compose.setContent { AlohaTheme { InterestsScreen(paused, actions, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/interests_instance_paused.png")
        compose.onNodeWithText("Your administrator has paused learning", substring = true).assertExists()
        compose.onNodeWithText("Learn from what I read").assertExists()
        compose.onNodeWithText("Pause learning").assertExists()
        compose.onNodeWithText("Still learning", substring = true).assertDoesNotExist()
    }

    @Test
    fun `little to go on is still learning only while learning is on`() {
        val learning = mutableStateOf(InterestSettings())
        compose.setContent {
            val thin = state.interests?.copy(settings = learning.value, thin = true)
            AlohaTheme { InterestsScreen(state.copy(interests = thin), actions, {}) }
        }
        compose.onNodeWithText("Still learning", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pause learning").assertDoesNotExist()
        compose.onNodeWithText("Learn from what I read").performClick()
        assertEquals(listOf("learning:true"), asked)
        learning.value = InterestSettings(learning = true)
        compose.onNodeWithText("Still learning", substring = true).assertExists()
        learning.value = InterestSettings(learning = true, paused = true)
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

    @Test
    fun `the activity learning consent shows where the server keeps one, and asks to change it`() {
        val consent = state.copy(activity = ActivityLearning(revision = 3, learning = false))
        compose.setContent { AlohaTheme { InterestsScreen(consent, actions, {}) } }
        compose.onNodeWithText("Learn from what I do").performClick()
        assertEquals(listOf("activity:true"), asked)
    }

    @Test
    fun `a server with recommendations on shows the consent, and a stale change adopts what the server has`() =
        runBlocking {
            val server = RecommendationsServer(preferences = """{"revision": 2, "learning_mode": "paused"}""")
            withModel(server) { model ->
                val shown = withTimeout(10.seconds) { model.uiState.first { it.activity != null } }
                assertEquals(ActivityLearning(revision = 2, learning = false), shown.activity)
                server.preferences = """{"revision": 4, "learning_mode": "on"}"""
                server.learning = MockResponse.Builder().code(409)
                    .body("""{"error": "revision-conflict", "current": {"revision": 4, "learning_mode": "on"}}""")
                    .addHeader("content-type", "application/json").build()
                model.onActivityLearning(true)
                val adopted = withTimeout(10.seconds) { model.uiState.first { it.activity?.revision == 4 } }
                assertTrue(checkNotNull(adopted.activity).learning)
                assertTrue(adopted.changeFailed)
                assertEquals(listOf("if_revision=2&mode=on"), server.changes)
            }
        }

    @Test
    fun `a change the server takes shows its answer`() = runBlocking {
        val server = RecommendationsServer(preferences = """{"revision": 2, "learning_mode": "off"}""")
        withModel(server) { model ->
            withTimeout(10.seconds) { model.uiState.first { it.activity != null } }
            server.learning = json("""{"revision": 3, "learning_mode": "on"}""")
            model.onActivityLearning(true)
            val changed = withTimeout(10.seconds) { model.uiState.first { it.activity?.revision == 3 } }
            assertTrue(checkNotNull(changed.activity).learning)
            assertFalse(changed.changeFailed)
        }
    }

    @Test
    fun `a server with recommendations off shows the interests alone`() = runBlocking {
        val server = RecommendationsServer(preferences = null)
        withModel(server) { model ->
            val shown = withTimeout(10.seconds) { model.uiState.first { !it.loading } }
            withTimeout(10.seconds) {
                while (server.paths.none { it.endsWith("/recommendations/preferences") }) delay(10)
            }
            assertNull(model.uiState.value.activity)
            assertTrue(shown.interests != null)
        }
    }

    private suspend fun withModel(server: RecommendationsServer, check: suspend (InterestsViewModel) -> Unit) {
        val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
        val web = MockWebServer().apply {
            dispatcher = server
            start()
        }
        try {
            val reader = fixture.signIn(web.url("/"))
            check(main.keep(InterestsViewModel(reader.id, fixture.accounts, Interests(fixture.clients))))
        } finally {
            fixture.close()
            web.close()
        }
    }
}

private fun json(body: String): MockResponse =
    MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

/** Interests always; the recommendations preferences when [preferences] is set, and 404 without. */
private class RecommendationsServer(@Volatile var preferences: String?) : Dispatcher() {
    @Volatile var learning: MockResponse = MockResponse.Builder().code(500).build()

    val paths = CopyOnWriteArrayList<String>()

    val changes = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        paths += path
        return when {
            path.endsWith("/api/v1/interests") -> json("""{"settings": {"learning": true}}""")

            path.endsWith("/api/v1/recommendations/preferences") ->
                preferences?.let(::json) ?: MockResponse.Builder().code(404).build()

            path.endsWith("/api/v1/recommendations/learning") -> {
                changes += request.body?.utf8().orEmpty()
                learning
            }

            else -> MockResponse.Builder().code(404).build()
        }
    }
}
