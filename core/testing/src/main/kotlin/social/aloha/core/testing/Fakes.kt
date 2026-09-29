// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.core.DataStore
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.datastore.SecretCipher

/** A DataStore held in memory: the same contract, no file, so tests run on any host. */
public class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)
    override val data: Flow<T> = state

    /** What is stored right now, for assertions. */
    public val value: T get() = state.value

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}

/**
 * Stands in for the Keystore cipher, which does not exist on a JVM. XOR with a one-byte key is not
 * encryption; it only keeps plain text out of the stored bytes so a test can tell. [lose] makes the
 * next decryption fail, as an invalidated Keystore key does.
 */
public class FakeSecretCipher : SecretCipher {
    private var key: Int? = null
    public var lose: Boolean = false

    override fun encrypt(plain: ByteArray): ByteArray {
        val k = key ?: KEY.also { key = it }
        return byteArrayOf(MAGIC) + plain.map { (it.toInt() xor k).toByte() }
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val k = key?.takeUnless { lose } ?: throw GeneralSecurityException("key invalidated")
        if (blob.firstOrNull() != MAGIC) throw GeneralSecurityException("not written with this key")
        return blob.drop(1).map { (it.toInt() xor k).toByte() }.toByteArray()
    }

    override fun reset() {
        key = null
        lose = false
    }

    private companion object {
        const val MAGIC: Byte = 42
        const val KEY = 0x5A
    }
}
