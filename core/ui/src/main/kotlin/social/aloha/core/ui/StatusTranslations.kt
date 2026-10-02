// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser

/**
 * Translating posts into the reader's language, for every screen that shows them: the card asks here
 * whether to offer it, and draws the translation in place of the post until the reader asks for the
 * original back. Translations are kept for as long as the reader's account is in use.
 */
@Stable
public interface StatusTranslations {
    /** Whether [row] is worth offering to translate: written in another language, to a server that can. */
    public fun offers(row: StatusRowUi): Boolean

    /** What came of asking for [statusId]'s translation; null when nobody asked, or after [showOriginal]. */
    public fun stateOf(statusId: String): TranslationUi?

    public fun translate(row: StatusRowUi)

    public fun showOriginal(statusId: String)

    /** Opens the system's settings where the language a [TranslationUi.NeedsLanguage] names is downloaded. */
    public fun getLanguage(): Unit = Unit
}

/** A translation asked for: on its way, arrived, waiting for a language to download, or refused. */
@Immutable
public sealed interface TranslationUi {
    public data object Working : TranslationUi

    /**
     * [content] is HTML, as the post's own; [provider] names the service the server used, when it says.
     * [onDevice] when the device translated it, and nothing was sent anywhere.
     */
    public data class Done(
        val content: String,
        val spoiler: String?,
        val provider: String?,
        val onDevice: Boolean = false,
    ) : TranslationUi

    /** Only the device can translate it, once [language] (its name, to show) is downloaded to it. */
    public data class NeedsLanguage(val language: String) : TranslationUi

    /** [message] is the server's own words, shown as they are, when it gave any. */
    public data class Failed(val message: String?) : TranslationUi
}

/** Offers nothing: where no account is signed in, and in previews and tests. */
public object NoTranslations : StatusTranslations {
    override fun offers(row: StatusRowUi): Boolean = false

    override fun stateOf(statusId: String): TranslationUi? = null

    override fun translate(row: StatusRowUi): Unit = Unit

    override fun showOriginal(statusId: String): Unit = Unit
}

public val LocalStatusTranslations: ProvidableCompositionLocal<StatusTranslations> =
    staticCompositionLocalOf { NoTranslations }

/** [row] as the reader reads it: its translation, when one has arrived, in place of what was written. */
@Composable
internal fun translated(row: StatusRowUi, state: TranslationUi?): StatusRowUi {
    val done = state as? TranslationUi.Done ?: return row
    val colors = RichTextColors.fromTheme()
    return remember(row, done, colors) {
        val body = StatusHtmlParser.parse(done.content, emojis = row.emojis)
        row.copy(
            body = body.toAnnotatedString(colors),
            plainText = body.plainText,
            spoiler = done.spoiler?.takeIf { row.spoiler != null && it.isNotBlank() }
                ?.let { StatusHtmlParser.parseText(it, row.emojis).toAnnotatedString(colors) }
                ?: row.spoiler,
        )
    }
}

/** Under a translated post: who translated it and the way back; or that it is coming, or why it is not. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranslationLine(state: TranslationUi, onShowOriginal: () -> Unit, onGetLanguage: () -> Unit) {
    // the button moves under the words where both do not fit, so a large font never breaks a word
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            Icon(
                AlohaIcons.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SMALL_ICON),
            )
            Text(
                translationText(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            TranslationUi.Working -> Unit

            is TranslationUi.NeedsLanguage ->
                TextButton(onClick = onGetLanguage) { Text(stringResource(R.string.status_translation_get_language)) }

            is TranslationUi.Done ->
                TextButton(onClick = onShowOriginal) { Text(stringResource(R.string.status_show_original)) }

            is TranslationUi.Failed -> TextButton(onClick = onShowOriginal) {
                Text(stringResource(R.string.status_dismiss))
            }
        }
    }
}

/** What [TranslationLine] says, which a screen reader hears at the end of the post. */
@Composable
internal fun translationText(state: TranslationUi): String = when (state) {
    TranslationUi.Working -> stringResource(R.string.status_translating)

    is TranslationUi.Done -> when {
        state.onDevice -> stringResource(R.string.status_translated_on_device)
        state.provider != null -> stringResource(R.string.status_translated_by, state.provider)
        else -> stringResource(R.string.status_translated)
    }

    is TranslationUi.NeedsLanguage -> stringResource(R.string.status_translation_needs_language, state.language)

    is TranslationUi.Failed -> state.message?.let { stringResource(R.string.status_translation_refused, it) }
        ?: stringResource(R.string.status_translation_failed)
}
