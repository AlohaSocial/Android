// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.datastore.IntelligenceChoices
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.intelligence.RewriteStyle
import social.aloha.core.model.CharacterCount

/** A rewrite on its way, offered, or turned down. */
internal sealed interface Rewrite {
    data class Working(val style: RewriteStyle) : Rewrite

    /**
     * [proposal] for post [index] of the thread, which read [original] when it was asked; [pieces] tell
     * the two apart.
     */
    data class Proposed(val index: Int, val original: String, val proposal: String, val pieces: List<Piece>) : Rewrite

    data class Declined(val why: Drafted) : Rewrite
}

/**
 * A draft rewritten on the device, offered beside the original and never put in its place unasked. Only
 * with Rewrite on, and only once the device has the model: until then nothing asks the model anything.
 */
internal class Rewrites(
    private val intelligence: Intelligence,
    choices: Flow<IntelligenceChoices>,
    private val segments: MutableList<TextFieldValue>,
    private val scope: CoroutineScope,
    /** The post being written. */
    private val focus: () -> Int,
    /** How many characters post `index` would have left reading `text`, counted as the composer counts. */
    private val room: (Int, String) -> Int,
) {
    private val shown = MutableStateFlow<Rewrite?>(null)
    private var job: Job? = null

    val state: StateFlow<Rewrite?> = shown.asStateFlow()

    val offered: StateFlow<Boolean> = intelligence.ready(choices.map { it.rewrite })
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_MILLIS), false)

    fun start(style: RewriteStyle) {
        val index = focus()
        val original = segments.getOrNull(index)?.text?.takeIf { it.isNotBlank() } ?: return
        job?.cancel()
        shown.value = Rewrite.Working(style)
        job = scope.launch {
            val limit = CharacterCount.graphemes(original) + room(index, original)
            val drafted = intelligence.rewrite(original, style, limit) { room(index, it) >= 0 }
            shown.value = if (drafted is Drafted.Text) {
                val pieces = withContext(Dispatchers.Default) { wordDiff(original, drafted.text) }
                Rewrite.Proposed(index, original, drafted.text, pieces)
            } else {
                Rewrite.Declined(drafted)
            }
        }
    }

    /** Puts the proposal in place of the post it was made for, if that still reads as it did. */
    fun replace() {
        val proposed = shown.value as? Rewrite.Proposed ?: return
        if (segments.getOrNull(proposed.index)?.text == proposed.original) {
            segments[proposed.index] = TextFieldValue(proposed.proposal, TextRange(proposed.proposal.length))
        }
        dismiss()
    }

    fun dismiss() {
        job?.cancel()
        shown.value = null
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

/** One stretch of a word diff: the same in both, only in the old text, or only in the new one. */
internal data class Piece(val text: String, val kind: Kind) {
    enum class Kind { Same, Removed, Added }
}

/** [old] and [new] compared word by word, spaces kept, in the order a reader meets them. */
internal fun wordDiff(old: String, new: String): List<Piece> {
    val a = TOKENS.findAll(old).map { it.value }.toList()
    val b = TOKENS.findAll(new).map { it.value }.toList()
    val common = common(a, b)
    val pieces = mutableListOf<Piece>()
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        pieces.join(
            when {
                i < a.size && j < b.size && a[i] == b[j] -> Piece(a[i++], Piece.Kind.Same).also { j++ }
                j < b.size && (i == a.size || common[i][j + 1] >= common[i + 1][j]) -> Piece(b[j++], Piece.Kind.Added)
                else -> Piece(a[i++], Piece.Kind.Removed)
            },
        )
    }
    return pieces
}

/** How many tokens [a] from `i` and [b] from `j` have in common, in order, for every `i` and `j`. */
private fun common(a: List<String>, b: List<String>): Array<IntArray> {
    val common = Array(a.size + 1) { IntArray(b.size + 1) }
    // from the ends back, row by row: each cell needs only the ones after it
    for (cell in a.size * b.size - 1 downTo 0) {
        val i = cell / b.size
        val j = cell % b.size
        common[i][j] = if (a[i] == b[j]) common[i + 1][j + 1] + 1 else maxOf(common[i + 1][j], common[i][j + 1])
    }
    return common
}

/** Adds [piece], run together with the last one when they are of a kind. */
private fun MutableList<Piece>.join(piece: Piece) {
    val last = lastOrNull()
    if (last?.kind == piece.kind) set(lastIndex, last.copy(text = last.text + piece.text)) else add(piece)
}

private val TOKENS = Regex("""\s+|\S+""")
