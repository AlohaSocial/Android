// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Visibility

/** What a row asks for; the screen that shows it decides what each does. */
@Immutable
public interface StatusActions {
    /** The thread of [statusId]: the post shown, or a quoted one. */
    public fun onOpen(statusId: String)

    public fun onProfile(accountId: String)

    public fun onLink(target: RichLinkTarget)

    public fun onMedia(row: StatusRowUi, index: Int)

    public fun onReply(row: StatusRowUi)

    public fun onBoost(row: StatusRowUi)

    public fun onFavourite(row: StatusRowUi)

    public fun onBookmark(row: StatusRowUi)

    public fun onVote(row: StatusRowUi, choices: List<Int>)

    public fun onReact(row: StatusRowUi, name: String, add: Boolean)

    public fun onMenu(row: StatusRowUi, item: StatusMenuItem)

    /** The edits of [row], from its "edited" mark; without a screen of its own for them, the post. */
    public fun onHistory(row: StatusRowUi): Unit = onOpen(row.statusId)

    /** The menu items this screen can carry out; the others are not offered. */
    public val menu: Set<StatusMenuItem> get() = StatusMenuItem.entries.toSet()
}

/** The overflow menu. The author's own items appear only on their own posts. */
public enum class StatusMenuItem {
    Share,
    CopyLink,
    OpenInBrowser,
    Translate,
    ShowOriginal,
    TranslationLanguage,
    MuteConversation,
    Report,
    Edit,
    Delete,
    Redraft,
    Pin,
    AddToAlbum,
    Archive,
    OpenInNewWindow,
}

/**
 * One post. The whole row is a single element for a screen reader: it reads who, when and what was
 * said, and offers the actions as custom actions rather than a stop per button. Every toggled action
 * draws a different glyph when on, so its state never rests on colour alone.
 *
 * @param now the time ages are counted from; the list passes one clock so every row agrees.
 * @param canReact whether the server takes emoji reactions (shown in a thread, where they are fetched).
 * @param focused the post a thread is about: larger text, its content the full width of the card rather
 *   than beside the avatar, and no tap to open it, since it is open.
 */
@Composable
public fun StatusCard(
    post: StatusRowUi,
    now: Instant,
    policy: SensitiveMediaPolicy,
    screenActions: StatusActions,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    canReact: Boolean = false,
    animateEmoji: Boolean = true,
    focused: Boolean = false,
) {
    // a translation takes the post's place, everywhere the card reads it: its text, its label, its links
    val translations = LocalStatusTranslations.current
    val translation = translations.stateOf(post.statusId)
    val translatedRow = translated(post, translation)
    val offered = translation == null && translations.offers(post)
    val actions = remember(screenActions, translations, offered, translation) {
        TranslatingActions(screenActions, translations, offered, translation)
    }
    val hiding = rememberHiding(translatedRow, focused)
    val warning = translatedRow.filterWarning
    if (warning != null && !hiding.filterRevealed) {
        FilteredPlaceholder(warning, onReveal = hiding.onFilterReveal, modifier = modifier)
        return
    }
    val row = withMatchesPainted(translatedRow, warning != null)
    var pollChoice by rememberSaveable(row.rowId) { mutableStateOf(listOf<Int>()) }
    val bodyShown = row.spoiler == null || hiding.spoilerRevealed
    val label = listOfNotNull(accessibilityLabel(row, now, bodyShown), translation?.let { translationText(it) })
        .joinToString(". ")
    val controls = CardControls(
        hiding.spoilerRevealed,
        hiding.onSpoiler,
        pollChoice,
        { pollChoice = it },
        translation,
        { translations.showOriginal(row.statusId) },
        translations::getLanguage,
        hiding.collapse,
        hiding.rehide(row),
    )
    val customActions = customActions(row, actions, controls)
    val compact = LocalReadingStyle.current.compact
    ProvideLinkRouting(onLink = actions::onLink) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .opens(label, customActions, onOpen = { actions.onOpen(row.statusId) }.takeUnless { focused })
                .padding(horizontal = AlohaSpacing.m, vertical = if (compact) AlohaSpacing.xs else AlohaSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            row.context?.let { ContextLineRow(it, now) }
            CardLayout(row, now, policy, actions, Flags(showActions, canReact, animateEmoji, focused), controls)
        }
    }
}

