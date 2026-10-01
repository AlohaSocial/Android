// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.runtime.mutableStateMapOf
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.translation.TranslationRepository
import social.aloha.core.data.translation.offersTranslation
import social.aloha.core.network.ApiError
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.StatusTranslations
import social.aloha.core.ui.TranslationUi

/**
 * The translations of one signed-in account's shell, which every screen in it shares: a post translated
 * in a timeline reads translated in its thread too. Kept until the account's shell goes.
 */
@HiltViewModel
class TranslationsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val translator: TranslationRepository,
) : ViewModel(),
    StatusTranslations {
    private val states = mutableStateMapOf<String, TranslationUi>()
    private val jobs = HashMap<String, Job>()

    /** The reader's languages, most preferred first: the app's own choice, else the system's. */
    internal var readerLanguages: () -> List<String> = {
        val list = LocaleListCompat.getAdjustedDefault()
        (0 until list.size()).mapNotNull { list[it]?.toLanguageTag() }
    }

    override fun offers(row: StatusRowUi): Boolean =
        accounts.activeAccount.value?.capabilities?.offersTranslation(row.language, readerLanguages()) == true

    override fun stateOf(statusId: String): TranslationUi? = states[statusId]

    override fun translate(row: StatusRowUi) {
        val account = accounts.activeAccount.value ?: return
        val into = readerLanguages().firstOrNull() ?: return
        val id = row.statusId
        if (jobs[id]?.isActive == true) return
        states[id] = TranslationUi.Working
        jobs[id] = viewModelScope.launch {
            states[id] = when (val answer = translator.translate(account, id, into)) {
                is Answer.Got -> answer.value.let { TranslationUi.Done(it.content, it.spoilerText, it.provider) }
                is Answer.Missed -> TranslationUi.Failed(reason(answer.error))
            }
        }
    }

    override fun showOriginal(statusId: String) {
        jobs.remove(statusId)?.cancel()
        states.remove(statusId)
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
