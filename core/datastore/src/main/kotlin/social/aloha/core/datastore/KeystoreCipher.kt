// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that never leaves the AndroidKeyStore, in StrongBox where the device has
 * one. No user authentication is required, so background work can read the tokens. The blob is the
 * IV length, the IV and the ciphertext with its tag.
 */
internal class KeystoreCipher(private val alias: String = "aloha-vault") : SecretCipher {
    // loaded on first use, on the vault's I/O dispatcher, not when the vault is created
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    override fun encrypt(plain: ByteArray): ByteArray = guarded {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray = guarded {
        val ivLength = blob.firstOrNull()?.toInt() ?: throw GeneralSecurityException("empty blob")
        if (ivLength <= 0 || blob.size <= 1 + ivLength) throw GeneralSecurityException("truncated blob")
        val key = keyStore.getKey(alias, null) as? SecretKey ?: throw GeneralSecurityException("vault key is gone")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, ivLength))
        cipher.doFinal(blob, 1 + ivLength, blob.size - 1 - ivLength)
    }

    override fun reset() {
        guarded { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    private fun key(): SecretKey = keyStore.getKey(alias, null) as? SecretKey
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) generateInStrongBox() else generate(strongBox = false)

    /** StrongBox where the device has one; a device without it says so with an exception, and gets a TEE key. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun generateInStrongBox(): SecretKey = try {
        generate(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    /** Keystore failures surface as runtime exceptions on some devices; the vault expects checked ones. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ProviderException) {
        throw GeneralSecurityException(e)
    } catch (e: IllegalStateException) {
        throw GeneralSecurityException(e)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
