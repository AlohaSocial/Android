// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.profile.FollowedAuthors
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.TimelineSource
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples
import social.aloha.core.ui.RichTextColors

@RunWith(RobolectricTestRunner::class)
class StrangersTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val builder = TimelineRowBuilder(
        RichTextCache(),
        fixture.filters,
        FollowedAuthors(fixture.clients),
        Clock.fixed(StatusSamples.NOW, ZoneOffset.UTC),
    )

    private val carol = Account("3", "carol", "carol@far.example", displayName = "Carol")

    // the fixture signs in as account 6
    private val reader = Account("6", "alice", "alice", displayName = "Alice")

    // bob is followed, carol not yet known, and post 11 is the reader's own
    private val stored = listOf(
        TimelineRow.Post(StatusSamples.post().copy(id = "10", account = StatusSamples.bob)),
        TimelineRow.Post(StatusSamples.post().copy(id = "11", account = reader)),
        TimelineRow.Post(StatusSamples.post().copy(id = "12", account = carol)),
    )

    private fun shape(hide: Boolean) = TimelineRowBuilder.Shape(
        RichTextColors(Color.Blue, Color.Gray, Color.LightGray),
        AccountSettings(hideStrangers = hide),
        filters = emptyList(),
        held = emptySet(),
        loadingGaps = emptySet(),
        follows = mapOf(StatusSamples.bob.id to true),
    )

    @After
    fun close() = fixture.close()

    private val signedIn by lazy { runBlocking { fixture.signIn("https://social.example/".toHttpUrl()) } }

    private fun shown(source: TimelineSource, hide: Boolean) =
        builder.build(signedIn, source, stored, shape(hide)).map { it.key }

    @Test
    fun `the federated timeline shows only those followed and oneself, one not known yet held back and asked about`() {
        assertEquals(listOf("10", "11"), shown(TimelineSource.Federated, hide = true))
        assertEquals(
            setOf(carol.id),
            builder.unknownAuthors(signedIn, TimelineSource.Federated, stored, shape(hide = true)),
        )
    }

    @Test
    fun `home, and a public timeline with the switch off, show everyone`() {
        assertEquals(listOf("10", "11", "12"), shown(TimelineSource.Home, hide = true))
        assertEquals(listOf("10", "11", "12"), shown(TimelineSource.Local, hide = false))
        assertEquals(
            emptySet<String>(),
            builder.unknownAuthors(signedIn, TimelineSource.Home, stored, shape(hide = true)),
        )
    }
}
