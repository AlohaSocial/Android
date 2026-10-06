// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.RewriteStyle

/** The toolbar's way to a rewrite: the styles in a menu. */
@Composable
internal fun RewriteMenu(onRewrite: (RewriteStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(AlohaIcons.Intelligence, stringResource(R.string.composer_rewrite))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RewriteStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(style.label)) },
                    onClick = {
                        open = false
                        onRewrite(style)
                    },
                )
            }
        }
    }
}

/**
 * A rewrite under way, then the writer's draft above the proposal with what changed marked in each:
 * Replace puts it in place, Copy only copies it, Cancel leaves the draft as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RewriteSheet(rewrite: Rewrite, onReplace: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        ) {
            when (rewrite) {
                is Rewrite.Working -> Working(onDismiss)
                is Rewrite.Proposed -> Proposed(rewrite, onReplace, onDismiss)
                is Rewrite.Declined -> Declined(rewrite.why, onDismiss)
            }
        }
    }
}

@Composable
private fun Working(onCancel: () -> Unit) {
    Text(
        stringResource(R.string.composer_rewriting),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = onCancel) { Text(stringResource(R.string.composer_rewrite_cancel)) }
}

@Composable
private fun Proposed(rewrite: Rewrite.Proposed, onReplace: () -> Unit, onDismiss: () -> Unit) {
    val removed = removed()
    val added = added()
    val yours = remember(rewrite.pieces, removed) { marked(rewrite.pieces, Piece.Kind.Removed, removed) }
    val theirs = remember(rewrite.pieces, added) { marked(rewrite.pieces, Piece.Kind.Added, added) }
    val spoken = rewrite.proposal + "\n" + changes(rewrite.pieces)
    Heading(stringResource(R.string.composer_rewrite_yours))
    Text(yours, style = MaterialTheme.typography.bodyLarge)
    // the marks are colour and lines; a screen reader hears the proposal and then what changed, as it arrives
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Heading(stringResource(R.string.composer_rewrite_proposal))
        Text(stringResource(R.string.composer_rewrite_generated), color = MaterialTheme.colorScheme.primary)
        Text(
            theirs,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
    ProposedActions(rewrite.proposal, onReplace, onDismiss)
}

@Composable
private fun ProposedActions(proposal: String, onReplace: () -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        Button(onClick = onReplace) { Text(stringResource(R.string.composer_rewrite_replace)) }
        TextButton(onClick = { scope.launch { clipboard.setClipEntry(plain(proposal)) } }) {
            Text(stringResource(R.string.composer_rewrite_copy))
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_rewrite_cancel)) }
    }
}

@Composable
private fun Declined(why: Drafted, onClose: () -> Unit) {
    val message = when (why) {
        Drafted.Refused -> R.string.composer_rewrite_refused
        Drafted.TooLong -> R.string.composer_rewrite_too_long
        else -> R.string.composer_rewrite_failed
    }
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    TextButton(onClick = onClose) { Text(stringResource(R.string.composer_rewrite_close)) }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun removed() = SpanStyle(
    color = MaterialTheme.colorScheme.error,
    textDecoration = TextDecoration.LineThrough,
)

// underlined and bold besides the colour: a background alone hardly shows on Black
@Composable
private fun added() = SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
    textDecoration = TextDecoration.Underline,
)

/** What changed, said in words: each stretch replaced, taken out or put in. */
@Composable
private fun changes(pieces: List<Piece>): String {
    val words = LocalResources.current
    return remember(pieces, words) {
        pieces.indices.mapNotNull { at ->
            val piece = pieces[at]
            val next = pieces.getOrNull(at + 1)
            val before = pieces.getOrNull(at - 1)
            when {
                piece.kind == Piece.Kind.Removed && next?.kind == Piece.Kind.Added ->
                    words.getString(R.string.composer_rewrite_changed, piece.text.trim(), next.text.trim())

                piece.kind == Piece.Kind.Removed -> words.getString(R.string.composer_rewrite_cut, piece.text.trim())

                piece.kind == Piece.Kind.Added && before?.kind != Piece.Kind.Removed ->
                    words.getString(R.string.composer_rewrite_added, piece.text.trim())

                else -> null
            }
        }.joinToString(" ")
    }
}

private fun plain(text: String) = ClipEntry(ClipData.newPlainText("rewrite", text))

/** The text one side of [pieces] reads, with what only it has in [style]. */
private fun marked(pieces: List<Piece>, own: Piece.Kind, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    pieces.filter { it.kind == Piece.Kind.Same || it.kind == own }.forEach { piece ->
        if (piece.kind == own) withStyle(style) { append(piece.text) } else append(piece.text)
    }
}
