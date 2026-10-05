// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.text.Html
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Status
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusMenuItem
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi

/**
 * The post as its readers will see it, each part of a thread a card, its content warning folded. Its text
 * is as written; the server links mentions, hashtags and addresses once it is posted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewSheet(state: ComposerUiState, segments: List<String>, spoiler: String, onDismiss: () -> Unit) {
    val colors = RichTextColors.fromTheme()
    val rows = remember(state, segments, spoiler, colors) {
        val mapper = StatusRowMapper(RichTextCache(), colors)
        segments.indices.map { mapper.map(previewOf(state, segments, spoiler, it), viewerAccountId = null) }
    }
    val now = remember { Instant.now() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.composer_preview),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = AlohaSpacing.l)) {
            rows.forEachIndexed { index, row ->
                // shown for its looks: a screen reader hears what it says, and no actions that do nothing
                val said = listOfNotNull(spoiler.takeIf { state.spoilerShown && it.isNotBlank() }, segments[index])
                    .joinToString(". ")
                Box(Modifier.clearAndSetSemantics { contentDescription = said }) {
                    StatusCard(row, now, LocalSensitiveMediaPolicy.current, Inert, showActions = false)
                }
            }
        }
    }
}

/** Part [index] of what is being written, as a post of the writer's own. */
internal fun previewOf(state: ComposerUiState, segments: List<String>, spoiler: String, index: Int): Status {
    val author = state.author
    val handle = author?.handle.orEmpty().removePrefix("@")
    return Status(
        id = "preview-$index",
        account = Account(
            id = author?.id.orEmpty(),
            username = handle.substringBefore('@'),
            acct = handle,
            displayName = author?.name.orEmpty(),
            avatar = author?.avatarUrl,
        ),
        createdAt = Instant.now(),
        content = segments[index].split("\n\n").joinToString("") { paragraph ->
            "<p>" + Html.escapeHtml(paragraph).replace("\n", "<br>") + "</p>"
        },
        spoilerText = if (state.spoilerShown) spoiler else "",
        visibility = state.visibility,
        sensitive = state.mediaSensitive,
        language = state.language,
        mediaAttachments = state.attachments.getOrElse(index) { emptyList() }.map { attachment ->
            MediaAttachment(
                attachment.id,
                when {
                    attachment.isVideo -> AttachmentKind.Video
                    attachment.isPicture -> AttachmentKind.Image
                    else -> AttachmentKind.UnsupportedFile
                },
                url = attachment.file?.toURI()?.toString() ?: attachment.previewUrl,
                previewUrl = attachment.file?.toURI()?.toString() ?: attachment.previewUrl,
                description = attachment.description,
            )
        },
    )
}

/** A card shown for its looks alone: nothing on it does anything. */
private object Inert : StatusActions {
    override fun onOpen(statusId: String) = Unit

    override fun onProfile(accountId: String) = Unit

    override fun onLink(target: RichLinkTarget) = Unit

    override fun onMedia(row: StatusRowUi, index: Int) = Unit

    override fun onReply(row: StatusRowUi) = Unit

    override fun onBoost(row: StatusRowUi) = Unit

    override fun onFavourite(row: StatusRowUi) = Unit

    override fun onBookmark(row: StatusRowUi) = Unit

    override fun onVote(row: StatusRowUi, choices: List<Int>) = Unit

    override fun onReact(row: StatusRowUi, name: String, add: Boolean) = Unit

    override fun onMenu(row: StatusRowUi, item: StatusMenuItem) = Unit
}
