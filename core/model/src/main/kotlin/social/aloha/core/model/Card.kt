// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/** A link preview. */
@Serializable
public data class Card(
    val url: String? = null,
    val title: String = "",
    val description: String = "",
    val type: String = "link",
    val authorName: String? = null,
    val authorUrl: String? = null,
    val providerName: String? = null,
    val providerUrl: String? = null,
    val image: String? = null,
    val blurhash: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val html: String? = null,
) {
    /** What to print under the headline: the provider, else the link's host. */
    val displayProvider: String
        get() = providerName?.takeIf { it.isNotEmpty() }
            ?: hostOf(url)
            ?: ""
}
