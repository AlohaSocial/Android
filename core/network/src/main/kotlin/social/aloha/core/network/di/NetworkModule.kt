// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import social.aloha.core.network.AlohaHttp
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.probe.ReprobeGate
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.tls.AlohaTrustManager
import social.aloha.core.network.tls.ClientCertificateAliases
import social.aloha.core.network.tls.ClientCertificateKeyManager
import social.aloha.core.network.tls.UserTrustStore
import social.aloha.core.network.tls.systemTrustManager

/** The dispatcher for blocking I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class IoDispatcher

/** The `User-Agent` every request carries; bound by the app, which knows its version. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class UserAgent

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // Both TLS stores live in no-backup storage: a trust decision belongs to this device.
    @Provides
    @Singleton
    fun userTrustStore(@ApplicationContext context: Context): UserTrustStore =
        UserTrustStore(File(context.noBackupFilesDir, "tls/trusted-certificates.p12"))

    @Provides
    @Singleton
    fun clientCertificateAliases(@ApplicationContext context: Context): ClientCertificateAliases =
        ClientCertificateAliases(File(context.noBackupFilesDir, "tls/client-certificates.properties"))

    @Provides
    @Singleton
    fun okHttpClient(
        @ApplicationContext context: Context,
        @UserAgent userAgent: String,
        trustStore: UserTrustStore,
        aliases: ClientCertificateAliases,
    ): OkHttpClient = AlohaHttp.client(
        cacheDirectory = File(context.cacheDir, "http"),
        userAgent = userAgent,
        trustManager = AlohaTrustManager(systemTrustManager(), trustStore),
        keyManager = ClientCertificateKeyManager(context, aliases),
    )

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun rateLimiter(clock: Clock): RateLimiter = RateLimiter(nowMillis = clock::millis)

    @Provides
    @Singleton
    fun serverProbe(http: OkHttpClient, rateLimiter: RateLimiter, @IoDispatcher io: CoroutineDispatcher): ServerProbe =
        ServerProbe(http, rateLimiter, io)

    @Provides
    @Singleton
    fun oauthClient(http: OkHttpClient, rateLimiter: RateLimiter, @IoDispatcher io: CoroutineDispatcher): OAuthClient =
        OAuthClient(http, rateLimiter, io)

    @Provides
    @Singleton
    fun capabilityDetector(
        http: OkHttpClient,
        rateLimiter: RateLimiter,
        @IoDispatcher io: CoroutineDispatcher,
        clock: Clock,
    ): CapabilityDetector = CapabilityDetector(http, rateLimiter, io, clock::instant)

    @Provides
    @Singleton
    fun reprobeGate(clock: Clock): ReprobeGate = ReprobeGate(nowMillis = clock::millis)
}
