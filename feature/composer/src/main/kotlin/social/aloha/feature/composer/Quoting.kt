// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing

/** What the quote's controls do: drop it, put a note away, and answer whether to quote a followers-only post. */
internal interface QuoteActions {
    fun onDropQuote() {}

    fun onQuoteNoticeShown() {}

    fun onConfirmQuote(dontAsk: Boolean) {}

    fun onCancelQuote() {}
}

/**
 * The post a new one quotes, kept in the composer's [control]. A server that says nothing of quotes
 * gets a mention and a link to the post instead, with room left above them to write. Quoting a quiet
 * public post says once what that does; a post for the people mentioned only turns the quote into a
 * link; quoting someone else's followers-only post asks first, until the writer says not to.
 */
internal class Quoting(
    private val compose: ComposeRepository,
    private val preferences: AppPreferences,
    private val control: MutableStateFlow<ComposerUiState>,
    private val scope: CoroutineScope,
    private val post: () -> Unit,
) : QuoteActions {
    /**
     * Quotes the post [draft] quoted or, with no draft, [quoteId]; a server that takes no quotes gets a link
     * to it instead, written into a new post only, never over a draft's text.
     */
    suspend fun start(
        account: SignedInAccount,
        draft: DraftPost?,
        quoteId: String?,
        segments: SnapshotStateList<TextFieldValue>,
    ) {
        val id = if (draft != null) draft.quotedId else quoteId
        val status = id?.let { (compose.status(account, it) as? Answer.Got)?.value?.displayed } ?: return
        val url = status.url ?: status.uri
        if (!quotes(status, account)) {
            if (draft == null) segments[0] = TextFieldValue("\n\n@${status.account.acct} $url", TextRange(0))
            return
        }
        val quote = QuoteUi(
            status.id,
            status.account.bestDisplayName,
            StatusHtmlParser.plainText(status.content).trim(),
            url,
            status.visibility,
            own = status.account.id == account.serverAccountId,
        )
        val note = QuoteNotice.Unlisted.takeIf {
            quote.visibility == Visibility.Unlisted && !preferences.writing.first().quoteUnlistedNoted
        }
        control.update { it.copy(quote = quote, quoteNotice = note) }
    }

    /** Mentioned only cannot quote: the quoted post becomes a link at the end of the text. */
    fun onVisibility(visibility: Visibility, segments: SnapshotStateList<TextFieldValue>) {
        val quote = control.value.quote?.takeIf { visibility == Visibility.Direct } ?: return
        val text = segments[0].text.trimEnd() + "\n\n" + quote.url
        segments[0] = TextFieldValue(text, TextRange(text.length))
        control.update { it.copy(quote = null, quoteNotice = QuoteNotice.Linked) }
    }

    /** Whether posting waits for the writer to say yes to quoting someone else's followers-only post. */
    suspend fun holds(): Boolean {
        val state = control.value
        val quote = state.quote ?: return false
        val asking = quote.visibility == Visibility.Private && !quote.own && !state.quoteConfirmed &&
            preferences.writing.first().askBeforeFollowersQuote
        if (asking) control.update { it.copy(confirmQuote = true, posting = false) }
        return asking
    }

    override fun onDropQuote() = control.update { it.copy(quote = null, quoteNotice = null) }

    override fun onQuoteNoticeShown() {
        if (control.value.quoteNotice == QuoteNotice.Unlisted) note { it.copy(quoteUnlistedNoted = true) }
        control.update { it.copy(quoteNotice = null) }
    }

    override fun onConfirmQuote(dontAsk: Boolean) {
        if (dontAsk) note { it.copy(askBeforeFollowersQuote = false) }
        control.update { it.copy(confirmQuote = false, quoteConfirmed = true) }
        post()
    }

    override fun onCancelQuote() = control.update { it.copy(confirmQuote = false) }

    private fun note(change: (Writing) -> Writing) {
        scope.launch { preferences.setWriting(change(preferences.writing.first())) }
    }

    /** Whether [status]'s server takes quotes of it: it says who may quote, or it is Nextcloud Social. */
    private fun quotes(status: Status, account: SignedInAccount): Boolean =
        status.quoteApproval != null || status.quoteApprovalPolicy != null || account.capabilities.isNextcloudSocial
}
