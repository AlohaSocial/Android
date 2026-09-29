// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.tls

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Certificates the person accepted for servers the system does not trust, e.g. a self-hosted
 * Nextcloud with a self-signed certificate. Stored as PKCS#12 in app-private storage. A certificate
 * is trusted for the host it was accepted for only, and hostname verification still applies.
 */
public class UserTrustStore(private val file: File) {
    private val lock = Any()
    private var cached: KeyStore? = null

    /** Trusts [certificate] for [host] from now on. */
    public fun trust(host: String, certificate: X509Certificate) {
        synchronized(lock) {
            val store = load()
            store.setCertificateEntry(alias(host, certificate), certificate)
            file.parentFile?.mkdirs()
            file.outputStream().use { store.store(it, PASSWORD) }
            cached = store
        }
    }

    /** Whether [chain]'s leaf was accepted for [host]. */
    public fun isTrusted(host: String, chain: Array<out X509Certificate>): Boolean {
        val leaf = chain.firstOrNull() ?: return false
        val entry = synchronized(lock) { load() }.getCertificate(alias(host, leaf)) as? X509Certificate
        return entry == leaf && leaf.isValidNow()
    }

    private fun X509Certificate.isValidNow(): Boolean = try {
        checkValidity()
        true
    } catch (_: CertificateException) {
        false
    }

    private fun load(): KeyStore = cached ?: KeyStore.getInstance(TYPE).also { store ->
        if (file.exists()) file.inputStream().use { store.load(it, PASSWORD) } else store.load(null, PASSWORD)
        cached = store
    }

    private fun prefix(host: String) = host.lowercase(Locale.ROOT) + "|"

    private fun alias(host: String, certificate: X509Certificate) = prefix(host) + certificate.sha256Fingerprint()

    private companion object {
        const val TYPE = "PKCS12"

        // The file lives in app-private storage; the password only satisfies the PKCS#12 format.
        val PASSWORD = "aloha".toCharArray()
    }
}

/** The SHA-256 fingerprint as colon-separated upper-case hex, the form a person compares. */
public fun X509Certificate.sha256Fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

/** The platform's default trust manager: system CAs only in release builds. */
public fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}
