// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.DataStore
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Where a secret belongs. */
public sealed class VaultKey(internal val name: String) {
    public class AccessToken(accountId: String) : VaultKey("token:$accountId")

    public class AppPassword(accountId: String) : VaultKey("app-password:$accountId")

    public class ClientSecret(host: String) : VaultKey("client-secret:${host.lowercase()}")

    /** The client an account's token was issued to, kept once its server's registration was replaced. */
    public class TokenClient(accountId: String) : VaultKey("token-client:$accountId")

    /** The sign-in in flight: state, PKCE verifier and API base, kept only while the browser tab is open. */
    public data object PendingAuthorization : VaultKey("pending-authorization")
}

/** Encrypts and decrypts the vault blob; the Keystore implementation in production, a fake in tests. */
public interface SecretCipher {
    public fun encrypt(plain: ByteArray): ByteArray

    /** Throws a [GeneralSecurityException] when the key is gone or the blob was not written with it. */
    public fun decrypt(blob: ByteArray): ByteArray

    /** Drops the key, so the next [encrypt] creates a new one. */
    public fun reset()
}

/**
 * The only place secrets live: tokens, client secrets, app passwords and the sign-in in flight. One
 * blob, encrypted with a device-bound AndroidKeyStore key and excluded from backup, so a restored
 * backup can never use it.
 *
 * When the blob cannot be decrypted (an OEM invalidated the key, StrongBox lost it in an OS update)
 * the vault starts again empty and reports it once through [wasLost]: every account then needs a new
 * sign-in, and nothing crashes.
 *
 * The blob is decrypted once per process and kept in memory under the lock; Keystore calls are IPC to
 * the keystore daemon (StrongBox takes far longer), so they run on [ioDispatcher], never the caller's.
 */
public class TokenVault(
    private val store: DataStore<ByteArray>,
    private val cipher: SecretCipher,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var lost = false
    private var secrets: Map<String, String>? = null
    private val json = Json
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    public suspend fun get(key: VaultKey): String? = mutex.withLock { read()[key.name] }

    public suspend fun put(key: VaultKey, value: String) {
        mutex.withLock { write(read() + (key.name to value)) }
    }

    public suspend fun remove(key: VaultKey) {
        mutex.withLock { write(read() - key.name) }
    }

    /** True once after the vault had to start again because its contents could not be read. */
    public suspend fun wasLost(): Boolean = mutex.withLock {
        read()
        lost.also { lost = false }
    }

    private suspend fun read(): Map<String, String> =
        secrets ?: withContext(ioDispatcher) { decrypt() }.also { secrets = it }

    private suspend fun decrypt(): Map<String, String> {
        val blob = store.data.first()
        if (blob.isEmpty()) return emptyMap()
        return try {
            json.decodeFromString(serializer, cipher.decrypt(blob).decodeToString())
        } catch (_: GeneralSecurityException) {
            startAgain()
        } catch (_: SerializationException) {
            startAgain()
        } catch (_: IllegalArgumentException) {
            startAgain()
        }
    }

    private suspend fun startAgain(): Map<String, String> {
        lost = true
        cipher.reset()
        store.updateData { EMPTY }
        return emptyMap()
    }

    private suspend fun write(updated: Map<String, String>) {
        withContext(ioDispatcher) {
            val plain = json.encodeToString(serializer, updated).toByteArray()
            val blob = if (updated.isEmpty()) EMPTY else cipher.encrypt(plain)
            store.updateData { blob }
        }
        secrets = updated
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
