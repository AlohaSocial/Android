// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.translation.TranslationRepository
import social.aloha.core.data.translation.foreignLanguage
import social.aloha.core.data.translation.offersTranslation
import social.aloha.core.data.translation.primaryLanguage
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.StatusTranslations
import social.aloha.core.ui.TranslationUi

/**
 * The translations of one signed-in account's shell, which every screen in it shares: a post translated
 * in a timeline reads translated in its thread too. Kept until the account's shell goes.
 *
 * The server translates first. Where it does not, or cannot (a server without a translation service
 * answers 503), the device does, when it has the two languages.
 */
@HiltViewModel
class TranslationsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val translator: TranslationRepository,
    private val device: DeviceTranslator,
) : ViewModel(),
    StatusTranslations {
    private val states = mutableStateMapOf<String, TranslationUi>()
    private val jobs = HashMap<String, Job>()

    // the device's language pairs and whether each is downloaded; asked again before each translation
    private var pairs by mutableStateOf(emptyMap<Pair<String, String>, Boolean>())

    /** The reader's languages, most preferred first: the app's own choice, else the system's. */
    internal var readerLanguages: () -> List<String> = {
        val list = LocaleListCompat.getAdjustedDefault()
        (0 until list.size()).mapNotNull { list[it]?.toLanguageTag() }
    }

    init {
        viewModelScope.launch { pairs = device.pairs() }
    }

    override fun offers(row: StatusRowUi): Boolean {
        val readers = readerLanguages()
        return serverOffers(row, readers) || devicePair(row, readers) in pairs
    }

    override fun stateOf(statusId: String): TranslationUi? = states[statusId]

    override fun translate(row: StatusRowUi) {
        val account = accounts.activeAccount.value ?: return
        val readers = readerLanguages()
        val into = readers.firstOrNull() ?: return
        val id = row.statusId
        if (jobs[id]?.isActive == true) return
        states[id] = TranslationUi.Working
        jobs[id] = viewModelScope.launch {
            val server = if (serverOffers(row, readers)) fromServer(account, id, into) else null
            states[id] = server.takeIf { it is TranslationUi.Done }
                ?: onDevice(row, readers)
                ?: server
                ?: TranslationUi.Failed(null)
        }
    }

    override fun showOriginal(statusId: String) {
        jobs.remove(statusId)?.cancel()
        states.remove(statusId)
    }

    override fun getLanguage() {
        device.openLanguages()
    }

    private fun serverOffers(row: StatusRowUi, readers: List<String>): Boolean =
        accounts.activeAccount.value?.capabilities?.offersTranslation(row.language, readers) == true

    private fun devicePair(row: StatusRowUi, readers: List<String>): Pair<String, String>? =
        foreignLanguage(row.language, readers)?.let { it to primaryLanguage(readers.first()) }

    private suspend fun fromServer(account: SignedInAccount, id: String, into: String): TranslationUi =
        when (val answer = translator.translate(account, id, into)) {
            is Answer.Got -> answer.value.let { TranslationUi.Done(it.content, it.spoilerText, it.provider) }
            is Answer.Missed -> TranslationUi.Failed(reason(answer.error))
        }

    /** What the device makes of it: translated, a language to download first, or null when it cannot. */
    private suspend fun onDevice(row: StatusRowUi, readers: List<String>): TranslationUi? {
        val pair = devicePair(row, readers) ?: return null
        // a language downloaded since the last look counts
        pairs = device.pairs()
        return when (pairs[pair]) {
            null -> null

            false -> TranslationUi.NeedsLanguage(Locale.forLanguageTag(pair.first).displayLanguage)

            true -> device.translate(row.plainText, pair.first, pair.second)
                ?.let { TranslationUi.Done(html(it), spoiler = null, provider = null, onDevice = true) }
        }
    }

    /**
     * The server's own sentence for a refusal: a 503 is how a server without a translation service says
     * so. A page in its place (a proxy's error page) is not a sentence, and is not shown.
     */
    private fun reason(error: ApiError): String? = (error as? ApiError.Server)
        ?.takeIf { it.status == UNAVAILABLE }
        ?.body
        ?.takeUnless { it.isBlank() || it.trimStart().startsWith("<") }
        ?.take(MESSAGE)

    private companion object {
        const val UNAVAILABLE = 503

        // a server's reason is a sentence; anything longer is a page that answered in its place
        const val MESSAGE = 200
    }
}

/** Plain text as the HTML of a post's body: escaped, its line breaks kept. */
internal fun html(text: String): String = "<p>" +
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") +
    "</p>"
