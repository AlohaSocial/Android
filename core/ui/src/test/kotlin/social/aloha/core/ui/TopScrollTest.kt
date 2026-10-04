// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TopScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private var clock = 0L
    private lateinit var list: LazyListState

    private fun scroller(): TopScroller {
        compose.setContent {
            list = rememberLazyListState(initialFirstVisibleItemIndex = 40)
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(100) { Box(Modifier.height(100.dp)) }
            }
        }
        compose.waitForIdle()
        return TopScroller(list, tail = 300f) { clock }
    }

    private fun TopScroller.tap(): Boolean {
        var moved = false
        compose.runOnIdle { moved = runBlocking { toggle(reduced = true) } }
        return moved
    }

    @Test
    fun `a tap goes to the top, and a second one back to where the reader was`() {
        val scroller = scroller()
        scroller.tap()
        compose.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)

        clock += 60_000
        scroller.tap()
        compose.waitForIdle()
        assertEquals(40, list.firstVisibleItemIndex)
    }

    @Test
    fun `after five minutes at the top, there is nowhere to go back to`() {
        val scroller = scroller()
        scroller.tap()
        clock += 5 * 60_000 + 1
        assertFalse(scroller.tap())
        assertEquals(0, list.firstVisibleItemIndex)
    }
}
