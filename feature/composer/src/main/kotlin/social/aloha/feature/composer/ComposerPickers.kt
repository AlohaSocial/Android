// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.util.Locale
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.CustomEmoji

/** The server's custom emoji by category, each a 48 dp target named by its shortcode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiSheet(emojis: List<CustomEmoji>, onPick: (CustomEmoji) -> Unit, onDismiss: () -> Unit) {
    val other = stringResource(R.string.composer_emoji_other)
    val groups = remember(emojis, other) { emojis.groupBy { it.category ?: other }.toSortedMap() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.composer_emoji_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TARGET),
            modifier = Modifier.fillMaxWidth().heightIn(max = SHEET_HEIGHT),
        ) {
            groups.forEach { (category, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "category-$category") {
                    Text(
                        category,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(AlohaSpacing.s).semantics { heading() },
                    )
                }
                items(list, key = { it.shortcode }) { emoji ->
                    Box(
                        Modifier
                            .size(TARGET)
                            .clickable(role = Role.Button, onClickLabel = ":${emoji.shortcode}:") {
                                onPick(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // the box already says the shortcode; the picture does not say it twice
                        AsyncImage(emoji.url, contentDescription = null, modifier = Modifier.size(EMOJI))
                    }
                }
            }
        }
    }
}

/**
 * Every language Android knows by its name in the reader's own language, the post's current one and
 * the device's first, and "not set" for a post that should not claim one.
 */
@Composable
internal fun LanguageDialog(current: String?, detected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val languages = remember(current) {
        val device = Locale.getDefault().language
        // each name looked up once, not at every comparison of the sort
        val all = Locale.getISOLanguages().map {
            it to languageName(it)?.lowercase()
        }.sortedBy { it.second }.map { it.first }
        (listOfNotNull(current, device) + all).distinct()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composer_language_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = SHEET_HEIGHT).selectableGroup()) {
                if (detected != null) {
                    item(key = "detected") {
                        val name = languageName(detected) ?: detected
                        LanguageRow(stringResource(R.string.composer_language_detected, name), selected = false) {
                            onPick(detected)
                            onDismiss()
                        }
                    }
                }
                item(key = "none") {
                    LanguageRow(stringResource(R.string.composer_language_none), current == null) {
                        onPick(null)
                        onDismiss()
                    }
                }
                items(languages, key = { it }) { code ->
                    LanguageRow(languageName(code) ?: code, code == current) {
                        onPick(code)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_keep_editing)) } },
    )
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        trailingContent = { if (selected) Icon(AlohaIcons.Check, contentDescription = null) },
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** [code]'s name in the reader's language, capitalised as a list item; null for no code. */
internal fun languageName(code: String?): String? = code?.let {
    Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { first -> first.titlecase(Locale.getDefault()) }
        .ifEmpty { null }
}

private val TARGET = 48.dp
private val EMOJI = 32.dp
private val SHEET_HEIGHT = 420.dp

/** A menu item that is the current choice, said as selected rather than only shown by its mark. */
internal fun Modifier.chosen(chosen: Boolean) = semantics { selected = chosen }
