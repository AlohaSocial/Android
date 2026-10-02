// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import social.aloha.core.data.previewText
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.WidgetFeed

/**
 * How many unread notifications an account has, from what the last poll found. What it shows is read
 * inside the widget's content, so a redraw that finds the content still running shows the new count.
 */
internal class UnreadWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (account, _) = context.widgetAccount(id)
        val feed = account?.let { context.widgetEntryPoint().widgets().feed(it.id) } ?: flowOf(WidgetFeed())
        provideContent {
            val shown by feed.collectAsState(WidgetFeed())
            GlanceTheme { UnreadContent(account, shown.unread) }
        }
    }
}

/** An account's newest mentions, from the last notifications page the app read. */
internal class MentionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (account, _) = context.widgetAccount(id)
        val feed = account?.let { context.widgetEntryPoint().widgets().feed(it.id) } ?: flowOf(WidgetFeed())
        val locked = context.widgetEntryPoint().lock().enabled
        provideContent {
            val shown by feed.collectAsState(WidgetFeed())
            val behindLock by locked.collectAsState(true)
            val rows = remember(shown, behindLock) {
                if (behindLock) emptyList() else shown.mentions.map { WidgetRow(it.statusId, it.name, it.text) }
            }
            val empty = if (behindLock) R.string.widget_locked else R.string.widget_mentions_empty
            GlanceTheme { ListContent(account, R.string.widget_mentions_title, rows, empty) }
        }
    }
}

/** The newest posts of one of an account's timelines, as the app last cached them; only those are read. */
internal class LatestPostsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (account, settings) = context.widgetAccount(id)
        val posts = account?.let {
            context.widgetEntryPoint().timelines()
                .observe(it, TimelineKey.home(sourceOf(settings[WidgetKeys.SOURCE])), limit = ROWS)
                .map { rows ->
                    rows.filterIsInstance<TimelineRow.Post>().take(POSTS).map { row ->
                        val shown = row.status.displayed
                        WidgetRow(row.status.id, shown.account.bestDisplayName, shown.previewText().orEmpty())
                    }
                }
        } ?: flowOf(emptyList())
        val locked = context.widgetEntryPoint().lock().enabled
        provideContent {
            val shown by posts.collectAsState(emptyList())
            // behind the app lock no post shows on the home screen; until the setting is read, none either
            val behindLock by locked.collectAsState(true)
            val rows = if (behindLock) emptyList() else shown
            val empty = if (behindLock) R.string.widget_locked else R.string.widget_latest_empty
            GlanceTheme { ListContent(account, R.string.widget_latest_title, rows, empty) }
        }
    }

    private companion object {
        const val POSTS = 10

        // a few more than are shown, for the gaps among them
        const val ROWS = 15
    }
}

/** One tap to a new post by an account. */
internal class ComposeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (account, _) = context.widgetAccount(id)
        provideContent { GlanceTheme { ComposeContent(account) } }
    }
}

internal class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}

internal class MentionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MentionsWidget()
}

internal class LatestPostsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestPostsWidget()
}

internal class ComposeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ComposeWidget()
}
