// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.CredentialsUpdate
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class OwnProfileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val asked = CopyOnWriteArrayList<String>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += "${request.method} ${request.url.encodedPath}"
                return MockResponse.Builder().code(500).body("{}").build()
            }
        }
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `a save the server fails is a failure, not looked up again`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val saved = fixture.ownProfile.save(reader, CredentialsUpdate(displayName = "Alice"), false, false)
        assertTrue((saved as Answer.Missed).error is ApiError.Server)
        assertEquals(listOf("PATCH /api/v1/accounts/update_credentials"), asked)
    }
}
