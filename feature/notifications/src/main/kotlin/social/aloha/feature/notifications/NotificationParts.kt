// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.NotificationKind
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.MediaImage

/**
 * Up to six faces of who did it, as many as the width holds, each a way to their profile. A screen
 * reader finds those ways among the row's actions (see [profileActions]) rather than one stop per face.
 */
@Composable
internal fun Faces(people: List<NotificationRowUi.Person>, onProfile: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        val fit = (maxWidth / FACE_TARGET).toInt().coerceAtLeast(1)
        Row {
            people.take(minOf(FACES, fit)).forEach { person ->
                IconButton(onClick = { onProfile(person.id) }) { Avatar(person.avatarUrl, FACE) }
            }
        }
    }
}

/** Opening each face's profile, as a row's custom actions for a screen reader. */
@Composable
internal fun profileActions(people: List<NotificationRowUi.Person>, onProfile: (String) -> Unit) =
    people.take(FACES).map { person ->
        CustomAccessibilityAction(stringResource(R.string.notifications_profile_of, person.name)) {
            onProfile(person.id)
            true
        }
    }

/** The summary, with the newest name set in medium weight and a tap on it opening their profile. */
internal fun summaryWithName(
    summary: String,
    name: String?,
    accountId: String?,
    onProfile: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(summary)
    val shown = name?.takeIf { it.isNotEmpty() } ?: return@buildAnnotatedString
    val at = summary.indexOf(shown)
    if (at < 0 || accountId == null) return@buildAnnotatedString
    addStyle(SpanStyle(fontWeight = FontWeight.Medium), at, at + shown.length)
    addLink(LinkAnnotation.Clickable("profile", TextLinkStyles()) { onProfile(accountId) }, at, at + shown.length)
}

/** A mention or a reply as a small card: its text at more length than a preview, and its first picture. */
@Composable
internal fun CompactPost(row: NotificationRowUi) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(AlohaSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            row.preview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = CARD_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            row.media?.let { media ->
                MediaImage(
                    media,
                    media.description,
                    Modifier.fillMaxWidth().heightIn(max = CARD_MEDIA).clip(MaterialTheme.shapes.small),
                    fitToAspect = false,
                )
            }
        }
    }
}

/** Follows cut, or a moderator's warning, as a card with the reason and the way to the server's page. */
@Composable
internal fun NoticeCard(row: NotificationRowUi, origin: String?, onLearnMore: (String) -> Unit) {
    val (text, link) = when {
        row.severance != null -> severanceText(row.severance.type, row.severance.targetName) to
            origin?.let { "$it/severed_relationships" }

        row.warning != null -> listOf(
            stringResource(R.string.notifications_warning),
            row.warning.text.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("\n") to origin?.let { "$it/disputes/strikes/${row.warning.id}" }

        else -> return
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(start = AlohaSpacing.s, top = AlohaSpacing.s, end = AlohaSpacing.s)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            link?.let {
                TextButton(onClick = { onLearnMore(it) }) { Text(stringResource(R.string.notifications_learn_more)) }
            }
        }
    }
}

@Composable
private fun severanceText(type: String, target: String): String = stringResource(
    when (type) {
        "domain_block" -> R.string.notifications_severed_domain
        "user_domain_block" -> R.string.notifications_severed_user_domain
        "account_suspension" -> R.string.notifications_severed_suspension
        else -> R.string.notifications_severed_other
    },
    target,
)

/** Accept and Decline, on a follow request's own row. */
@Composable
internal fun RequestButtons(onAnswer: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        FilledTonalButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.notifications_request_accept)) }
        OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.notifications_request_decline)) }
    }
}

/** The first row while filtered notifications wait: how many people, and the way to them. */
@Composable
internal fun RequestsRow(pending: Int, onRequests: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onRequests),
        leadingContent = { Icon(AlohaIcons.Filtered, contentDescription = null, modifier = Modifier.size(KIND_ICON)) },
        headlineContent = { Text(stringResource(R.string.notifications_filtered_row)) },
        supportingContent = { Text(pluralStringResource(R.plurals.notifications_filtered_people, pending, pending)) },
    )
}

/** The kinds whose post shows as a compact card rather than a preview line. */
internal val CARD_KINDS = setOf(NotificationKind.Mention, NotificationKind.Update)

private val FACE_TARGET = 48.dp
private const val FACES = 6
private const val CARD_LINES = 6
private val FACE = 28.dp
private val CARD_MEDIA = 160.dp
private val KIND_ICON = 24.dp
