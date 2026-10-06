// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.datastore.IntelligencePreferences
import social.aloha.core.html.RichTextCache
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.intelligence.LanguageModel
import social.aloha.core.intelligence.ModelAvailability
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SummaryViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val mapper = StatusRowMapper(RichTextCache(), RichTextColors(Color.Blue, Color.Gray, Color.LightGray))
    private var read: String? = null

    private val model = object : LanguageModel {
        override suspend fun availability() = ModelAvailability.Available

        override fun prepare() = Unit

        override suspend fun generate(instructions: String, prompt: String): Drafted {
            read = prompt
            return Drafted.Text("They talk about the beach.")
        }
    }

    @Before
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun done() = Dispatchers.resetMain()

    private fun post(id: String, text: String, warning: String = "") = ThreadItem.Post(
        mapper.map(StatusSamples.post(text).copy(id = id, spoilerText = warning), "1", showContext = false),
        focused = id == "f",
        depth = 0,
    )

    private suspend fun summaries(on: Boolean): SummaryViewModel {
        val preferences = IntelligencePreferences(InMemoryDataStore(emptyPreferences()))
        preferences.update { it.copy(summary = on) }
        return SummaryViewModel(Intelligence(context, Optional.of(model), Optional.empty()), preferences)
    }

    @Test
    fun `the thread is read in order, by author, leaving out what the reader's filters or a warning hide`() =
        runBlocking {
            val viewModel = summaries(on = true)
            viewModel.offered.first { it }
            val filtered = post("x", "<p>Something the reader filters</p>").let {
                it.copy(row = it.row.copy(filterWarning = listOf("Politics")))
            }
            viewModel.summarise(
                listOf(post("f", "<p>Surf is up</p>"), filtered, post("r", "<p>Spoiled ending</p>", "Spoilers")),
            )
            assertEquals(
                Summary.Done("They talk about the beach.", 2, 2),
                viewModel.summary.first {
                    it is Summary.Done
                },
            )
            assertEquals("@alice: Surf is up\n\n@alice: Spoilers", read)
            viewModel.dismiss()
            assertEquals(null, viewModel.summary.value)
        }

    @Test
    fun `with Summarise off the model is never asked`() = runBlocking {
        val viewModel = summaries(on = false)
        assertFalse(viewModel.offered.value)
        assertEquals(null, read)
    }
}
