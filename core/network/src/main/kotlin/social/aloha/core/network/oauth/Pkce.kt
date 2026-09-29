// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** An RFC 7636 proof key; S256 is the only method sent, Nextcloud Social refuses `plain` with a 400. */
public class Pkce(public val verifier: String) {
    public val challenge: String = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    public val method: String = "S256"

    override fun toString(): String = "Pkce(method=$method)"

    public companion object {
        private const val VERIFIER_BYTES = 64
        private val random = SecureRandom()

        /** 64 random bytes as base64url: 86 characters, inside the RFC's 43 to 128. */
        public fun generate(): Pkce = Pkce(randomToken(VERIFIER_BYTES))

        public fun randomToken(bytes: Int): String = base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
