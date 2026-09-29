// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.tls

import android.content.Context
import android.security.KeyChain
import android.security.KeyChainException
import java.io.File
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.Properties
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Which system KeyChain alias to present as a client certificate for which host. The person picks
 * the alias with `KeyChain.choosePrivateKeyAlias`; the key itself never leaves the KeyChain.
 */
public class ClientCertificateAliases(private val file: File) {
    private val lock = Any()
    private var cached: Properties? = null

    public fun aliasFor(host: String): String? = synchronized(lock) { load().getProperty(key(host)) }

    public fun set(host: String, alias: String?) {
        synchronized(lock) {
            val properties = load()
            if (alias == null) properties.remove(key(host)) else properties.setProperty(key(host), alias)
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, null) }
        }
    }

    // read once: the aliases are consulted in every TLS handshake
    private fun load(): Properties = cached ?: Properties().also { properties ->
        if (file.exists()) file.inputStream().use(properties::load)
        cached = properties
    }

    private fun key(host: String) = host.lowercase(Locale.ROOT)
}

/** Presents the KeyChain certificate chosen for the handshake's host, and nothing for any other host. */
public class ClientCertificateKeyManager(private val context: Context, private val aliases: ClientCertificateAliases) :
    X509ExtendedKeyManager() {
    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = (socket as? SSLSocket)?.handshakeSession?.peerHost?.let(aliases::aliasFor)

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String? = engine?.peerHost?.let(aliases::aliasFor)

    override fun getCertificateChain(alias: String): Array<X509Certificate>? = try {
        KeyChain.getCertificateChain(context, alias)
    } catch (_: KeyChainException) {
        null
    }

    override fun getPrivateKey(alias: String): PrivateKey? = try {
        KeyChain.getPrivateKey(context, alias)
    } catch (_: KeyChainException) {
        null
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}
