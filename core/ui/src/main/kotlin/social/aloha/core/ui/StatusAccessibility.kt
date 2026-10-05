// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.LinkAnnotation
import java.time.Instant
import social.aloha.core.designsystem.AlohaIcons

internal fun menuItems(
    row: StatusRowUi,
    available: Set<StatusMenuItem> = StatusMenuItem.entries.toSet(),
): List<Triple<StatusMenuItem, ImageVector, Int>> = buildList {
    add(Triple(StatusMenuItem.Share, AlohaIcons.Share, R.string.status_menu_share))
    if (row.url != null) {
        add(Triple(StatusMenuItem.CopyLink, AlohaIcons.CopyLink, R.string.status_menu_copy_link))
        add(Triple(StatusMenuItem.OpenInBrowser, AlohaIcons.OpenInBrowser, R.string.status_menu_open_in_browser))
    }
    add(Triple(StatusMenuItem.OpenInNewWindow, AlohaIcons.NewWindow, R.string.status_menu_new_window))
    add(Triple(StatusMenuItem.Translate, AlohaIcons.Translate, R.string.status_menu_translate))
    add(Triple(StatusMenuItem.ShowOriginal, AlohaIcons.Translate, R.string.status_show_original))
    add(Triple(StatusMenuItem.TranslationLanguage, AlohaIcons.Translate, R.string.status_translation_get_language))
    val mute = if (row.state.muted) R.string.status_menu_unmute_conversation else R.string.status_menu_mute_conversation
    add(Triple(StatusMenuItem.MuteConversation, AlohaIcons.MuteConversation, mute))
    if (row.isOwn) {
        add(
            Triple(
                StatusMenuItem.Pin,
                AlohaIcons.Pinned,
                if (row.state.pinned) R.string.status_menu_unpin else R.string.status_menu_pin,
            ),
        )
        // only the author's own posts with pictures go into their albums
        if (row.media.isNotEmpty()) {
            add(Triple(StatusMenuItem.AddToAlbum, AlohaIcons.Album, R.string.status_menu_add_to_album))
        }
        add(Triple(StatusMenuItem.Archive, AlohaIcons.Archived, R.string.status_menu_archive))
        add(Triple(StatusMenuItem.Edit, AlohaIcons.Edited, R.string.status_menu_edit))
        add(Triple(StatusMenuItem.Redraft, AlohaIcons.Redraft, R.string.status_menu_redraft))
        add(Triple(StatusMenuItem.Delete, AlohaIcons.Delete, R.string.status_menu_delete))
    } else {
        // report, block and mute are never more than two taps away
        add(Triple(StatusMenuItem.Report, AlohaIcons.Report, R.string.status_menu_report))
    }
}.filter { it.first in available }

/** Who, when and what was said; the content warning instead of the body while it is closed. */
@Composable
internal fun accessibilityLabel(row: StatusRowUi, now: Instant, bodyShown: Boolean): String {
    val age = rememberPostTime(row.createdAt, now).second
    val spoiler = row.spoiler
    if (spoiler != null &&
        !bodyShown
    ) {
        return stringResource(R.string.status_accessibility_spoiler, row.author.plainName, age, spoiler.text)
    }
    val context = row.context?.let { contextText(it, now, spoken = true) }
    val media = row.media.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.status_media_count, it, it) }
    val poll = if (row.poll != null) stringResource(R.string.status_poll) else null
    val quote = row.quote?.let { stringResource(R.string.status_quote, it.author.plainName) + ": " + it.excerpt.text }
    val card = row.card?.let { it.title.ifBlank { it.url.orEmpty() } }?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        context,
        stringResource(R.string.status_accessibility_header, row.author.plainName, age),
        row.plainText.takeIf {
            it.isNotBlank()
        },
        media,
        poll,
        quote,
        card,
    )
        .joinToString(". ")
}

/** The line above a post, as shown or, [spoken], as a screen reader says it; a boost's own age included. */
@Composable
internal fun contextText(context: StatusRowUi.ContextLine, now: Instant, spoken: Boolean = false): String =
    when (context) {
        is StatusRowUi.ContextLine.BoostedBy -> listOfNotNull(
            stringResource(R.string.status_context_boosted, context.name),
            context.at?.let {
                val age = PostAge.of(it, now)
                val justNow = stringResource(R.string.status_age_now)
                if (spoken) age.spoken(justNow) else age.short(justNow)
            },
            context.reply?.let { contextText(it, now, spoken) },
        ).joinToString(if (spoken) ". " else " · ")

        StatusRowUi.ContextLine.Pinned -> stringResource(R.string.status_context_pinned)

        StatusRowUi.ContextLine.ContinuedThread -> stringResource(R.string.status_context_thread)

        is StatusRowUi.ContextLine.ReplyingTo -> stringResource(R.string.status_context_replying_to, context.handle)

        StatusRowUi.ContextLine.Replying -> stringResource(R.string.status_context_replying)
    }

/**
 * What a card's own controls hold, which a screen reader reaches through [customActions] as well:
 * whether the content warning is open, the poll options chosen before voting, and the translation shown.
 */
internal data class CardControls(
    val spoilerRevealed: Boolean = false,
    val onSpoiler: () -> Unit = {},
    val pollChoice: List<Int> = emptyList(),
    val onPollChoice: (List<Int>) -> Unit = {},
    val translation: TranslationUi? = null,
    val onShowOriginal: () -> Unit = {},
    val onGetLanguage: () -> Unit = {},
    val collapse: Collapse = Collapse(),
    val onRehide: (() -> Unit)? = null,
)

