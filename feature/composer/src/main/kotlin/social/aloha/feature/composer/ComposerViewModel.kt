// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.RemoteLookup
import social.aloha.core.data.Trouble
import social.aloha.core.data.compose.ComposeRepository
import social.aloha.core.data.compose.DraftPost
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.compose.PostSender
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.CharacterCount
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.LengthRule
import social.aloha.core.model.Preferences
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.navigation.ComposerKey
import social.aloha.core.sync.MediaUploads
import social.aloha.core.sync.PostQueue

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
    sender: PostSender,
    outbox: Outbox,
    queue: PostQueue,
    interactions: StatusInteractions,
    @ApplicationScope appScope: CoroutineScope,
    private val lookup: RemoteLookup,
    uploads: MediaUploads,
    mediaRepository: MediaRepository,
    private val preferences: AppPreferences,
    stories: Stories,
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

    /** What a reply or a direct message started with (whom it is for), which leaving would not lose. */
    private var prefill = key.sharedText.takeIf { key.direct }.orEmpty()

    /** Whether leaving would lose something the writer wrote. */
    val hasWriting: Boolean
        get() = spoiler.isNotBlank() || attachments.byPost.value.flatten().isNotEmpty() ||
            segments.any { it.text.isNotBlank() && it.text.trim() != prefill.trim() }

    private val videos = VideoTransformer(context)
    private val preparation = MediaPreparation(
        context.contentResolver,
        File(context.filesDir, Outbox.UPLOADS),
        videos,
        captures = CaptureFiles.authority(context),
    )

    /** The pictures, videos and files of each post; the screen describes and removes them here. */
    val attachments = Attachments(uploads, mediaRepository, preparation, viewModelScope)

    /** Filters, trims and smaller sizes, each uploaded in place of what it changed. */
    val edits = MediaEdits(attachments, preparation, videos, viewModelScope)

    /** GIFs and Nextcloud files the server attaches itself. */
    val library = Library(compose, attachments, viewModelScope) { focused.takeIf { room > 0 } }

    private val control = MutableStateFlow(ComposerUiState())

    /** The post quoted, and what quoting it asks of the writer. */
    val quoting = Quoting(compose, preferences, control, viewModelScope) { onPost() }
    private val completions = Completions(compose, viewModelScope)
    private val poster = ThreadPoster(sender, interactions, gameWords(context))

    /** The post kept as a draft while it is written, and when the composer closes. */
    val drafts = DraftKeeper(outbox, queue, appScope, key.draftId, editing = key.editId != null)
    private val opener = PostOpener(compose, interactions, drafts)

    /** The writer's own post being edited, by its id. */
    private var editing: String? = null

    /** The opening post's poll, which takes the place of its media. */
    val poll = MutableStateFlow<PollUi?>(null)

    /** When the post goes out, if not at once. */
    val scheduledAt = MutableStateFlow<Instant?>(null)
    private var reader: SignedInAccount? = null
    private var parent: Status? = null
    private var emojiList: List<CustomEmoji> = emptyList()

    /** A short post drawn as a picture, when the writer chooses. */
    val cards = TextCards(attachments, File(context.filesDir, Outbox.UPLOADS), viewModelScope)

    /** The post shared as a story instead, where it can be one. */
    val story = StoryShare(stories, attachments, cards, chosen = key.story)

    private val extras = combine(cards.state, poll, scheduledAt, story.chosen, story.seconds, ::Extras)

    /** The post as a draft, when the writer wrote anything. */
    private val written: DraftPost?
        get() = if (hasWriting) {
            poster.keep(
                uiState.value.draft(
                    segments.map {
                        it.text
                    },
                    spoiler,
                    parent?.id,
                ),
            )
        } else {
            null
        }

    private data class Written(val segments: List<String>, val spoiler: String)

    private data class Media(
        val attachments: List<List<Attachment>>,
        val sensitive: Boolean,
        val failure: AttachFailure?,
        val warn: Boolean,
        val edit: EditFailure?,
        val tagShorts: Boolean?,
        val writing: Writing,
    )

    private val media = combine(
        attachments.byPost,
        attachments.sensitive,
        attachments.failure,
        edits.failure,
        combine(preferences.warnMissingDescription, preferences.tagShorts, preferences.writing, ::Triple),
    ) { attachments, sensitive, failure, edit, (warn, tag, writing) ->
        Media(attachments, sensitive, failure, warn, edit, tag, writing)
    }

    val uiState: StateFlow<ComposerUiState> = combine(
        control,
        snapshotFlow { Written(segments.map { it.text }, spoiler) },
        completions.suggestions,
        media,
        extras,
    ) { state, text, found, media, extras ->
        val (card, poll, at) = extras
        val capabilities = reader?.capabilities
        val limits = capabilities?.limits ?: ServerLimits.MastodonDefaults
        val rule = capabilities?.lengthRule ?: LengthRule.Mastodon
        // the content warning goes out with every segment, so it counts in each
        val cw = if (state.spoilerShown) text.spoiler else ""
        // the card is an attachment only to the server; the strip shows what the writer attached
        val attached = media.attachments.map { list -> list.filterNot { it.id == cards.attachmentId } }
        val cardFits = poll == null && TextCards.fits(text.segments.first(), text.segments.size, attached.first().size)
        state.copy(
            remaining = text.segments.mapIndexed { index, segment ->
                val number = numbering(media.writing.numberThreads, index, text.segments.size)
                CharacterCount.remaining(segment + number, cw, limits, rule)
            },
            empty = text.segments.withIndex().any { (index, segment) ->
                segment.isBlank() && media.attachments.getOrElse(index) { emptyList() }.isEmpty() &&
                    !(index == 0 && poll != null)
            },
            games = text.segments.flatMap(ComposerGames::kinds).distinct(),
            suggestions = found,
            attachments = attached,
            card = card,
            cardFits = cardFits,
            storyFits = capabilities?.stories == true &&
                StoryShare.fits(
                    text.segments,
                    attached.flatten(),
                    alone = state.fresh && poll == null && at == null,
                    card = card.on && cardFits,
                ),
            asStory = extras.asStory,
            storySeconds = extras.seconds,
            poll = poll,
            maxPollOptions = limits.maxPollOptions,
            maxPollOptionCharacters = limits.maxPollOptionCharacters,
            pollDurations = PollUi.durations(limits.minPollExpiration, limits.maxPollExpiration),
            scheduledAt = at,
            mediaSensitive = media.sensitive,
            maxAttachments = limits.maxMediaAttachments,
            attachFailure = media.failure,
            editFailure = media.edit,
            tagShorts = media.tagShorts,
            warnMissingDescription = media.warn,
            confirmBeforePosting = media.writing.confirmBeforePosting,
            numberThreads = media.writing.numberThreads,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ComposerUiState())

    init {
        viewModelScope.launch { start() }
        viewModelScope.launch {
            snapshotFlow { segments.first().text }.collect { if (cards.state.value.on) cards.onText(it) }
        }
        viewModelScope.launch {
            // a moment after each change, so a crash or a killed app loses at most that moment
            combine(snapshotFlow { Written(segments.map { it.text }, spoiler) }, uiState) { _, state -> state }
                .filter { it.ready && !it.done }
                .map { written }
                .distinctUntilChanged()
                .collectLatest {
                    delay(AUTOSAVE_MILLIS)
                    drafts.save(reader?.id, it)
                }
        }
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
        if (visibility !in control.value.visibilities) return
        control.update { it.copy(visibility = visibility) }
        quoting.onVisibility(visibility, segments)
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
            attachments.resize(segments.size)
        } else if (index in 1..segments.lastIndex && index >= poster.posted) {
            segments.removeAt(index)
            poster.forgetFrom(index)
            attachments.resize(segments.size, removed = index)
            focused = index - 1
        }
    }

    /** The types the author's server takes, for the file picker; anything when it does not say. */
    val acceptedTypes: Array<String>
        get() = reader?.capabilities?.limits?.supportedMimeTypes?.takeIf { it.isNotEmpty() }?.toTypedArray()
            ?: arrayOf("*/*")

    /**
     * Attaches what the writer picked or recorded to the post being written, as many as it has room
     * for. A short recorded here gets `#shorts` when [tagShort] says so, which is remembered as the
     * writer's choice when [remember]; never without them having chosen it.
     */
    fun onPicked(uris: List<Uri>, tagShort: Boolean = false, remember: Boolean = false) {
        if (room > 0) attachments.add(focused, uris, room)
        if (remember) viewModelScope.launch { preferences.setTagShorts(tagShort) }
        if (tagShort && uris.isNotEmpty()) segments.getOrNull(focused)?.let { segments[focused] = tagged(it) }
    }

    // a poll takes the opening post's place for media
    private val room: Int
        get() = if (focused == 0 && poll.value != null) {
            0
        } else {
            uiState.value.maxAttachments - uiState.value.attachments.getOrElse(focused) { emptyList() }.size
        }

    /** Writes as another signed-in account; a reply is looked up on that account's server first. */
    fun onAuthor(id: String) {
        if (poster.posted > 0 || control.value.posting) return
        viewModelScope.launch {
            val next = accounts.byId(id) ?: return@launch
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
        val account = reader?.takeIf { uiState.value.canPost } ?: return
        control.update { it.copy(posting = true, failure = null) }
        viewModelScope.launch {
            if (quoting.holds()) return@launch
            val state = uiState.value
            if (state.sharesStory) {
                val failure = story.share(account, state, segments.first().text, onShared = drafts::posted)
                control.update { it.copy(posting = false, failure = failure, done = failure == null) }
                return@launch
            }
            if (!cards.ready(state.cardFits, segments.first().text)) {
                control.update { it.copy(posting = false, failure = PostFailure.CardFailed) }
                return@launch
            }
            // the card is attached for the server alone, so the post takes every attachment there is
            val texts = numbered(segments, state.numberThreads)
            val post = state.draft(texts, spoiler, parent?.id, attachments.byPost.value)
            // what was described since uploading goes first: a post must not go out without it
            val failure = if (attachments.sync(account)) {
                poster.send(account, post, editing) { posted -> control.update { it.copy(posted = posted) } }
            } else {
                OFFLINE
            }
            // no network, or what it attaches is not up yet: it goes out later, from the outbox
            if (failure == OFFLINE && drafts.queue(account.id, poster.prepare(post))) {
                control.update { it.copy(posting = false, queued = true, done = true) }
                return@launch
            }
            control.update { it.copy(posting = false, failure = failure, done = failure == null) }
            if (failure == null) {
                drafts.posted()
                compose.refreshHome(account)
                compose.rememberTags(account, post.segments.flatMap { ComposerText.hashtags(it.text) })
            }
        }
    }

    fun onFailureShown() {
        control.update { it.copy(failure = null) }
        attachments.failure.value = null
        edits.onFailureShown()
    }

    override fun onCleared() {
        // a post that went out needs its files no more, nor one given up; a draft keeps them
        attachments.clear(keepFiles = drafts.leave(reader?.id, written))
    }

    private suspend fun start() {
        val account = accounts.byId(key.readerId) ?: return
        val opened = opener.open(account, key)
        if (opened !is Opened.Writing) {
            // ponytail: closes without a word; a post going out shows as sending in the drafts list
            control.update { it.copy(done = true) }
            return
        }
        editing = opened.editing
        val draft = opened.post
        replyId(draft, key, account, lookup)?.let { id -> parent = (compose.status(account, id) as? Answer.Got)?.value }
        use(account)
        val preferences = compose.preferences(account)
        val writing = this.preferences.writing.first()
        control.update { it.withDefaults(preferences, writing, key.direct) }
        if (draft != null) {
            segments.clear()
            segments.addAll(draft.segments.map { TextFieldValue(it.text, TextRange(it.text.length)) })
            spoiler = draft.spoiler.orEmpty()
            poll.value = draft.poll?.toUi()
            scheduledAt.value = draft.scheduledAt
            poster.adopt(draft)
            // an edit's media, and those a redraft takes over, are the original post's: described already
            attachments.restore(draft, attached = editing != null || draft.replaces != null)
            control.update { it.restored(draft, opened.entry, editing != null) }
        } else {
            parent?.let { status ->
                segments[0] = prefilled(status, account)
                prefill = segments[0].text
            }
            // what another app shared starts the post: its text, and the media there is room for
            key.sharedText?.let { text -> segments[0] = TextFieldValue(text, TextRange(text.length)) }
            onPicked(key.sharedMedia.map { it.toUri() })
        }
        quoting.start(account, draft, key.quoteId, segments)
        control.update { it.copy(ready = true) }
    }

    /** Makes [account] the one written as: its limits, its emoji, what its server allows. */
    private suspend fun use(account: SignedInAccount) {
        reader = account
        // a reply by address, as another account, that its server cannot find is no post of its own
        if (key.replyToUrl != null && parent == null) control.update { it.copy(failure = PostFailure.ReplyNotFound) }
        attachments.account = account
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
                gifLibrary = account.capabilities.isNextcloudSocial,
                nextcloudFiles = account.capabilities.mediaFromNextcloudFiles,
            )
        }
        emojiList = compose.emojis(account)
        control.update { it.copy(emojis = emojiList.filter(CustomEmoji::visibleInPicker)) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
        const val AUTOSAVE_MILLIS = 2_000L
    }
}

