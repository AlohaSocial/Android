// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.languageName

/** The server being typed: a card that says it is being looked up at once, and what it says about itself. */
@Composable
internal fun PreviewCard(preview: ServerPreview) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            when (preview) {
                is ServerPreview.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
                ) {
                    CircularProgressIndicator(Modifier.size(SPINNER), strokeWidth = 2.dp)
                    Text(stringResource(R.string.signin_preview_loading, preview.host))
                }

                // said once it is found, not at every key typed while it is looked up
                is ServerPreview.Shown -> Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
                ) {
                    InstanceFacts(preview.card)
                    if (preview.card.description.isNotBlank()) {
                        Text(
                            preview.card.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = DESCRIPTION_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** An invite link copied before the app opened: where it leads, opening it, or leaving it. */
@Composable
internal fun InviteCard(invite: String, actions: SignInActions) {
    val host = invite.toHttpUrlOrNull()?.host ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
            Text(stringResource(R.string.signin_invite, host), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    actions.onInvite(use = false)
                }) { Text(stringResource(R.string.signin_invite_dismiss)) }
                TextButton(onClick = {
                    actions.onInvite(use = true)
                }) { Text(stringResource(R.string.signin_invite_use)) }
            }
        }
    }
}

/** [codes] as the reader's language names them, joined; null for none. */
internal fun languageNames(codes: List<String>): String? =
    codes.mapNotNull(::languageName).distinct().takeIf { it.isNotEmpty() }?.joinToString()

/**
 * An invite link on the clipboard, read only where reading it says nothing on screen for what is not a
 * link: from Android 12 the system tells of every read, so a clip it does not see a link in is left alone.
 */
internal fun inviteOnClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val description = clipboard.primaryClipDescription ?: return null
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !looksLikeLink(description)) return null
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let(::inviteOf)
}

private fun looksLikeLink(description: ClipDescription): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
    description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
    description.getConfidenceScore(TextClassifier.TYPE_URL) >= LINK_CONFIDENCE

/** [text] when it is a Mastodon invite link, `https://server/invite/code`; else null. */
internal fun inviteOf(text: String): String? = text.trim().takeIf { INVITE.matches(it) }

private val INVITE = Regex("""^https://[^/\s]+\.[^/\s]+/invite/[A-Za-z0-9_-]+/?$""")
private const val LINK_CONFIDENCE = 0.5f
private const val DESCRIPTION_LINES = 3
private val SPINNER = 20.dp
