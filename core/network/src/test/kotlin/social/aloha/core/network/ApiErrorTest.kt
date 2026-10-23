// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiErrorTest {
    private fun from(status: Int, body: String = "", headers: Map<String, String> = emptyMap()) =
        ApiError.from(status, errorMessage(body), body, headers::get)

    @Test
    fun `a revoked token is a re-authentication state, not a transient error`() {
        val error = from(401, """{"error":"the access_token was revoked"}""")
        assertEquals(ApiError.Unauthorised("the access_token was revoked"), error)
        assertTrue(error?.requiresReauthentication == true)
        assertFalse(error?.isTransient == true)
    }

    @Test
    fun `a 422's message is preserved for showing verbatim`() {
        assertEquals(
            ApiError.Unprocessable("Validation failed: Text is too long"),
            from(422, """{"error":"Validation failed: Text is too long"}"""),
        )
    }

    @Test
    fun `a refusal's code and the fields of its advice are kept beside the sentence`() {
        val body = """{"error":"this account already has 3 live 24-hour shorts","code":"shorts-full","limit":3}"""
        val error = from(422, body)
        assertEquals(
            ApiError.Unprocessable(
                "this account already has 3 live 24-hour shorts",
                Refusal("shorts-full", mapOf("limit" to "3")),
            ),
            error,
        )
        assertEquals(3, error?.refusal?.number("limit"))
    }

    @Test
    fun `a refusal on another status keeps its code and text fields`() {
        val body = """{"error":"request failed","code":"banner-too-large","limit":"10 MB","extra":null,"list":[1]}"""
        assertEquals(Refusal("banner-too-large", mapOf("limit" to "10 MB")), from(400, body)?.refusal)
        val notSetUp = """{"error":"not set up","code":"account-not-set-up","setup_url":"https://cloud.example/"}"""
        assertEquals(
            Refusal("account-not-set-up", mapOf("setup_url" to "https://cloud.example/")),
            from(403, notSetUp)?.refusal,
        )
    }

    @Test
    fun `an answer without a code, or with one that is not text, carries no refusal`() {
        assertNull(from(422, """{"error":"Validation failed"}""")?.refusal)
        assertNull(from(422, """{"error":"x","code":422}""")?.refusal)
        assertNull(from(422, """{"error":"x","code":""}""")?.refusal)
        assertNull(from(413, "<html>too large</html>")?.refusal)
        assertNull(from(422, "[]")?.refusal)
    }

    @Test
    fun `error_description wins over error`() {
        assertEquals(
            ApiError.Forbidden("nope", insufficientScope = false),
            from(403, """{"error":"x","error_description":"nope"}"""),
        )
    }

    @Test
    fun `an insufficient scope is recognised from WWW-Authenticate`() {
        val error = from(403, headers = mapOf("WWW-Authenticate" to "Bearer error=\"insufficient_scope\""))
        assertEquals(ApiError.Forbidden(null, insufficientScope = true), error)
    }

    @Test
    fun `Retry-After is honoured where the server sent one`() {
        assertEquals(ApiError.RateLimited(30.seconds), from(429, headers = mapOf("Retry-After" to "30")))
        assertEquals(ApiError.RateLimited(null), from(429))
    }

    @Test
    fun `a non-JSON body from a PHP limit becomes the server error body`() {
        assertEquals(ApiError.Server(413, "<html>too large</html>"), from(413, "<html>too large</html>"))
    }

    @Test
    fun `a 2xx is not an error`() {
        assertNull(from(200))
        assertNull(from(204))
    }

    @Test
    fun `5xx and transport failures are transient, 4xx are not`() {
        assertTrue(ApiError.Server(503, null).isTransient)
        assertFalse(ApiError.Server(400, null).isTransient)
        assertFalse(ApiError.NotFound.isTransient)
    }
}
