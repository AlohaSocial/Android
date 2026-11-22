// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.navigation.DraftsKey
import social.aloha.core.navigation.HomeKey
import social.aloha.core.navigation.OwnProfilePlace
import social.aloha.core.navigation.ProfileKey

/** Saved, Drafts and Statistics live on the reader's own profile, and what led to them before leads there. */
class OwnProfileRoutesTest {
    @Test
    fun `the drafts a notification or shortcut opens are the Drafts tab of the reader's own profile`() {
        assertEquals(
            AccountKey("r", id = "6", open = OwnProfilePlace.Drafts),
            DraftsKey("r").asOwnProfile(serverAccountId = "6"),
        )
    }

    @Test
    fun `a draft picked from the drafts the composer opened takes the place of both`() {
        val stack = mutableListOf<NavKey>(HomeKey, ComposerKey("r"), DraftsKey("r"))
        stack.openDraft("r", "d1")
        assertEquals(listOf(HomeKey, ComposerKey("r", draftId = "d1")), stack)
    }

    @Test
    fun `a draft picked on the profile opens over it`() {
        val stack = mutableListOf<NavKey>(HomeKey, ProfileKey)
        stack.openDraft("r", "d1")
        assertEquals(listOf(HomeKey, ProfileKey, ComposerKey("r", draftId = "d1")), stack)
    }

    @Test
    fun `the account sheet's profile is the reader's own, on the tab its account chose`() {
        assertEquals(AccountKey("r", id = "6"), ownProfile("r", "6"))
    }

    @Test
    fun `the account sheet no longer lists what the profile's own tabs hold`() {
        val names = AccountPlace.entries.map { it.name }
        listOf("Bookmarks", "Favourites", "Archived", "Statistics").forEach { assertFalse(it in names) }
    }
}
