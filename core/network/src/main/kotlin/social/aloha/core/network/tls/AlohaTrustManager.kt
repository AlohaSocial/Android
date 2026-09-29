// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.tls

import android.annotation.SuppressLint
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Thrown when a server presents a chain that neither the system nor the person trusts. Carries the
 * host and chain so the sign-in screen can show the certificate and offer to trust it.
 */
public class UntrustedServerCertificateException(
    public val host: String,
    public val chain: List<X509Certificate>,
    cause: CertificateException,
) : CertificateException("Untrusted certificate for $host", cause)

/**
 * The system's trust decision first; a chain it rejects is accepted only when the person trusted its
 * leaf for exactly this host in [userStore]. The host comes from the TLS session, so an accepted
 * certificate never vouches for a different server.
 */
// Deliberately custom: every decision starts with the platform's own trust manager, and the only
// addition is a certificate the person accepted for exactly this host.
@SuppressLint("CustomX509TrustManager")
public class AlohaTrustManager(private val system: X509TrustManager, private val userStore: UserTrustStore) :
    X509ExtendedTrustManager() {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
        verify(chain, host) {
            val extended = system as? X509ExtendedTrustManager
            if (extended != null) {
                extended.checkServerTrusted(chain, authType, socket)
            } else {
                system.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        verify(chain, engine?.peerHost) {
            val extended = system as? X509ExtendedTrustManager
            if (extended != null) {
                extended.checkServerTrusted(chain, authType, engine)
            } else {
                system.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        // Without a session there is no host to match an accepted certificate against.
        system.checkServerTrusted(chain, authType)
    }

    private inline fun verify(chain: Array<out X509Certificate>, host: String?, systemCheck: () -> Unit) {
        try {
            systemCheck()
        } catch (e: CertificateException) {
            if (host != null && userStore.isTrusted(host, chain)) return
            throw UntrustedServerCertificateException(host.orEmpty(), chain.toList(), e)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?): Unit =
        system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?): Unit =
        system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String): Unit =
        system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