private const val EXCERPT = 140

/** What stops a post that the outbox then sends. */
private val OFFLINE = PostFailure.Unreached(Trouble.Offline)

/** What the post carries besides its text and media. */
private data class Extras(val card: CardUi, val poll: PollUi?, val at: Instant?, val asStory: Boolean, val seconds: Int)

/**
 * The writer's own defaults from their server: the visibility where it is allowed, and the language. A
 * [direct] message is direct whatever the default.
 */
private fun ComposerUiState.withDefaults(preferences: Preferences?, writing: Writing, direct: Boolean) = copy(
    visibility = Visibility.Direct.takeIf { direct && it in visibilities }
        ?: writing.visibility?.takeIf { it in visibilities }
        ?: preferences?.defaultVisibility?.takeIf { it in visibilities } ?: visibilities.first(),
    language = writing.language ?: preferences?.defaultLanguage ?: Locale.getDefault().language.ifEmpty { null },
    // a warning already written, as in a draft, is open anyway
    spoilerShown = spoilerShown || writing.alwaysShowWarning,
)

/** The number a part of a thread ends in, where the writer numbers threads. */
private fun numbering(on: Boolean, index: Int, size: Int): String = if (on) Writing.numbering(index, size) else ""

/** The post a reply answers: the draft's, the key's, or the one found by the key's address on [account]'s server. */
private suspend fun replyId(
    draft: DraftPost?,
    key: ComposerKey,
    account: SignedInAccount,
    lookup: RemoteLookup,
): String? = draft?.replyToId ?: key.replyToId ?: key.replyToUrl?.let { lookup.post(account, it) }

/** Each segment's text as it goes out, its number after it where the thread is numbered. */
private fun numbered(segments: List<TextFieldValue>, on: Boolean): List<String> = segments.mapIndexed { index, value ->
    val number = numbering(on, index, segments.size)
    if (number.isEmpty()) value.text else value.text.trimEnd() + number
}

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

/** [value] ending in `#shorts`, once, with the cursor after it. */
private fun tagged(value: TextFieldValue): TextFieldValue {
    if (SHORTS_TAG in value.text) return value
    val text = value.text.trimEnd().let { if (it.isEmpty()) SHORTS_TAG else "$it $SHORTS_TAG" }
    return TextFieldValue(text, TextRange(text.length))
}

private const val SHORTS_TAG = "#shorts"
