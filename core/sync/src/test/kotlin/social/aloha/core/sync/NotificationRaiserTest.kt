// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.model.Digest
import social.aloha.core.model.QuietHours
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/** Serves one page of notifications, [favourites] favourites strong, and a read marker at 100. */
private class Page : Dispatcher() {
    var favourites = 2
    var favouritesNewest = 120
    var markerFails = false
    var visibility = "public"

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path.endsWith("/markers") ->
                if (markerFails) {
                    MockResponse.Builder().code(500).body("{}").build()
                } else {
                    json("""{"notifications":{"last_read_id":"100"}}""")
                }

            path.endsWith("/api/v2/notifications") -> json(
                """{"accounts":[$ALICE],"statuses":[${POST.replace(
                    "VISIBILITY",
                    visibility,
                )}],"notification_groups":[""" +
                    """{"group_key":"favourite-s1","notifications_count":$favourites,"type":"favourite",""" +
                    """"most_recent_notification_id":"$favouritesNewest",""" +
                    """"sample_account_ids":["1"],"status_id":"s1"},""" +
                    """{"group_key":"ungrouped-110","notifications_count":1,"type":"mention",""" +
                    """"most_recent_notification_id":"110","sample_account_ids":["1"],"status_id":"s1"},""" +
                    """{"group_key":"ungrouped-90","notifications_count":1,"type":"follow",""" +
                    """"most_recent_notification_id":"90","sample_account_ids":["1"]}]}""",
            )

            // nobody followed: a relationship list with no one in it
            path.endsWith("/relationships") -> json("[]")

            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val ALICE = """{"id":"1","username":"alice","acct":"alice","display_name":"Alice"}"""
        const val POST = """{"id":"s1","content":"<p>Surf is up</p>","account":$ALICE,"visibility":"VISIBILITY"}"""
    }
}

