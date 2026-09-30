// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.database

import org.junit.Assert.assertTrue
import org.junit.Test

/** Secrets live only in the vault; a column whose name suggests one fails this test. */
class NoSecretsInDatabaseTest {
    private val forbidden = listOf("token", "secret", "password", "verifier", "code")

    @Test
    fun `no entity has a field that could hold a secret`() {
        val fields = listOf(AccountEntity::class.java, ClientRegistrationEntity::class.java, OutboxEntity::class.java)
            .flatMap { entity -> entity.declaredFields.map { "${entity.simpleName}.${it.name}" } }
        val offending = fields.filter { field -> forbidden.any { field.substringAfter('.').lowercase().contains(it) } }
        assertTrue("fields that look like secrets: $offending", offending.isEmpty())
    }
}
