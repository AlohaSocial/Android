// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInMappingTest {
    private val page = "https://cloud.example/apps/social/signup"

    @Test
    fun `an open sign-up with a page is offered, continued into the sign-in on Aloha Social only`() {
        assertEquals(SignUp(page, true, false), signUpOf(page, BASE, true, false))
        assertEquals(SignUp(page, false, true), signUpOf(page, BASE, false, true))
    }

    @Test
    fun `a sign-up that names no usable page is not offered`() {
        assertNull(signUpOf(null, BASE, true, false))
        assertNull(signUpOf("not a link", BASE, true, false))
    }

    @Test
    fun `a page without https is offered only by a server itself reached over plain http, on its own host`() {
        assertNull(signUpOf("http://cloud.example/signup", BASE, true, false))
        assertNull(signUpOf("http://elsewhere.example/signup", BASE, false, false))
        assertEquals("https://join.example/", signUpOf("https://join.example/", BASE, false, false)?.page)
        val local = "http://localhost:8080/signup"
        assertEquals(local, signUpOf(local, "http://localhost:8080/", true, false)?.page)
        assertNull(signUpOf("http://elsewhere.example/signup", "http://localhost:8080/", true, false))
    }

    @Test
    fun `the sign-up page carries the authorization request and the invite, each encoded whole`() {
        val authorization = "https://cloud.example/apps/social/oauth/authorize?response_type=code&client_id=abc" +
            "&redirect_uri=alohasocial%3A%2F%2Foauth-callback%2F&scope=read+write&state=s1"
        val url = signUpUrl(page, authorization, "Tok123").toHttpUrl()
        assertEquals("/apps/social/signup", url.encodedPath)
        assertEquals(authorization, url.queryParameter("return"))
        assertEquals("Tok123", url.queryParameter("invite"))
        assertEquals(listOf("return", "invite"), url.queryParameterNames.toList())
        assertNull(signUpUrl(page, authorization, null).toHttpUrl().queryParameter("invite"))
    }
}

private const val BASE = "https://cloud.example/"
