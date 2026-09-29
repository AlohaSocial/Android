// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import java.security.cert.X509Certificate
import java.time.Instant
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.probe.ProbeOutcome

/** Which redirect URI this install can receive; the app decides from its App Link verification state. */
public fun interface RedirectUriProvider {
    public fun redirectUri(): String
}

/** What looking for a server found. */
public sealed interface ServerLookup {
    public data class Found(val server: DiscoveredServer) : ServerLookup

    /** Something answered, but nothing like a Mastodon API; [tried] is every address, in the order of preference. */
    public data class NothingAnswered(val tried: List<TriedAddress>) : ServerLookup

    /** No address got any answer: a typo, a server that is down, or no network. */
    public data object Unreachable : ServerLookup

    /** The server presented a certificate nobody trusts; the person may trust it and look again. */
    public data class UntrustedCertificate(val certificate: ServerCertificate) : ServerLookup

    /** The text cannot be a server address. */
    public data object InvalidAddress : ServerLookup

    /** The address is fine but asks for `http://`, which this build does not accept. */
    public data object InsecureAddress : ServerLookup
}

/**
 * A server whose API was found; hand it back to [SignInCoordinator.beginAuthorization]. Its texts are
 * trimmed: mastodon.social ends its description with blank lines.
 */
public class DiscoveredServer internal constructor(internal val outcome: ProbeOutcome) {
    public val apiBase: String get() = outcome.apiBase.toString()
    public val host: String get() = outcome.apiBase.host
    public val title: String get() = outcome.instance.title.trim().ifBlank { outcome.instance.domain }
    public val domain: String get() = outcome.instance.domain
    public val description: String
        get() = outcome.instance.shortDescription.trim().ifBlank { outcome.instance.description.trim() }
    public val userCount: Int? get() = outcome.instance.userCount
    public val rules: List<String> get() = outcome.instance.rules.map { it.text.trim() }
    public val isNextcloudSocial: Boolean get() = outcome.nodeInfo?.isNextcloudSocial == true
}

/** One address the lookup tried, and why. */
public data class TriedAddress(val url: String, val reason: TriedBecause)

public enum class TriedBecause { Discovery, DomainRoot, AppPath, PrettyAppPath, TypedPath, TypedAppPath, Manual }

/** A certificate a server presented that neither the system nor the person trusts yet. */
public class ServerCertificate internal constructor(
    public val host: String,
    public val subject: String,
    public val issuer: String,
    public val validUntil: Instant,
    public val sha256: String,
    internal val leaf: X509Certificate,
)

/** Why a step of the sign-in did not work, in terms the screen can word. */
public sealed interface SignInProblem {
    /** The server could not be reached. */
    public data object Offline : SignInProblem

    /** The person or the server refused; [reason] is the server's own wording. */
    public data class Refused(val reason: String?) : SignInProblem

    /** The server answered with a problem; [detail] is its own words when it gave some. */
    public data class ServerProblem(val detail: String?) : SignInProblem

    public data class UntrustedCertificate(val certificate: ServerCertificate) : SignInProblem
}

/** How starting the authorisation went. */
public sealed interface Authorization {
    /** Open [url] in the browser. */
    public data class Started(val url: String) : Authorization

    public data class Failed(val problem: SignInProblem) : Authorization
}

/** How finishing a sign-in ended. */
public sealed interface SignInResult {
    public data class SignedIn(val account: SignedInAccount) : SignInResult

    /** The callback did not belong to the sign-in in flight, or none was in flight: nothing was exchanged. */
    public data object NotForUs : SignInResult

    public data class Failed(val problem: SignInProblem) : SignInResult
}