/**
 * The avatar beside the header, and the content under the name, so text, media, poll and cards hang in
 * one column; a focused post takes the full width under the header instead.
 */
@Composable
private fun CardLayout(
    row: StatusRowUi,
    now: Instant,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    flags: Flags,
    controls: CardControls,
) {
    val avatar = @Composable {
        Avatar(row.author.avatarUrl, AVATAR, Modifier.clickable { actions.onProfile(row.author.id) })
    }
    if (flags.focused) {
        Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            avatar()
            StatusHeader(row, now, flags.animateEmoji, { actions.onHistory(row) }, controls, Modifier.weight(1f))
        }
        StatusContent(row, policy, actions, flags, controls)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            avatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
                StatusHeader(row, now, flags.animateEmoji, { actions.onHistory(row) }, controls)
                StatusContent(row, policy, actions, flags, controls)
            }
        }
    }
}

/**
 * The card as one element for a screen reader, read as [label] with [actions] as its custom actions, and
 * opened by a tap through [onOpen]; the focused post passes none, since it is the one open.
 */
private fun Modifier.opens(label: String, actions: List<CustomAccessibilityAction>, onOpen: (() -> Unit)?): Modifier =
    (if (onOpen != null) clickable(onClick = onOpen) else this).clearAndSetSemantics {
        contentDescription = label
        customActions = actions
        if (onOpen != null) onClick { onOpen().let { true } }
    }

/** How a card is drawn, beyond the row it draws. */
private data class Flags(
    val showActions: Boolean,
    val canReact: Boolean,
    val animateEmoji: Boolean,
    val focused: Boolean,
)

