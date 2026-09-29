// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.tls

import java.io.File
import javax.net.ssl.SSLContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Authentication
import social.aloha.core.network.Credentials
import social.aloha.core.network.Endpoint
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.unitRequest

class TlsTest {
    @TempDir
    lateinit var directory: File

    private val selfSigned = HeldCertificate.Builder().addSubjectAlternativeName(
        "localhost",
    ).commonName("localhost").build()
    private val server = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(selfSigned).build().sslSocketFactory())
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun client(store: UserTrustStore): ApiClient {
        val trustManager = AlohaTrustManager(systemTrustManager(), store)
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        val http = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trustManager).build()
        return ApiClient(server.url("/"), { Credentials(null) }, http, RateLimiter(nowMillis = { 0L }), Dispatchers.IO)
    }

    private val ping = unitRequest(Endpoint("api/v1/instance", authentication = Authentication.None))

    @Test
    fun `a self-signed server is reported with its chain, not as a generic failure`() = runTest {
        server.start()
        val error = client(UserTrustStore(File(directory, "t.p12"))).execute(ping)
        val untrusted = assertInstanceOf(ApiError.UntrustedCertificate::class.java, (error as ApiResult.Failure).error)
        assertEquals("localhost", untrusted.host)
        assertEquals(selfSigned.certificate, untrusted.chain.first())
    }

    @Test
    fun `a certificate the person trusted for this host is accepted`() = runTest {
        server.enqueue(MockResponse.Builder().body("{}").build())
        server.start()
        val store = UserTrustStore(File(directory, "t.p12"))
        store.trust("localhost", selfSigned.certificate)
        assertEquals(ApiResult.Success(Unit), client(store).execute(ping))
    }

    @Test
    fun `a certificate trusted for another host does not vouch for this one`() = runTest {
        server.start()
        val store = UserTrustStore(File(directory, "t.p12"))
        store.trust("other.example", selfSigned.certificate)
        assertInstanceOf(ApiResult.Failure::class.java, client(store).execute(ping))
    }

    @Test
    fun `trust survives a restart`() {
        val file = File(directory, "t.p12")
        UserTrustStore(file).trust("Cloud.Example", selfSigned.certificate)
        val reopened = UserTrustStore(file)
        assertTrue(reopened.isTrusted("cloud.example", arrayOf(selfSigned.certificate)))
    }

    @Test
    fun `the fingerprint is colon-separated SHA-256 hex`() {
        assertTrue(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(selfSigned.certificate.sha256Fingerprint()))
    }
}