/**
 * The actions a screen reader offers on the row: opening the content warning, the four, the profile,
 * each link, each attachment, each poll option and the vote, the quote, the link card, and the menu.
 */
@Composable
internal fun customActions(
    row: StatusRowUi,
    actions: StatusActions,
    controls: CardControls,
): List<CustomAccessibilityAction> {
    fun action(label: String, block: () -> Unit) = CustomAccessibilityAction(label) { block().let { true } }
    val base = toggleActions(row, actions)
    val links = row.body.getLinkAnnotations(0, row.body.length).map { range ->
        val text = row.body.substring(range.start, range.end)
        action(text) {
            actions.onLink(RichLinkTarget.parse((range.item as LinkAnnotation.Url).url))
        }
    }.distinctBy { it.label }
    val media = row.media.indices.map { index ->
        action(
            row.media[index].description?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.status_media_no_description),
        ) { actions.onMedia(row, index) }
    }
    val menu = menuItems(row, actions.menu).map { (item, _, text) ->
        action(stringResource(text)) { actions.onMenu(row, item) }
    }
    val spoiler = spoilerActions(row, controls)
    val history = listOfNotNull(
        if (row.edited) action(stringResource(R.string.status_action_history)) { actions.onHistory(row) } else null,
        controls.onRehide?.let { action(stringResource(R.string.status_rehide), it) },
    )
    // behind a closed content warning, only opening it is offered: the rest is not on screen
    if (row.spoiler != null && !controls.spoilerRevealed) return spoiler + base + history + menu
    return spoiler + base + links + media + pollActions(row, actions, controls) + attachedActions(row, actions) +
        history + menu
}

/** Choosing each option, then voting, while the poll takes votes. */
@Composable
private fun pollActions(
    row: StatusRowUi,
    actions: StatusActions,
    controls: CardControls,
): List<CustomAccessibilityAction> {
    val poll = row.poll?.takeIf { !it.showsResults } ?: return emptyList()
    val choices = poll.options.mapIndexed { index, option ->
        val chosen = index in controls.pollChoice
        val label =
            stringResource(if (chosen) R.string.status_action_unchoose else R.string.status_action_choose, option.title)
        CustomAccessibilityAction(label) {
            true.also { controls.onPollChoice(toggled(controls.pollChoice, index, poll.multiple)) }
        }
    }
    val vote = if (controls.pollChoice.isNotEmpty()) {
        listOf(
            CustomAccessibilityAction(stringResource(R.string.status_poll_vote)) {
                true.also { actions.onVote(row, controls.pollChoice) }
            },
        )
    } else {
        emptyList()
    }
    return choices + vote
}

/** The quoted post and the link card, each opened as a tap on it would. */
@Composable
private fun attachedActions(row: StatusRowUi, actions: StatusActions): List<CustomAccessibilityAction> = listOfNotNull(
    row.quote?.let { quote ->
        CustomAccessibilityAction(stringResource(R.string.status_action_open_quote)) {
            true.also { actions.onOpen(quote.statusId) }
        }
    },
    row.card?.url?.let { url ->
        val title = row.card.title.ifBlank { url }
        val label = if (row.card.playable) R.string.status_action_play_card else R.string.status_action_open_card
        val context = LocalContext.current
        CustomAccessibilityAction(stringResource(label, title)) {
            true.also { openCard(context, row.card, actions) }
        }
    },
)

@Composable
private fun countLabel(action: String, count: Int): String = if (count >
    0
) {
    stringResource(R.string.status_action_count, action, count)
} else {
    action
}

/** Reply, boost, favourite and bookmark, with their counts and state, and the author's profile. */
@Composable
private fun toggleActions(row: StatusRowUi, actions: StatusActions): List<CustomAccessibilityAction> {
    fun action(label: String, block: () -> Unit) = CustomAccessibilityAction(label) { block().let { true } }
    return listOfNotNull(
        action(countLabel(stringResource(R.string.status_action_reply), row.counts.replies)) { actions.onReply(row) },
        action(
            countLabel(
                stringResource(if (row.state.boosted) R.string.status_action_unboost else R.string.status_action_boost),
                row.counts.boosts,
            ),
        ) {
            actions.onBoost(row)
        },
        action(stringResource(quoteLabel(row.quoteAccess))) { actions.onQuote(row) }
            .takeIf { actions.quotes && row.quoteAccess != QuoteAccess.Denied },
        action(
            countLabel(
                stringResource(
                    if (row.state.favourited) R.string.status_action_unfavourite else R.string.status_action_favourite,
                ),
                row.counts.favourites,
            ),
        ) {
            actions.onFavourite(row)
        },
        action(
            stringResource(
                if (row.state.bookmarked) R.string.status_action_unbookmark else R.string.status_action_bookmark,
            ),
        ) {
            actions.onBookmark(row)
        },
        action(stringResource(R.string.status_action_profile)) { actions.onProfile(row.author.id) },
    )
}

/** Opening or closing the content warning, when there is one. */
@Composable
private fun spoilerActions(row: StatusRowUi, controls: CardControls): List<CustomAccessibilityAction> {
    if (row.spoiler == null) return emptyList()
    val label =
        stringResource(if (controls.spoilerRevealed) R.string.status_spoiler_hide else R.string.status_spoiler_show)
    return listOf(CustomAccessibilityAction(label) { true.also { controls.onSpoiler() } })
}
