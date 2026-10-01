// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.navigation.AppIntents

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutsideRequestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val own = context.packageName

    private fun send(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun `the launcher's shortcuts name no account and ask for the account in use`() {
        listOf(AppIntents.ACTION_COMPOSE, AppIntents.ACTION_SEARCH, AppIntents.ACTION_OPEN).forEach {
            val intent = AppIntents.asActiveAccount(context, it)
            val request = OutsideRequest.of(intent, own)
            assertEquals(
                it,
                when (it) {
                    AppIntents.ACTION_COMPOSE -> OutsideRequest.Compose(null)
                    AppIntents.ACTION_SEARCH -> OutsideRequest.Search(null)
                    else -> OutsideRequest.Open(null, null, null)
                },
                request,
            )
        }
    }

    @Test
    fun `an account's shortcut and a notification name their account`() {
        assertEquals(OutsideRequest.Compose("a1"), OutsideRequest.of(AppIntents.compose(context, "a1"), own))
        assertEquals(
            OutsideRequest.Open("a1", "s1", "p1"),
            OutsideRequest.of(AppIntents.open(context, "a1", "s1", "p1"), own),
        )
    }

    @Test
    fun `a Direct Share names its account, a plain share none, and another shortcut never`() {
        val direct = send("Aloha").putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, "account:a2")
        assertEquals("a2", (OutsideRequest.of(direct, own) as OutsideRequest.Share).accountId)
        assertNull((OutsideRequest.of(send("Aloha"), own) as OutsideRequest.Share).accountId)
        val other = send("Aloha").putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, "conversation:a1:x")
        assertNull((OutsideRequest.of(other, own) as OutsideRequest.Share).accountId)
    }

    @Test
    fun `Open in Aloha hands over the first web address, without the sentence around it`() {
        val alias = ComponentName(own, OutsideRequest.OPEN_IN_ALOHA)
        val shared = send("Look (https://mastodon.social/@alice/117355).").setComponent(alias)
        assertEquals(
            OutsideRequest.Link("https://mastodon.social/@alice/117355", handedOver = true),
            OutsideRequest.of(shared, own),
        )
        assertNull(OutsideRequest.of(send("no address here").setComponent(alias), own))
        // the same text through the app's own share target is a new post, not a link
        assertEquals(OutsideRequest.Share::class, OutsideRequest.of(send("https://x.example/a"), own)!!::class)
    }

    @Test
    fun `a link another app opens is an ordinary link, and anything else is nothing`() {
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("web+ap://mastodon.social/@alice"))
        assertEquals(OutsideRequest.Link("web+ap://mastodon.social/@alice"), OutsideRequest.of(view, own))
        assertNull(OutsideRequest.of(Intent(Intent.ACTION_MAIN), own))
    }
}
