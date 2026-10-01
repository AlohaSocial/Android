// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AccountForTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext())
    private val accounts = fixture.accounts

    @After
    fun close() = fixture.close()

    private suspend fun signIn() = fixture.signIn("https://social.example/".toHttpUrl())

    @Test
    fun `a named account opens while it is signed in, and nothing once it is gone`() = runBlocking {
        val alice = signIn()
        assertEquals(alice.id, accounts.accountFor(alice.id, shared = false))
        assertNull(accounts.accountFor("signed-out", shared = false))
    }

    @Test
    fun `something shared to an account gone since goes to the account in use`() = runBlocking {
        val alice = signIn()
        assertEquals(alice.id, accounts.accountFor("signed-out", shared = true))
    }

    @Test
    fun `no account named is the account in use, and with none signed in nothing opens later`() = runBlocking {
        assertNull(accounts.accountFor(null, shared = false, loadMillis = 100))
        val alice = signIn()
        assertEquals(alice.id, accounts.accountFor(null, shared = false))
    }

    @Test
    fun `something shared before any sign-in waits for it`() = runBlocking {
        val waiting = async { accounts.accountFor(null, shared = true) }
        val alice = signIn()
        assertEquals(alice.id, waiting.await())
    }
}
