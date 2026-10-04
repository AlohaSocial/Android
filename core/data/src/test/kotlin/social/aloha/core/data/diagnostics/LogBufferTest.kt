// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.diagnostics

import java.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.model.LogArea
import timber.log.Timber

class LogBufferTest {
    private val buffer = LogBuffer(Clock.systemUTC()).also { Timber.plant(it) }

    @After
    fun uproot() = Timber.uprootAll()

    @Test
    fun `info and above are kept with level and area, verbose and debug are not`() {
        Timber.tag(LogArea.Network.name).v("verbose")
        Timber.tag(LogArea.Network.name).d("debug")
        Timber.tag(LogArea.Network.name).i("info")
        Timber.tag(LogArea.Sync.name).w("warn")
        Timber.tag(LogArea.Auth.name).e(IllegalStateException("boom"), "error")

        val lines = buffer.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0], lines[0].endsWith(" I/Network: info"))
        assertTrue(lines[1], lines[1].endsWith(" W/Sync: warn"))
        assertTrue(lines[2], " E/Auth: error\njava.lang.IllegalStateException: boom" in lines[2])
    }

    @Test
    fun `the last 500 lines are kept, each cut at 1000 characters`() {
        repeat(LogBuffer.CAPACITY + 20) { Timber.tag(LogArea.App.name).i("line $it ${"x".repeat(2_000)}") }

        val lines = buffer.lines()
        assertEquals(LogBuffer.CAPACITY, lines.size)
        assertTrue(lines.first(), "line 20 " in lines.first())
        assertTrue(lines.last(), "line ${LogBuffer.CAPACITY + 19} " in lines.last())
        assertTrue(lines.all { it.length == LogBuffer.LINE_LIMIT })
    }

    @Test
    fun `secrets are redacted before they are kept`() {
        val secrets = listOf(
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.e30.ZRrHA1JJJW8opsbCGfG_HACGpVUMN_a9IV7pAx_Zmeo",
            "client_secret=Wq8sPz3kVb",
            "code=Xk2pQ9",
            "\"code_verifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"",
            "access_token: 7f3kq9",
            "appPassword=Yx8Lm2Np",
            "bearer abc.def-ghi",
            "https://social.example/oauth/authorize?client_id=Kp4&state=Zz9 failed",
            "token-like 3vT9xQe2Lk8sWp4zRm7nYb6cHa5dJf1g in the middle",
        )
        secrets.forEach { Timber.tag(LogArea.Auth.name).w(it) }

        val kept = buffer.lines().joinToString("\n")
        listOf(
            "eyJhbGci", "Wq8sPz3kVb", "Xk2pQ9", "dBjftJeZ4CVP", "7f3kq9", "Yx8Lm2Np", "abc.def", "Kp4", "Zz9",
            "3vT9xQe2Lk8sWp4zRm7nYb6cHa5dJf1g",
        ).forEach { assertFalse(it, it in kept) }
        assertTrue(kept, "https://social.example/oauth/authorize?[redacted] failed" in kept)
    }

    @Test
    fun `what is no secret stays readable`() {
        val plain = listOf(
            "GET /api/v1/timelines/home → 200 in 84 ms",
            "account 0b6f4a2e-5c3d-4e1f-9a8b-7c6d5e4f3a2b synced",
            "at social.aloha.core.data.timeline.TimelineRepository\$refreshEverythingThatIsStale\$1.invoke",
            "status 113309221445760223 dropped at index 4: SerializationException",
        )

        plain.forEach { assertEquals(it, LogBuffer.redact(it)) }
    }
}
