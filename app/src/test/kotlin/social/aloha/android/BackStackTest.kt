// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.ThreadKey

class BackStackTest {
    @Test
    fun `the destination on screen is not opened again, so one back leaves it`() {
        val stack = mutableListOf<NavKey>(HomeKey)
        stack.push(ThreadKey("r", "1"))
        stack.push(ThreadKey("r", "1"))
        assertEquals(listOf(HomeKey, ThreadKey("r", "1")), stack)
    }

    @Test
    fun `another destination, or one further down, still opens`() {
        val stack = mutableListOf<NavKey>(HomeKey, ThreadKey("r", "1"))
        stack.push(ThreadKey("r", "2"))
        stack.push(ThreadKey("r", "1"))
        assertEquals(listOf(HomeKey, ThreadKey("r", "1"), ThreadKey("r", "2"), ThreadKey("r", "1")), stack)
    }
}
