// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.datastore.FocusPreferences
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.Digest
import social.aloha.core.testing.InMemoryDataStore

class FocusModeTest {
    private val store = InMemoryDataStore(emptyPreferences())
    private val reading = ReadingPreferences(store)
    private val notifications = NotificationPreferences(store)
    private val focus = FocusMode(reading, notifications, FocusPreferences(store))

    @Test
    fun `focus turns on the calm set, and off puts back what each was`() = runBlocking {
        // the reader had folded boosts already, and had no digest
        reading.setStyle(reading.style.first().copy(boostCarousel = true))
        focus.set(true)
        val calm = reading.style.first()
        assertFalse(calm.showCounts)
        assertFalse(calm.showTrends)
        assertTrue(calm.boostCarousel)
        assertEquals(Digest.Default, notifications.digest.first())
        assertTrue(focus.on.first())
        focus.set(false)
        val back = reading.style.first()
        assertTrue(back.showCounts)
        assertTrue(back.showTrends)
        // chosen before, so still chosen
        assertTrue(back.boostCarousel)
        assertNull(notifications.digest.first())
        assertFalse(focus.on.first())
    }

    @Test
    fun `what the reader changes while focus is on stays as they changed it`() = runBlocking {
        // the numbers were hidden before focus, and turned on in Reading while it was on
        reading.setStyle(reading.style.first().copy(showCounts = false))
        focus.set(true)
        reading.setStyle(reading.style.first().copy(showCounts = true))
        focus.set(false)
        assertTrue(reading.style.first().showCounts)
    }
}
