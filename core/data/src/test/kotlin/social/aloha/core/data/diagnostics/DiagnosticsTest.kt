// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.model.LogArea
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.SignedInFixture
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class DiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val log = LogBuffer(fixture.clock).also { Timber.plant(it) }
    private val diagnostics = Diagnostics(context, AppBuild("1.2.3", "gplay"), fixture.accounts, log, Dispatchers.IO)

    @After
    fun close() {
        Timber.uprootAll()
        fixture.close()
    }

    @Test
    fun `the report names build, servers and log, and no handle, host or token`() = runBlocking {
        val base = "https://social.example/"
        fixture.signIn(
            base.toHttpUrl(),
            ServerCapabilities.minimal(base).copy(softwareName = "mastodon", softwareVersion = "4.4.1"),
        )
        Timber.tag(LogArea.Sync.name).i("Poll of 1 failed: Transport")

        val report = diagnostics.report()

        assertTrue(report, report.startsWith("Aloha Social 1.2.3 (gplay)\nAndroid "))
        assertTrue(report, "\nServers\n- mastodon 4.4.1\n" in report)
        assertTrue(report, report.endsWith(" I/Sync: Poll of 1 failed: Transport\n"))
        listOf("alice", "Alice", "social.example", MockCredentials.ACCESS_TOKEN).forEach {
            assertFalse(it, it in report)
        }
    }

    @Test
    fun `a long log keeps its newest lines within the limit`() = runBlocking {
        repeat(LogBuffer.CAPACITY) { Timber.tag(LogArea.App.name).i("line $it ${"x".repeat(LogBuffer.LINE_LIMIT)}") }

        val report = diagnostics.report()

        assertTrue(report.length <= 200_000)
        assertTrue(report.endsWith("x\n"))
        assertTrue("line ${LogBuffer.CAPACITY - 1} " in report.lines().last { it.isNotEmpty() })
        assertFalse("line 0 " in report)
    }
}
