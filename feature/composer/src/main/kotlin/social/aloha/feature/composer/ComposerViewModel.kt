// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.CharacterCount
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.LengthRule
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.ComposerKey

/**
 * One new post, or a thread of them, written as one of the signed-in accounts. The text lives in
 * Compose state here, so typing never waits on a flow; everything derived from it (what is left of
 * the limit, the games it plays, the completion at the cursor) follows it. [ThreadPoster] sends it.
 */
@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)
internal class ComposerViewModel @AssistedInject constructor(
    @Assisted private val key: ComposerKey,
    @ApplicationContext context: Context,
    private val accounts: AccountRepository,
    private val compose: ComposeRepository,
    private val lookup: RemoteLookup,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: ComposerKey): ComposerViewModel
    }

    /** The segments of the thread as typed; one for a single post. */
    val segments = mutableStateListOf(TextFieldValue())
    var spoiler by mutableStateOf("")
        private set
    private var focused by mutableIntStateOf(0)

    /** What a reply started with (its mentions), which leaving the composer would not lose. */
    private var prefill = ""

    /** Whether leaving would lose something the writer wrote. */
    val hasWriting: Boolean
        get() = spoiler.isNotBlank() || segments.any { it.text.isNotBlank() && it.text.trim() != prefill.trim() }

    private val control = MutableStateFlow(ComposerUiState())
    private val completions = Completions(compose, viewModelScope)
    private val poster = ThreadPoster(compose, gameWords(context))
    private var reader: SignedInAccount? = null
    private var parent: Status? = null
    private var emojiList: List<CustomEmoji> = emptyList()

    private data class Written(val segments: List<String>, val spoiler: String)

    val uiState: StateFlow<ComposerUiState> = combine(
        control,
        snapshotFlow { Written(segments.map { it.text }, spoiler) },
        completions.suggestions,
    ) { state, text, found ->
        val capabilities = reader?.capabilities
        val limits = capabilities?.limits ?: ServerLimits.MastodonDefaults
        val rule = capabilities?.lengthRule ?: LengthRule.Mastodon
        // the content warning goes out with every segment, so it counts in each
        val cw = if (state.spoilerShown) text.spoiler else ""
        state.copy(
            remaining = text.segments.map { CharacterCount.remaining(it, cw, limits, rule) },
            games = text.segments.flatMap(ComposerGames::kinds).distinct(),
            suggestions = found,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ComposerUiState())

    init {
        viewModelScope.launch { start() }
        viewModelScope.launch {
            snapshotFlow { segments.getOrNull(focused)?.let(ComposerText::completing) }.collect { completing ->
                reader?.let { completions.onTyping(it, completing, emojiList) }
            }
        }
    }

    fun onText(index: Int, value: TextFieldValue) {
        if (index !in segments.indices) return
        segments[index] = value
        focused = index
    }

    /** Shows or hides the content warning field; hidden, it is not sent, whatever it holds. */
    fun onSpoiler(shown: Boolean, text: String = spoiler) {
        spoiler = text
        control.update { it.copy(spoilerShown = shown) }
    }

    fun onVisibility(visibility: Visibility) {
        control.update { if (visibility in it.visibilities) it.copy(visibility = visibility) else it }
    }

    fun onLanguage(language: String?) = control.update { it.copy(language = language) }

    fun onQuotePolicy(policy: QuotePolicy) = control.update { it.copy(quotePolicy = policy) }

    /** Replaces the word being completed with [suggestion]. */
    fun onSuggestion(suggestion: Suggestion) {
        val value = segments.getOrNull(focused) ?: return
        val completing = ComposerText.completing(value) ?: return
        segments[focused] = ComposerText.complete(value, completing, suggestion.replacement)
    }

    /** Inserts a custom emoji at the cursor, spaced from what is around it. */
    fun onEmoji(emoji: CustomEmoji) {
        val value = segments.getOrNull(focused) ?: return
        val start = value.selection.min
        val lead = if (start > 0 && !value.text[start - 1].isWhitespace()) " " else ""
        val insert = "$lead:${emoji.shortcode}: "
        segments[focused] = TextFieldValue(
            value.text.replaceRange(start, value.selection.max, insert),
            TextRange(start + insert.length),
        )
    }

    /** Adds a segment after the last, or removes segment [index]; the first and posted ones stay. */
    fun onSegments(index: Int? = null) {
        if (control.value.posting) return
        if (index == null) {
            segments.add(TextFieldValue())
            focused = segments.lastIndex
        } else if (index in 1..segments.lastIndex && index >= poster.posted) {
            segments.removeAt(index)
            poster.forgetFrom(index)
            focused = index - 1
        }
    }

    /** Writes as another signed-in account; a reply is looked up on that account's server first. */
    fun onAuthor(id: String) {
        if (poster.posted > 0 || control.value.posting) return
        viewModelScope.launch {
            val next = accounts.all().firstOrNull { it.id == id } ?: return@launch
            parent?.let { answering ->
                val there = lookup.post(next, answering.url ?: answering.uri)
                    ?.let { (compose.status(next, it) as? Answer.Got)?.value }
                if (there == null) {
                    control.update { it.copy(failure = PostFailure.ReplyNotFound) }
                    return@launch
                }
                parent = there
            }
            use(next)
        }
    }

    fun onPost() {
        val account = reader ?: return
        if (!uiState.value.canPost) return
        control.update { it.copy(posting = true, failure = null) }
        val thread = segments.indices.map(::segmentAt)
        viewModelScope.launch {
            val failure = poster.send(account, thread, parent?.id) { posted ->
                control.update { it.copy(posted = posted) }
            }
            control.update { it.copy(posting = false, failure = failure, done = failure == null) }
            if (failure == null) {
                compose.refreshHome(account)
                compose.rememberTags(account, thread.flatMap { ComposerText.hashtags(it.text) })
            }
        }
    }

    fun onFailureShown() = control.update { it.copy(failure = null) }

    private fun segmentAt(index: Int): Segment {
        val state = control.value
        return Segment(
            text = segments[index].text,
            spoiler = spoiler.trim().takeIf { state.spoilerShown && it.isNotEmpty() },
            visibility = state.visibility,
            language = state.language,
            // who may quote is the opening post's choice; the rest of the thread keeps the default
            quotePolicy = if (index == 0) state.quotePolicy else QuotePolicy.Anyone,
        )
    }

    private suspend fun start() {
        val account = accounts.all().firstOrNull { it.id == key.readerId } ?: return
        key.replyToId?.let { id -> parent = (compose.status(account, id) as? Answer.Got)?.value }
        use(account)
        val preferences = compose.preferences(account)
        control.update { state ->
            val wanted = preferences?.defaultVisibility?.takeIf { it in state.visibilities }
                ?: state.visibilities.first()
            state.copy(
                visibility = wanted,
                language = preferences?.defaultLanguage ?: Locale.getDefault().language.ifEmpty { null },
            )
        }
        parent?.let { status ->
            segments[0] = prefilled(status, account)
            prefill = segments[0].text
        }
        control.update { it.copy(ready = true) }
    }

    /** Makes [account] the one written as: its limits, its emoji, what its server allows. */
    private suspend fun use(account: SignedInAccount) {
        reader = account
        completions.forget()
        val answering = parent?.displayed
        // a reply may not reach further than the post it answers
        val allowed = Visibility.choices.filter {
            answering == null || it.restrictiveness >= answering.visibility.restrictiveness
        }
        val quoting = account.capabilities.quotePosts || account.capabilities.isNextcloudSocial
        control.update { state ->
            state.copy(
                author = account.toAuthor(),
                authors = accounts.all().map { it.toAuthor() },
                reply = answering?.let { ReplyContext("@${it.account.acct}", excerpt(it)) },
                visibilities = allowed,
                visibility = if (state.visibility in allowed) state.visibility else allowed.first(),
                visibilityClamped = allowed.size < Visibility.choices.size,
                quotePolicies = if (quoting) QuotePolicy.entries else emptyList(),
            )
        }
        emojiList = compose.emojis(account)
        control.update { it.copy(emojis = emojiList.filter(CustomEmoji::visibleInPicker)) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

private const val EXCERPT = 140

private fun SignedInAccount.toAuthor() = Author(id, qualifiedHandle, displayName.ifBlank { handle }, avatarUrl)

/** The mentions a reply starts with: the author, then everyone the post mentioned, never oneself. */
private fun prefilled(status: Status, account: SignedInAccount): TextFieldValue {
    val shown = status.displayed
    val handles = (listOf(shown.account.id to shown.account.acct) + shown.mentions.map { it.id to it.acct })
        .filter { (id, _) -> id != account.serverAccountId }
        .map { (_, acct) -> "@$acct" }
        .distinct()
    val text = if (handles.isEmpty()) "" else handles.joinToString(" ", postfix = " ")
    return TextFieldValue(text, TextRange(text.length))
}

private fun excerpt(status: Status): String =
    CharacterCount.prefix(StatusHtmlParser.plainText(status.content).trim(), EXCERPT)

private fun gameWords(context: Context) = ComposerGames.Words(
    heads = context.getString(R.string.composer_game_heads),
    tails = context.getString(R.string.composer_game_tails),
    picked = { choice, options -> context.getString(R.string.composer_game_picked, choice, options) },
)
