// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.translation

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Translation
import social.aloha.core.network.endpoints.StatusEndpoints

/** Posts translated by the reader's server, which hands them to the translation service it uses. */
@Singleton
public class TranslationRepository @Inject constructor(private val clients: ClientFactory) {
    /**
     * Status [statusId] in language [into]. A server without a translation service answers 503 with
     * a sentence saying so (Nextcloud Social: "no translation provider is configured").
     */
    public suspend fun translate(account: SignedInAccount, statusId: String, into: String): Answer<Translation> =
        clients.answer(account, StatusEndpoints.translate(statusId, into))
}

/**
 * Whether a post written in [language] is worth offering to a reader of [readerLanguages] (most preferred
 * first): it is in none of them, and the server translates, from that language into the first when it
 * lists the pairs it can do. A post that names no language is not offered: nothing says it is foreign.
 */
public fun ServerCapabilities.offersTranslation(language: String?, readerLanguages: List<String>): Boolean {
    val from = language?.let(::primary).orEmpty()
    val readers = readerLanguages.map(::primary)
    val into = readers.firstOrNull()
    return translation && from.isNotEmpty() && into != null && from !in readers &&
        (translationLanguages.isEmpty() || translationLanguages[from].orEmpty().any { primary(it) == into })
}

/** `pt-BR` and `pt` are one language to a reader. */
private fun primary(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()