/** Everything under the header: the warning, the body, the translation, the date, the actions. */
@Composable
private fun StatusContent(
    row: StatusRowUi,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    flags: Flags,
    controls: CardControls,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
        row.spoiler?.let { spoiler ->
            HiddenCard(
                HiddenEdge.Warning,
                AlohaIcons.ContentWarning,
                stringResource(
                    if (controls.spoilerRevealed) R.string.status_warning_rehide else R.string.status_filtered_show,
                ),
                controls.onSpoiler,
            ) {
                Text(
                    spoiler,
                    inlineContent = rememberEmojiContent(row.emojis, flags.animateEmoji),
                    style = MaterialTheme.typography.bodyMedium.contentDirection(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (row.spoiler == null || controls.spoilerRevealed) {
            StatusBody(row, policy, actions, flags.canReact, flags.animateEmoji, flags.focused, controls)
        }
        controls.translation?.let { TranslationLine(it, controls.onShowOriginal, controls.onGetLanguage) }
        if (flags.showActions) ActionRow(row, actions)
    }
}

@Composable
public fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = avatarShape()
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    }
}

/** A post a filter warns about, in its place: the filters' names, with their dotted edge. */
@Composable
private fun FilteredPlaceholder(titles: List<String>, onReveal: () -> Unit, modifier: Modifier) {
    HiddenCard(
        HiddenEdge.Filter,
        AlohaIcons.Filtered,
        stringResource(R.string.status_filtered_show),
        onReveal,
        modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
    ) {
        Text(
            stringResource(R.string.status_filtered, titles.joinToString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A filtered post shown anyway, its matched words painted in the error colours. */
@Composable
private fun withMatchesPainted(row: StatusRowUi, filtered: Boolean): StatusRowUi {
    if (!filtered || row.filterMatches.isEmpty()) return row
    val style = SpanStyle(
        color = MaterialTheme.colorScheme.onErrorContainer,
        background = MaterialTheme.colorScheme.errorContainer,
    )
    return remember(row, style) { row.copy(body = highlighted(row.body, row.filterMatches, style)) }
}

/** The four actions and the menu. Counts sit beside their button; the row itself reads as one element. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(row: StatusRowUi, actions: StatusActions) {
    // the menu keeps its place at the end; the actions spread across what is left and wrap where their
    // counts do not fit (a deep reply at a large font), so no count is ever cut short
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Actions(row, actions)
        }
        StatusMenu(row, actions)
    }
}

@Composable
private fun Actions(row: StatusRowUi, actions: StatusActions) {
    val semantic = LocalAlohaSemanticColors.current
    val tick = rememberTick()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ActionButton(AlohaIcons.Reply, row.counts.replies, muted) { actions.onReply(row) }
    ActionButton(
        if (row.state.boosted) AlohaIcons.Boosted else AlohaIcons.Boost,
        row.counts.boosts,
        if (row.state.boosted) semantic.boost else muted,
    ) {
        tick(!row.state.boosted)
        actions.onBoost(row)
    }
    ActionButton(
        if (row.state.favourited) AlohaIcons.Favourited else AlohaIcons.Favourite,
        row.counts.favourites,
        if (row.state.favourited) semantic.favourite else muted,
    ) {
        tick(!row.state.favourited)
        actions.onFavourite(row)
    }
    ActionButton(
        if (row.state.bookmarked) AlohaIcons.Bookmarked else AlohaIcons.Bookmark,
        null,
        if (row.state.bookmarked) semantic.bookmark else muted,
    ) {
        tick(!row.state.bookmarked)
        actions.onBookmark(row)
    }
    DislikeCount(row, muted)
}

/** PeerTube's thumbs-down, read-only: the server carries the count but has no route to cast one. */
@Composable
private fun DislikeCount(row: StatusRowUi, tint: Color) {
    val shown = row.counts.dislikes > 0 && LocalReadingStyle.current.showCounts && row.media.any { it.type.isPlayable }
    if (!shown) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AlohaIcons.Dislike, contentDescription = null, tint = tint, modifier = Modifier.size(SMALL_ICON))
        Text(
            row.counts.dislikes.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(start = AlohaSpacing.xxs),
        )
    }
}

/** A tick under the finger as a post changes: on, or off again. */
@Composable
private fun rememberTick(): (Boolean) -> Unit {
    val haptics = rememberHaptics()
    return { on -> haptics(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }
}

@Composable
private fun ActionButton(icon: ImageVector, count: Int?, tint: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val interactions = remember { MutableInteractionSource() }
        IconButton(onClick = onClick, interactionSource = interactions, modifier = Modifier.squish(interactions)) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Text(
            count?.takeIf { it > 0 && LocalReadingStyle.current.showCounts }?.toString().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusMenu(row: StatusRowUi, actions: StatusActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.status_action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menuItems(row, actions.menu).forEach { (item, icon, text) ->
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onMenu(row, item)
                    },
                )
            }
        }
    }
}

/** The screen's actions, with translating and its undo carried out here, for every screen alike. */
private class TranslatingActions(
    private val screen: StatusActions,
    private val translations: StatusTranslations,
    offered: Boolean,
    translation: TranslationUi?,
) : StatusActions by screen {
    private val added = buildSet {
        if (offered) add(StatusMenuItem.Translate)
        if (translation is TranslationUi.NeedsLanguage) add(StatusMenuItem.TranslationLanguage)
        if (translation != null) add(StatusMenuItem.ShowOriginal)
    }

    override val menu: Set<StatusMenuItem> get() = screen.menu - TRANSLATION_ITEMS + added

    override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = when (item) {
        StatusMenuItem.Translate -> translations.translate(row)
        StatusMenuItem.ShowOriginal -> translations.showOriginal(row.statusId)
        StatusMenuItem.TranslationLanguage -> translations.getLanguage()
        else -> screen.onMenu(row, item)
    }
}

private val TRANSLATION_ITEMS =
    setOf(StatusMenuItem.Translate, StatusMenuItem.ShowOriginal, StatusMenuItem.TranslationLanguage)

internal val AVATAR = 44.dp
internal val SMALL_ICON = 16.dp
