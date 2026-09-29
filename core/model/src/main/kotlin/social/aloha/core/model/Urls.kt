// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.net.URI
import java.net.URISyntaxException

/** The host of [url], or null when there is none or the text is not a URL. */
internal fun hostOf(url: String?): String? = try {
    url?.let { URI(it).host }
} catch (_: URISyntaxException) {
    null
}
