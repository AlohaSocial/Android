// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.probe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HostMetaTest {
    @Test
    fun `host-meta's lrdd link names the server, whatever the order of its attributes`() {
        val hostMeta = """<?xml version="1.0" encoding="UTF-8"?>
            <XRD xmlns="http://docs.oasis-open.org/ns/xri/xrd-1.0">
              <Link type="application/xrd+xml" template="https://social.example.com/.well-known/webfinger?resource={uri}"
                rel="lrdd"/>
            </XRD>"""
        assertEquals("https://social.example.com/", webFingerServer(hostMeta).toString())
        assertNull(webFingerServer("<XRD><Link rel=\"self\" href=\"https://a.example\"/></XRD>"))
        // never an address the app would refuse typed
        for (elsewhere in listOf("http://social.example.com", "https://192.168.1.2", "https://localhost")) {
            assertNull(
                webFingerServer("<Link rel=\"lrdd\" template=\"$elsewhere/.well-known/webfinger?resource={uri}\"/>"),
            )
        }
    }
}
