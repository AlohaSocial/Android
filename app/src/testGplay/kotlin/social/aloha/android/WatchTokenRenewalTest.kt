// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.HandedOverAccount
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.WatchHandover
import social.aloha.core.testing.InMemoryDataStore

class WatchTokenRenewalTest {
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val sent = mutableListOf<String>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val renewal = WatchTokenRenewal(settings, scope, { sent += it })

    private fun account(id: String) = SignedInAccount(
        id = id,
        host = "social.example",
        apiBase = "https://social.example/",
        serverAccountId = "6",
        handle = "alice",
        displayName = "Alice",
        avatarUrl = null,
        headerUrl = null,
        capabilities = ServerCapabilities.minimal("https://social.example/"),
        needsReauth = false,
        profilePending = false,
        addedAt = Instant.EPOCH,
        nextcloudConnected = false,
    )

    @Test
    fun `a renewed token of an account handed to a watch goes to the watch, without the refresh token`() = runBlocking {
        settings.update("a1") { it.copy(handedToWatch = true) }

        renewal.renewed(account("a1"), old = "old-token", new = "new-token")

        val told = Json.decodeFromString(HandedOverAccount.serializer(), sent.single())
        assertEquals("new-token", told.token)
        assertEquals(WatchHandover.digest("old-token"), told.tokenDigest)
        assertEquals("https://social.example/", told.apiBase)
        assertFalse(sent.single().contains("refresh"))
    }

    @Test
    fun `a renewed token of an account never handed over, or signed out since, goes nowhere`() = runBlocking {
        settings.update("a1") { it.copy(handedToWatch = true) }
        settings.forget("a1")

        renewal.renewed(account("a1"), old = "old-token", new = "new-token")
        renewal.renewed(account("a2"), old = "old-token", new = "new-token")

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `the notice never holds up the renewal, and is given up on a watch that does not answer`() = runBlocking {
        settings.update("a1") { it.copy(handedToWatch = true) }
        val gaveUp = CompletableDeferred<Unit>()
        val stuck = WatchTokenRenewal(settings, scope, {
            try {
                awaitCancellation()
            } finally {
                gaveUp.complete(Unit)
            }
        }, withinMillis = 10)

        withTimeout(WAIT_MILLIS) { stuck.renewed(account("a1"), old = "old-token", new = "new-token") }

        withTimeout(WAIT_MILLIS) { gaveUp.await() }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