@RunWith(RobolectricTestRunner::class)
class NotificationRaiserTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val page = Page()
    private val server = MockWebServer().apply {
        dispatcher = page
        start()
    }
    private val settings = SyncSettings(
        AccountSettingsStore(InMemoryDataStore(emptyMap())),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        NotificationPreferences(InMemoryDataStore(emptyPreferences())),
    )
    private val shade = shadowOf(context.getSystemService(NotificationManager::class.java))

    // noon, whatever the zone the test runs in
    private val noon = Clock.fixed(
        Instant.parse("2026-09-30T12:00:00Z").atZone(ZoneId.systemDefault())
            .withHour(12).toInstant(),
        ZoneId.systemDefault(),
    )

    private fun raiser(clock: Clock = noon) = NotificationRaiser(
        fixture.accounts,
        fixture.notifications,
        fixture.raised,
        settings,
        LocalNotifications(context) { null },
        clock,
    )

    init {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // the app's launcher, which a tap on a notification opens
        val launcher = ComponentName(context, "social.aloha.android.MainActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun signIn() = fixture.signIn(
        server.url("/"),
        ServerCapabilities.minimal(server.url("/").toString()).copy(groupedNotifications = true),
    )

    private fun raised(): List<Notification> =
        shade.allNotifications.filter { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }

    @Test
    fun `what came after the read marker is raised once, and a group that grew again in its place`() = runBlocking {
        val account = signIn()
        val raiser = raiser()
        assertTrue(raiser.onUnreadChanged(account, count = 2))
        val first = raised()
        assertEquals(2, first.size)
        raiser.onUnreadChanged(account, count = 2)
        // nothing was posted again: the very same notifications are still up
        raised().forEach { shown -> assertTrue(first.any { it === shown }) }
        page.favourites = 3
        page.favouritesNewest = 130
        raiser.onUnreadChanged(account, count = 3)
        val favourites = raised().single { it.channelId.endsWith(":favourites") }
        assertEquals("Alice and 2 others favourited your post", favourites.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(2, raised().size)
    }

    @Test
    fun `a mention reads as a message from its author, and can be answered from the shade`() = runBlocking {
        raiser().onUnreadChanged(signIn(), count = 2)
        val mention = raised().single { it.channelId.endsWith(":mentions") }
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(mention)!!
        assertEquals("Surf is up", style.messages.single().text)
        assertEquals("Alice", style.messages.single().person?.name)
        val actions = (0 until NotificationCompat.getActionCount(mention)).map {
            NotificationCompat.getAction(mention, it)!!
        }
        assertTrue(actions.first().remoteInputs!!.isNotEmpty())
        // a mention's third button mutes the thread rather than boosting it
        assertEquals(listOf("Reply", "Favourite", "Mute conversation"), actions.map { it.title.toString() })
        // nothing is done from a locked phone
        assertTrue(actions.all { it.isAuthenticationRequired })
        // a favourite notice is about the person's own post: nothing to favourite or answer there
        val favourites = raised().single { it.channelId.endsWith(":favourites") }
        assertEquals(0, NotificationCompat.getActionCount(favourites))
    }

    @Test
    fun `a private mention shows only that it arrived on a locked screen`() = runBlocking {
        page.visibility = "direct"
        raiser().onUnreadChanged(signIn(), count = 2)
        val mention = raised().single { it.channelId.endsWith(":mentions") }
        assertEquals(Notification.VISIBILITY_PRIVATE, mention.visibility)
        assertEquals("New private mention", mention.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `a read marker that cannot be read raises nothing, and asks to be told again`() = runBlocking {
        page.markerFails = true
        assertFalse(raiser().onUnreadChanged(signIn(), count = 2))
        assertEquals(0, raised().size)
    }

    @Test
    fun `nothing is raised in quiet hours, and it is raised once they end`() = runBlocking {
        val account = signIn()
        settings.setQuietHours(QuietHours(fromHour = 11, untilHour = 13))
        assertFalse(raiser().onUnreadChanged(account, count = 2))
        assertEquals(0, raised().size)
        settings.setQuietHours(null)
        raiser().onUnreadChanged(account, count = 2)
        assertEquals(2, raised().size)
    }

    @Test
    fun `with the digest on, a private mention is raised at once and the rest wait for one summary`() = runBlocking {
        val account = signIn()
        settings.setDigest(Digest(hours = listOf(8, 18)))
        page.visibility = "direct"
        val raiser = raiser()
        assertTrue(raiser.onUnreadChanged(account, count = 3))
        assertEquals(listOf(":mentions"), raised().map { it.channelId.substringAfter(account.id) })
        raiser.digest()
        val digest = raised().single { it.channelId.endsWith(":digest") }
        // the follow at 90 is below the read marker at 100, so the two favourites are all that waited
        assertEquals("2 new notifications", digest.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("2 favourites", digest.extras.getString(Notification.EXTRA_TEXT))
        // the digest said it: a second one, and the next poll, add nothing
        raiser.digest()
        raiser.onUnreadChanged(account, count = 3)
        assertEquals(2, raised().size)
    }

    @Test
    fun `without personal mentions at once, the digest holds every notification`() = runBlocking {
        val account = signIn()
        settings.setDigest(Digest(hours = listOf(8), personalNow = false))
        page.visibility = "direct"
        assertTrue(raiser().onUnreadChanged(account, count = 3))
        assertEquals(0, raised().size)
    }

    @Test
    fun `forgetting an account takes its notifications, channels and conversations away`() = runBlocking {
        val account = signIn()
        raiser().onUnreadChanged(account, count = 2)
        assertTrue(
            ShortcutManagerCompat.getDynamicShortcuts(context).any {
                it.id.startsWith("conversation:${account.id}:")
            },
        )
        LocalNotifications(context) { null }.forget(account.id)
        assertEquals(0, shade.allNotifications.size)
        assertTrue(
            ShortcutManagerCompat.getDynamicShortcuts(context).none {
                it.id.startsWith("conversation:${account.id}:")
            },
        )
        assertTrue(
            context.getSystemService(NotificationManager::class.java).notificationChannelGroups.none {
                it.id ==
                    "account:${account.id}"
            },
        )
    }
}
