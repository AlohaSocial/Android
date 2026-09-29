// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ClientRegistration
import social.aloha.core.network.oauth.OAuthCallback
import social.aloha.core.network.oauth.PendingAuthorization
import social.aloha.core.network.oauth.Pkce

/** A secret that reaches a string, in a log line or a crash report, must not carry its value. */
class SecretRedactionTest {
    private val secret = "s3cr3t-value-that-must-not-leak"

    @Test
    fun `no value type that holds a secret prints it`() {
        listOf(
            AccessToken(secret, "read"),
            ClientRegistration("client", secret, "read"),
            Credentials(bearerToken = secret, nextcloudBasic = "Basic $secret"),
            PendingAuthorization(
                "https://cloud.example/",
                state = secret,
                verifier = secret,
                redirectUri = "x",
                "",
                "",
                "",
            ),
            OAuthCallback.Code(secret),
            Pkce(secret),
        ).forEach { value -> assertFalse(value.toString().contains(secret), value::class.simpleName) }
    }
}
