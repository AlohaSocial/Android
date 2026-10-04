// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import social.aloha.core.designsystem.badgeCount
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AppIntents

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(CORNER).padding(PAD),
    ) { content() }
}

/** A widget whose account is gone, or was never chosen. */
@Composable
private fun NoAccount() {
    Frame {
        Text(
            LocalContext.current.getString(R.string.widget_no_account),
            style = TextStyle(color = GlanceTheme.colors.onSurface),
        )
    }
}

@Composable
internal fun UnreadContent(account: SignedInAccount?, unread: Int) {
    if (account == null) return NoAccount()
    val context = LocalContext.current
    val spoken = context.resources.getQuantityString(R.plurals.widget_unread_spoken, unread, unread)
    // a small widget, or large text, has room for the count alone
    val roomy = LocalSize.current.height >= ROOMY
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(CORNER).padding(PAD)
            .clickable(actionStartActivity(AppIntents.open(context, account.id, statusId = null)))
            .semantics {
                contentDescription = context.getString(R.string.widget_for_account, spoken, account.qualifiedHandle)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            badgeCount(unread),
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
            maxLines = 1,
        )
        if (roomy) {
            Text(
                context.resources.getQuantityString(R.plurals.widget_unread, unread),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
            Text(
                account.qualifiedHandle,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ListContent(account: SignedInAccount?, title: Int, rows: List<WidgetRow>, empty: Int) {
    if (account == null) return NoAccount()
    val context = LocalContext.current
    val opens = actionStartActivity(AppIntents.open(context, account.id, statusId = null))
    Frame {
        // the title opens the app, as big as a finger needs
        Box(GlanceModifier.fillMaxWidth().height(TARGET).clickable(opens), contentAlignment = Alignment.CenterStart) {
            Text(
                context.getString(title),
                style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            )
        }
        if (rows.isEmpty()) {
            Text(
                context.getString(empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.fillMaxSize().clickable(opens),
            )
        } else {
            LazyColumn {
                items(rows, itemId = { it.statusId.hashCode().toLong() }) { row ->
                    Column(
                        GlanceModifier.fillMaxWidth().padding(vertical = GAP)
                            .clickable(actionStartActivity(AppIntents.open(context, account.id, row.statusId))),
                    ) {
                        Text(
                            row.name,
                            style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                            maxLines = 1,
                        )
                        Text(row.text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposeContent(account: SignedInAccount?) {
    if (account == null) return NoAccount()
    val context = LocalContext.current
    val label = context.getString(R.string.widget_compose_action)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(CORNER).padding(PAD)
            .clickable(actionStartActivity(AppIntents.compose(context, account.id)))
            .semantics {
                contentDescription = context.getString(R.string.widget_for_account, label, account.qualifiedHandle)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer))
        Text(
            account.qualifiedHandle,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onPrimaryContainer),
            maxLines = 1,
        )
    }
}

private val CORNER = 16.dp
private val TARGET = 48.dp
private val ROOMY = 100.dp
private val PAD = 12.dp
private val GAP = 4.dp
