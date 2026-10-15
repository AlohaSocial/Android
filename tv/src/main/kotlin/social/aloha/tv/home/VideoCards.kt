// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.home

import androidx.compose.runtime.Immutable
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.media.BlurHash
import social.aloha.core.model.Account
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.ContentKind
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Status
import social.aloha.core.model.VideoSource
import social.aloha.core.model.VideoSources

/** A video on a shelf or in a grid: what its card shows, and what plays when it is previewed. */
@Immutable
internal data class VideoCardUi(
    val id: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    /** Marked sensitive and the reader's policy covers it: no picture, no preview, until it is opened. */
    val covered: Boolean,
    /** The colour of its blurhash, drawn under a covered card where the policy blurs rather than hides. */
    val coverColour: Int?,
    val short: Boolean,
    /** How far the reader got, between 0 and 1; null for a video not started. */
    val progress: Float?,
    /** Where a muted preview plays from, best first; empty for a covered video. */
    val preview: List<VideoSource>,
)

/** Someone the reader follows, as a channel to open. */
@Immutable
internal data class PersonCardUi(val id: String, val name: String, val handle: String, val avatar: String?)

/**
 * [status], or the post it boosts, as a card; null for a post without a video. [policy] is the reader's own
 * choice for sensitive media, so a covered video shows nothing of itself before it is opened.
 */
internal fun Status.toCard(apiBase: String, policy: SensitiveMediaPolicy, progress: Double?): VideoCardUi? {
    val shown = displayed
    val clip = shown.mediaAttachments.firstOrNull { it.type == AttachmentKind.Video || it.type == AttachmentKind.Gifv }
        ?: return null
    val covered = shown.sensitive && !policy.allowsAutomaticReveal
    return VideoCardUi(
        id = shown.id,
        title = shown.video?.title ?: StatusHtmlParser.firstLine(shown.content).orEmpty(),
        author = shown.account.bestDisplayName,
        thumbnail = clip.previewUrl.takeUnless { covered },
        covered = covered,
        coverColour = clip.blurhash?.takeIf { covered && policy.drawsAtAll }?.let(BlurHash::averageColour),
        short = ContentClassifier.classify(shown) == ContentKind.Short,
        progress = progress?.toFloat(),
        preview = if (covered) emptyList() else VideoSources.ladder(this, clip, apiBase),
    )
}

/** Whether [status] is a video or a short, the only posts a TV shows. */
internal fun isVideo(status: Status): Boolean =
    ContentClassifier.classify(status.displayed).let { it == ContentKind.Video || it == ContentKind.Short }

internal fun Account.toPerson() = PersonCardUi(id, bestDisplayName, acct, avatarStatic ?: avatar)
