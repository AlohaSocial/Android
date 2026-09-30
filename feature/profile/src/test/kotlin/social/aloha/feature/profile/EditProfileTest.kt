// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.model.AccountField
import social.aloha.core.network.ApiError

class EditProfileTest {
    private val original = ProfileForm(displayName = "Alice", note = "Surfs", avatar = Picture.Kept("https://x/a.png"))

    @Test
    fun `nothing changed sends nothing`() {
        assertTrue(changes(original, original, avatar = null, header = null).isEmpty)
    }

    @Test
    fun `only what changed is sent, and every field when one did`() {
        val edited = original.copy(
            note = "Surfs at dawn",
            fields = original.fields.toMutableList().also { it[1] = AccountField("Pronouns", "she/her") },
        )
        val update = changes(original, edited, avatar = null, header = null)
        assertEquals(null, update.displayName)
        assertEquals("Surfs at dawn", update.note)
        assertEquals(ProfileForm.FIELDS, update.fields?.size)
        assertEquals(null, update.locked)
    }

    @Test
    fun `a new name or picture refused is the server keeping them, anything else says why`() {
        val refused = ApiError.Unprocessable("Validation failed")
        assertEquals(SaveFailure.Managed, failureOf(refused, original, original.copy(displayName = "Al")))
        assertEquals(SaveFailure.Managed, failureOf(refused, original, original.copy(avatar = Picture.Removed)))
        assertEquals(SaveFailure.Refused("Validation failed"), failureOf(refused, original, original.copy(note = "x")))
        assertEquals(SaveFailure.Unreached, failureOf(ApiError.NotFound, original, original.copy(note = "x")))
    }
}
