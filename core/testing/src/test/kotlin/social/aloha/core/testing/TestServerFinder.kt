// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.io.File
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.ServerFinder
import social.aloha.core.data.ServerLookup
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.tls.ClientCertificateAliases
import social.aloha.core.network.tls.UserTrustStore

/**
 * A finder with throwaway TLS stores; it accepts `http://` unless told otherwise, as the mock server and
 * the dev instance need.
 */
internal fun testServerFinder(
    http: OkHttpClient,
    limiter: RateLimiter,
    cleartextAllowed: Boolean = true,
): ServerFinder = ServerFinder(
    ServerProbe(http, limiter, Dispatchers.IO),
    UserTrustStore(File.createTempFile("trust", ".p12").apply { delete() }),
    ClientCertificateAliases(File.createTempFile("aliases", ".properties").apply { delete() }),
    cleartextAllowed = cleartextAllowed,
    ioDispatcher = Dispatchers.IO,
)

internal suspend fun ServerFinder.found(typed: String): DiscoveredServer =
    (find(typed) as? ServerLookup.Found)?.server ?: error("nothing found at $typed")
