// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.NotificationKind

@RunWith(RobolectricTestRunner::class)
class SmallIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `each kind of notification has a status bar icon of its own that draws`() {
        val kinds = listOf(
            NotificationKind.Favourite,
            NotificationKind.Reblog,
            NotificationKind.Follow,
            NotificationKind.Mention,
            NotificationKind.Poll,
            NotificationKind.Update,
            NotificationKind.ModerationWarning,
        )
        val icons = kinds.map(::smallIcon)
        assertEquals(kinds.size, icons.toSet().size)
        icons.forEach { assertNotNull(ContextCompat.getDrawable(context, it)) }
    }
}
