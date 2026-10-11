// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount

/**
 * Nextcloud 36's WebDAV and OCS as the dev instance answered them, from the `nextcloud-36-dav` corpus,
 * and what a test answers in their place: [handler] is asked first, and a request it leaves is answered
 * from the corpus, or with 404. Every request is kept in [asked].
 */
public class DavServer : Dispatcher() {
    private val corpus = FixtureCorpus("nextcloud-36-dav")
    public val asked: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    @Volatile
    public var handler: (RecordedRequest) -> MockResponse? = { null }

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked += request
        val fixture = corpus.fixtures.firstOrNull { it.method == request.method && it.path == request.url.encodedPath }
        return handler(request) ?: fixture?.response() ?: MockResponse.Builder().code(NOT_FOUND).build()
    }

    /** Answers [method] at [path] with [status] and [body] from now on. */
    public fun answer(method: String, path: String, status: Int, body: String = "") {
        val earlier = handler
        handler = { request ->
            if (request.method == method && request.url.encodedPath == path) {
                MockResponse.Builder().code(status).body(body).build()
            } else {
                earlier(request)
            }
        }
    }

    private fun Fixture.response(): MockResponse {
        val builder = MockResponse.Builder().code(status).body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return builder.build()
    }

    public companion object {
        private const val NOT_FOUND = 404

        /** The app password the tests connect with, alice's. */
        public const val BASIC: String = "Basic YWxpY2U6YXBwLXBhc3N3b3Jk"

        /** Alice, signed in to the Nextcloud Social on [server] at its usual app path. */
        public fun account(server: MockWebServer): SignedInAccount {
            val apiBase = server.url("/index.php/apps/social/").toString()
            return SignedInAccount(
                id = "1",
                host = server.hostName,
                apiBase = apiBase,
                serverAccountId = "6",
                handle = "alice",
                displayName = "Alice",
                avatarUrl = null,
                headerUrl = null,
                capabilities = ServerCapabilities.minimal(apiBase),
                needsReauth = false,
                profilePending = false,
                addedAt = Instant.EPOCH,
                nextcloudConnected = true,
            )
        }
    }
}
