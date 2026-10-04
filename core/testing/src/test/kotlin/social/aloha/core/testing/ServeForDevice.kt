// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Serves the mock to an emulator for the signed-in UI flows (`scripts/ui-flows.sh`), not a test: it runs
 * only with `ALOHA_MOCK_PORT` set, answers until `ALOHA_MOCK_STOP` (a file) appears or an hour passed, and
 * then passes. `adb reverse tcp:PORT tcp:PORT` makes it the emulator's `http://localhost:PORT`, which the
 * debug build may reach in clear text, and the absolute URLs the mock rewrites name the same origin.
 */
class ServeForDevice {
    @Test
    fun serve() {
        val port = System.getenv("ALOHA_MOCK_PORT")?.toIntOrNull()
        assumeTrue(port != null, "set ALOHA_MOCK_PORT to serve the mock to a device")
        val stop = File(System.getenv("ALOHA_MOCK_STOP") ?: "build/mock-server.stop").apply { delete() }
        MockSocialServer(MockServerConfiguration.NextcloudWithRewrite).start(checkNotNull(port)).use {
            // a profile link handed to the app is looked up by whatever handle it carries: bob answers
            it.pin("GET", "/api/v1/accounts/lookup", "api/accounts-lookup-bob.json")
            // the posts the flows open on their own: the corpus has each post, not its context
            for (post in listOf(BOB_POST, VIDEO_POST)) {
                it.pin("GET", "/api/v1/statuses/$post/context", OK, """{"ancestors":[],"descendants":[]}""")
            }
            serveUntil(it, stop)
        }
    }

    /** Answers until [stop] exists or an hour passed, writing what the app asked for next to [stop]. */
    private fun serveUntil(server: MockSocialServer, stop: File) {
        // to see what a failed flow missed
        val log = File(stop.parentFile, "mock-requests.log").apply { writeText("") }
        var logged = 0
        val until = System.currentTimeMillis() + HOUR_MILLIS
        while (!stop.exists() && System.currentTimeMillis() < until) {
            Thread.sleep(POLL_MILLIS)
            val requests = server.requests
            requests.drop(logged).forEach { log.appendText("${it.method} ${it.url}\n") }
            logged = requests.size
        }
    }

    private companion object {
        /** The post `scripts/ui-flows.sh` opens by its link. */
        const val BOB_POST = "1790637117344364158"

        /** The video the flows watch, folded. */
        const val VIDEO_POST = "1790637104958214061"
        const val OK = 200
        const val HOUR_MILLIS = 3_600_000L
        const val POLL_MILLIS = 500L
    }
}
