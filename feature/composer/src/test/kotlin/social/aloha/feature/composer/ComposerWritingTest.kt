// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import java.net.URLDecoder
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.compose.ScheduledPosts
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.OutboxState
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.network.RateLimiter
import social.aloha.core.sync.MediaUploads
import social.aloha.core.sync.PostQueue
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.NumberedTimeline

/** What Settings, Writing changes in the composer. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class ComposerWritingTest : ComposerTestSetup() {
    @Test
    fun `a numbered thread ends each part in its number, which counts in its length`() = runBlocking {
        val viewModel = open(Writing(numberThreads = true))
        viewModel.await { it.ready }
        viewModel.type(0, "one ")
        viewModel.onSegments()
        viewModel.type(1, "two")
        val counted = viewModel.await { it.remaining.size == 2 }
        // "two" and "\n\n2/2": 3 + 5 characters of 5000
        assertEquals(5_000 - 8, counted.remaining[1])
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(listOf("one\n\n1/2", "two\n\n2/2"), posts.sent.map { it.second["status"] })
    }

    @Test
    fun `a single post is never numbered`() = runBlocking {
        val viewModel = open(Writing(numberThreads = true))
        viewModel.await { it.ready }
        viewModel.type(0, "alone ")
        viewModel.await { it.canPost }
        viewModel.onPost()
        viewModel.await { it.done }
        assertEquals(listOf("alone "), posts.sent.map { it.second["status"] })
    }

    @Test
    fun `the writer's own defaults come before the server's`() = runBlocking {
        val viewModel = open(Writing(alwaysShowWarning = true, visibility = Visibility.Unlisted, language = "de"))
        val state = viewModel.await { it.ready }
        assertEquals(Visibility.Unlisted, state.visibility)
        assertEquals("de", state.language)
        assertTrue(state.spoilerShown)
    }
}
