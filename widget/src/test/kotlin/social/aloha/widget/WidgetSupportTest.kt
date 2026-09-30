// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.TimelineSource
import social.aloha.core.navigation.AppIntents

@RunWith(RobolectricTestRunner::class)
class WidgetSupportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a stored timeline reads back, and anything else is the home timeline`() {
        WIDGET_SOURCES.forEach { assertEquals(it, sourceOf(it.storageKey)) }
        assertEquals(TimelineSource.Home, sourceOf(null))
        assertEquals(TimelineSource.Home, sourceOf("list:7"))
    }

    @Test
    fun `each tap opens what it shows, and no two rows share one intent`() {
        val post = AppIntents.open(context, "a1", "s1")
        val other = AppIntents.open(context, "a1", "s2")
        val compose = AppIntents.compose(context, "a1")
        assertEquals(AppIntents.ACTION_OPEN, post.action)
        assertEquals("s1", post.getStringExtra(AppIntents.EXTRA_STATUS))
        assertEquals("a1", post.getStringExtra(AppIntents.EXTRA_ACCOUNT))
        assertEquals(AppIntents.ACTION_COMPOSE, compose.action)
        assertNotEquals(post.data, other.data)
        assertNotEquals(post.data, compose.data)
    }
}
