// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.model.InstanceDescription
import social.aloha.core.network.ApiError
import social.aloha.core.network.di.IoDispatcher
import social.aloha.core.network.probe.CandidateKind
import social.aloha.core.network.probe.ProbeCandidate
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.tls.ClientCertificateAliases
import social.aloha.core.network.tls.UserTrustStore
import social.aloha.core.network.tls.sha256Fingerprint

/**
 * Finds a server's API from what a person typed, and keeps the TLS decisions they make about it: a
 * certificate they trust for that host, a client certificate they present to it.
 */
@Singleton
public class ServerFinder @Inject constructor(
    private val probe: ServerProbe,
    private val trustStore: UserTrustStore,
    private val clientCertificates: ClientCertificateAliases,
    @param:CleartextAllowed private val cleartextAllowed: Boolean,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Looks for the server a person named: a host, a URL, a path or a handle. */
    public suspend fun find(typed: String): ServerLookup {
        val address = ServerAddress.parse(typed, cleartextAllowed) ?: return when {
            !cleartextAllowed && ServerAddress.parse(
                typed,
                allowCleartext = true,
            ) != null -> ServerLookup.InsecureAddress

            else -> ServerLookup.InvalidAddress
        }
        return probe.discover(address).toLookup()
    }

    /** Looks at exactly the API address a person typed by hand. */
    public suspend fun findAt(apiAddress: String): ServerLookup {
        val text = apiAddress.trim().let { if (it.contains("://")) it else "https://$it" }
        val url = text.toHttpUrlOrNull()
        return when {
            url == null -> ServerLookup.InvalidAddress
            !url.isHttps && !cleartextAllowed -> ServerLookup.InsecureAddress
            else -> probe.discover(url).toLookup()
        }
    }

    /** What the server a person is typing says about itself; null while it cannot be an address, or nothing answers. */
    public suspend fun preview(typed: String): InstanceDescription? =
        ServerAddress.parse(typed, cleartextAllowed)?.let { probe.preview(it) }

    /** The host a person named, for the TLS decisions made about it; null when it cannot be an address. */
    public fun hostOf(typed: String): String? = ServerAddress.parse(typed, cleartextAllowed)?.origin?.host

    /** Trusts [certificate] for its host from now on; written to app-private storage off the caller's thread. */
    public suspend fun trust(certificate: ServerCertificate) {
        withContext(ioDispatcher) { trustStore.trust(certificate.host, certificate.leaf) }
    }

    /** Presents the KeyChain certificate [alias] to [host] from now on, or none when [alias] is null. */
    public suspend fun useClientCertificate(host: String, alias: String?) {
        withContext(ioDispatcher) { clientCertificates.set(host, alias) }
    }

    private fun ProbeResult.toLookup(): ServerLookup = when (this) {
        is ProbeResult.Found -> ServerLookup.Found(DiscoveredServer(outcome))

        is ProbeResult.NothingAnswered -> ServerLookup.NothingAnswered(attempted.map { it.toTried() })

        is ProbeResult.Unreachable -> ServerLookup.Unreachable

        is ProbeResult.UntrustedCertificate ->
            error.toCertificate()?.let(ServerLookup::UntrustedCertificate) ?: ServerLookup.NothingAnswered(emptyList())
    }

    private fun ProbeCandidate.toTried(): TriedAddress = TriedAddress(
        url = base.toString(),
        reason = when (kind) {
            CandidateKind.AuthorizationServerIssuer -> TriedBecause.Discovery
            CandidateKind.DomainRoot -> TriedBecause.DomainRoot
            CandidateKind.AppPath -> TriedBecause.AppPath
            CandidateKind.PrettyAppPath -> TriedBecause.PrettyAppPath
            CandidateKind.TypedPath -> TriedBecause.TypedPath
            CandidateKind.TypedAppPath -> TriedBecause.TypedAppPath
            CandidateKind.Manual -> TriedBecause.Manual
        },
    )
}

/** The leaf certificate of an untrusted chain, described for a person, or null without one. */
internal fun ApiError.UntrustedCertificate.toCertificate(): ServerCertificate? {
    val leaf = chain.firstOrNull() ?: return null
    return ServerCertificate(
        host = host,
        subject = leaf.subjectX500Principal.name,
        issuer = leaf.issuerX500Principal.name,
        validUntil = leaf.notAfter.toInstant(),
        sha256 = leaf.sha256Fingerprint(),
        leaf = leaf,
    )
}
