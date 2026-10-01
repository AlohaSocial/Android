// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AppIntents

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shortcuts = AccountShortcuts(context) { null }

    private fun dynamic() = ShortcutManagerCompat.getDynamicShortcuts(context).associateBy { it.id }

    @Test
    fun `each account gets a share target that composes as it, named by its handle`() = runTest {
        shortcuts.publish(listOf(account("a1", "alice")))
        val alone = dynamic().getValue("account:a1")
        assertEquals("a1", alone.intent.getStringExtra(AppIntents.EXTRA_ACCOUNT))
        assertEquals(AppIntents.ACTION_COMPOSE, alone.intent.action)
        assertEquals(setOf("social.aloha.category.SHARE_TARGET"), alone.categories)

        shortcuts.publish(listOf(account("a1", "alice"), account("a2", "bob")))
        val both = dynamic()
        assertEquals("@bob@example.social", both.getValue("account:a2").shortLabel)
    }

    @Test
    fun `a signed-out account's shortcut goes, a conversation's stays`() = runTest {
        val conversation = ShortcutInfoCompat.Builder(context, "conversation:a1:x")
            .setShortLabel("Jane")
            .setIntent(AppIntents.open(context, "a1", null))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, conversation)
        shortcuts.publish(listOf(account("a1", "alice"), account("a2", "bob")))
        shortcuts.publish(listOf(account("a2", "bob")))
        assertEquals(setOf("conversation:a1:x", "account:a2", "compose", "search", "notifications"), dynamic().keys)
    }

    @Test
    fun `the app's own shortcuts come first and name no account`() = runTest {
        shortcuts.publish(listOf(account("a1", "alice")))
        val all = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }.map { it.id }
        assertEquals(listOf("compose", "search", "notifications", "account:a1"), all)
        val compose = dynamic().getValue("compose").intent
        assertEquals(AppIntents.ACTION_COMPOSE, compose.action)
        assertNull(compose.getStringExtra(AppIntents.EXTRA_ACCOUNT))
    }

    @Test
    fun `only an account's shortcut names an account`() {
        assertEquals("a1", AccountShortcuts.accountOf("account:a1"))
        assertNull(AccountShortcuts.accountOf("conversation:a1:x"))
        assertNull(AccountShortcuts.accountOf(null))
    }

    private fun account(id: String, handle: String) = SignedInAccount(
        id = id,
        host = "example.social",
        apiBase = "https://example.social",
        serverAccountId = "1",
        handle = handle,
        displayName = handle,
        avatarUrl = null,
        headerUrl = null,
        capabilities = ServerCapabilities.minimal("4.3.0"),
        needsReauth = false,
        profilePending = false,
        addedAt = Instant.EPOCH,
        nextcloudConnected = false,
    )
}
