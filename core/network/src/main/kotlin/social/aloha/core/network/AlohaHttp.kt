// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.Cache
import okhttp3.OkHttpClient

/** Builds the one OkHttp client the app shares for the API, images and media. */
public object AlohaHttp {
    private const val CACHE_BYTES = 64L * 1024 * 1024
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 120L

    /**
     * @param cacheDirectory the HTTP cache. Nextcloud Social sends `no-store` on its polling routes,
     *   so the cache only helps where a server sends validators; it revalidates by itself then.
     * @param keyManager presents a client certificate where the person chose one.
     */
    public fun client(
        cacheDirectory: File,
        userAgent: String,
        trustManager: X509TrustManager,
        keyManager: KeyManager?,
    ): OkHttpClient {
        val tls = SSLContext.getInstance("TLS").apply {
            init(keyManager?.let { arrayOf(it) }, arrayOf(trustManager), null)
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trustManager)
            .cache(Cache(cacheDirectory, CACHE_BYTES))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
    }
}
