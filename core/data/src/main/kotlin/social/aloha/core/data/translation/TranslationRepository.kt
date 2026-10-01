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
 * lists the pairs it can do.
 */
public fun ServerCapabilities.offersTranslation(language: String?, readerLanguages: List<String>): Boolean {
    val from = foreignLanguage(language, readerLanguages) ?: return false
    val into = primaryLanguage(readerLanguages.first())
    return translation &&
        (translationLanguages.isEmpty() || translationLanguages[from].orEmpty().any { primaryLanguage(it) == into })
}

/**
 * The language a post is written in, as its primary subtag, when the reader reads none of
 * [readerLanguages] in it; null when they do, or when the post names no language, so nothing says it is
 * foreign.
 */
public fun foreignLanguage(language: String?, readerLanguages: List<String>): String? {
    val from = language?.let(::primaryLanguage)?.takeIf { it.isNotEmpty() } ?: return null
    return from.takeIf { readerLanguages.isNotEmpty() && readerLanguages.none { primaryLanguage(it) == from } }
}

/** `pt-BR` and `pt` are one language to a reader. */
public fun primaryLanguage(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()
