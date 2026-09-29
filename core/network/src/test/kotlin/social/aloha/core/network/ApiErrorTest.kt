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
