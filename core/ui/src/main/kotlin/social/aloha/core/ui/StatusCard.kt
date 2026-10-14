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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.LocalAlohaSemanticColors
import social.aloha.core.model.SensitiveMediaPolicy

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

    /** A quote of [row], or the request for one, in the composer; offered only where [quotes]. */
    public fun onQuote(row: StatusRowUi) {}

    /** Whether this screen quotes: then a tap on boost asks whether to boost or to quote. */
    public val quotes: Boolean get() = false

    /** The edits of [row], from its "edited" mark; without a screen of its own for them, the post. */
    public fun onHistory(row: StatusRowUi): Unit = onOpen(row.statusId)

    /** The menu items this screen can carry out; the others are not offered. */
    public val menu: Set<StatusMenuItem> get() = StatusMenuItem.entries.toSet()

    /** The menu items this screen can carry out on [row], where rows differ in what can be done to them. */
    public fun menuFor(row: StatusRowUi): Set<StatusMenuItem> = menu
}

/**
 * One of the reader's accounts that sees a post, where every account shows together: its face, ringed in
 * its own [colour] where it has one, and its handle for a screen reader.
 */
@Immutable
public data class SeenBy(val handle: String, val avatarUrl: String?, val colour: Int?)

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
 * @param pictureFirst for a feed of pictures: the content the full width of the card, its pictures
 *   before its text.
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
    seenBy: List<SeenBy> = emptyList(),
    pictureFirst: Boolean = false,
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
    val label = listOfNotNull(
        accessibilityLabel(row, now, bodyShown),
        translation?.let { translationText(it) },
        seenByText(seenBy),
    ).joinToString(". ")
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
    var askedAs by remember { mutableStateOf<AskedAs?>(null) }
    val customActions = customActions(row, actions, controls) + actAsActions(row) { askedAs = it }
    askedAs?.let { ActAsPicker(row, it) { askedAs = null } }
    val compact = LocalReadingStyle.current.compact
    ProvideLinkRouting(onLink = actions::onLink) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .opens(label, customActions, onOpen = { actions.onOpen(row.statusId) }.takeUnless { focused })
                .padding(horizontal = AlohaSpacing.m, vertical = if (compact) AlohaSpacing.xs else AlohaSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
        ) {
            ReadingTextSize {
                row.context?.let { ContextLineRow(it, now) }
                CardLayout(
                    row,
                    now,
                    policy,
                    actions,
                    Flags(showActions, canReact, animateEmoji, focused, pictureFirst),
                    controls,
                    seenBy,
                )
            }
        }
    }
}

/** Which of the reader's accounts see the post, said for a screen reader; null where none is marked. */
@Composable
private fun seenByText(seenBy: List<SeenBy>): String? = seenBy.takeIf { it.isNotEmpty() }
    ?.let { stringResource(R.string.status_seen_by, it.joinToString(", ") { account -> account.handle }) }

/**
 * The avatar beside the header, and the content under the name, so text, media, poll and cards hang in
 * one column; a focused post, and one whose pictures come first, take the full width under the header
 * instead.
 */
@Composable
private fun CardLayout(
    row: StatusRowUi,
    now: Instant,
    policy: SensitiveMediaPolicy,
    actions: StatusActions,
    flags: Flags,
    controls: CardControls,
    seenBy: List<SeenBy>,
) {
    val avatar = @Composable {
        // which of the reader's accounts see the post sits on the author's picture, at its corner
        Box {
            Avatar(row.author.avatarUrl, AVATAR, Modifier.clickable { actions.onProfile(row.author.id) })
            if (seenBy.isNotEmpty()) {
                val outline = MaterialTheme.colorScheme.outline
                StackedAvatars(
                    seenBy.map { it.avatarUrl },
                    SEEN_BY,
                    Modifier.align(Alignment.BottomEnd).offset(x = SEEN_BY_OUT, y = SEEN_BY_OUT),
                    rings = seenBy.map { account -> account.colour?.let(::Color) ?: outline },
                )
            }
        }
    }
    if (flags.focused || flags.pictureFirst) {
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
    val pictureFirst: Boolean,
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
        row.spoiler?.let { Warning(it, row, flags.animateEmoji, controls) }
        val shown = row.spoiler == null || controls.spoilerRevealed
        val actionRow = @Composable { if (flags.showActions) ActionRow(row, actions) }
        // where the pictures come first, the actions follow them and the words come last, as in a photo app
        val underPictures = flags.pictureFirst && shown && row.media.isNotEmpty()
        if (shown) {
            StatusBody(
                row,
                policy,
                actions,
                flags.canReact,
                flags.animateEmoji,
                flags.focused,
                controls,
                underPictures = actionRow.takeIf { underPictures },
            )
        }
        controls.translation?.let { TranslationLine(it, controls.onShowOriginal, controls.onGetLanguage) }
        if (!underPictures) actionRow()
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
    ActAsAction(row, PostAct.Reply, R.string.status_reply_as) { long ->
        ActionButton(AlohaIcons.Reply, row.counts.replies, muted, long) { actions.onReply(row) }
    }
    // where the screen quotes, the tick waits for the menu's choice
    val boosting = remember(row, actions) {
        object : StatusActions by actions {
            override fun onBoost(row: StatusRowUi) {
                tick(!row.state.boosted)
                actions.onBoost(row)
            }
        }
    }
    var boostingWith by remember { mutableStateOf(false) }
    val boostWith = { boostingWith = true }.takeIf { LocalActAs.current != null && row.url != null }
    Box {
        BoostButton(row, boosting) { onClick ->
            ActionButton(
                if (row.state.boosted) AlohaIcons.Boosted else AlohaIcons.Boost,
                row.counts.boosts,
                if (row.state.boosted) semantic.boost else muted,
                boostWith,
                onClick,
            )
        }
        BoostWithMenu(row, actions, boostingWith) { boostingWith = false }
    }
    ActAsAction(row, PostAct.Favourite, R.string.status_favourite_as) { long ->
        ActionButton(
            if (row.state.favourited) AlohaIcons.Favourited else AlohaIcons.Favourite,
            row.counts.favourites,
            if (row.state.favourited) semantic.favourite else muted,
            long,
        ) {
            tick(!row.state.favourited)
            actions.onFavourite(row)
        }
    }
    ActAsAction(row, PostAct.Bookmark, R.string.status_bookmark_as) { long ->
        ActionButton(
            if (row.state.bookmarked) AlohaIcons.Bookmarked else AlohaIcons.Bookmark,
            null,
            if (row.state.bookmarked) semantic.bookmark else muted,
            long,
        ) {
            tick(!row.state.bookmarked)
            actions.onBookmark(row)
        }
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
private fun ActionButton(
    icon: ImageVector,
    count: Int?,
    tint: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onLongClick != null) {
            PressableIcon(icon, tint, onClick, onLongClick)
        } else {
            val interactions = remember { MutableInteractionSource() }
            IconButton(onClick = onClick, interactionSource = interactions, modifier = Modifier.squish(interactions)) {
                Icon(icon, contentDescription = null, tint = tint)
            }
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
            menuItems(row, actions.menuFor(row)).forEach { (item, icon, text) ->
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

    override fun menuFor(row: StatusRowUi): Set<StatusMenuItem> = screen.menuFor(row) - TRANSLATION_ITEMS + added

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
private val SEEN_BY = 18.dp
private val SEEN_BY_OUT = 4.dp
internal val SMALL_ICON = 16.dp
