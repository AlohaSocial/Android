// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import java.security.GeneralSecurityException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenVaultTest {
    /** The bytes the vault hands to DataStore, kept in memory so the test can look at them. */
    private class MemoryStore : DataStore<ByteArray> {
        val bytes = MutableStateFlow(ByteArray(0))
        override val data: Flow<ByteArray> = bytes

        override suspend fun updateData(transform: suspend (t: ByteArray) -> ByteArray): ByteArray =
            transform(bytes.value).also { bytes.value = it }
    }

    /** XOR with a key that can be "lost", which is what an invalidated Keystore key looks like to the vault. */
    private class FakeCipher : SecretCipher {
        var key: Byte? = null
        var lose = false

        override fun encrypt(plain: ByteArray): ByteArray {
            val k = key ?: Random.nextInt(1, 127).toByte().also { key = it }
            return byteArrayOf(MAGIC) + plain.map { (it.toInt() xor k.toInt()).toByte() }
        }

        override fun decrypt(blob: ByteArray): ByteArray {
            val k = key?.takeUnless { lose } ?: throw GeneralSecurityException("key invalidated")
            if (blob.first() != MAGIC) throw GeneralSecurityException("not ours")
            return blob.drop(1).map { (it.toInt() xor k.toInt()).toByte() }.toByteArray()
        }

        override fun reset() {
            key = null
            lose = false
        }

        companion object {
            const val MAGIC: Byte = 42
        }
    }

    private val store = MemoryStore()

    private fun vault(cipher: SecretCipher) = TokenVault(store, cipher, Dispatchers.Unconfined)

    @Test
    fun `secrets round-trip and are not stored in the clear`() = runTest {
        val vault = vault(FakeCipher())
        vault.put(VaultKey.AccessToken("a1"), "token-value")
        vault.put(VaultKey.ClientSecret("Cloud.Example"), "client-secret-value")
        assertEquals("token-value", vault.get(VaultKey.AccessToken("a1")))
        assertEquals("client-secret-value", vault.get(VaultKey.ClientSecret("cloud.example")))
        assertFalse(store.bytes.value.decodeToString().contains("token-value"))
    }

    @Test
    fun `removing a secret leaves the others`() = runTest {
        val vault = vault(FakeCipher())
        vault.put(VaultKey.AccessToken("a1"), "one")
        vault.put(VaultKey.AccessToken("a2"), "two")
        vault.remove(VaultKey.AccessToken("a1"))
        assertNull(vault.get(VaultKey.AccessToken("a1")))
        assertEquals("two", vault.get(VaultKey.AccessToken("a2")))
    }

    @Test
    fun `a lost key empties the vault once, reports it once, and the vault keeps working`() = runTest {
        val cipher = FakeCipher()
        val vault = vault(cipher)
        vault.put(VaultKey.AccessToken("a1"), "one")
        cipher.lose = true
        // the next process reads the blob afresh, and the key is gone
        val restarted = vault(cipher)
        assertNull(restarted.get(VaultKey.AccessToken("a1")))
        assertTrue(restarted.wasLost())
        assertFalse(restarted.wasLost())
        restarted.put(VaultKey.AccessToken("a1"), "again")
        assertEquals("again", restarted.get(VaultKey.AccessToken("a1")))
    }

    @Test
    fun `an empty vault is not a lost vault`() = runTest {
        assertFalse(vault(FakeCipher()).wasLost())
    }

    @Test
    fun `the blob is decrypted once per process, not on every read`() = runTest {
        val inner = FakeCipher()
        var decryptions = 0
        val counting = object : SecretCipher by inner {
            override fun decrypt(blob: ByteArray): ByteArray = inner.decrypt(blob).also { decryptions++ }
        }
        vault(counting).put(VaultKey.AccessToken("a1"), "one")
        decryptions = 0
        val restarted = vault(counting)
        repeat(5) { restarted.get(VaultKey.AccessToken("a1")) }
        assertEquals(1, decryptions)
    }
}
