// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import social.aloha.core.datastore.NotificationPreferences
import social.aloha.core.datastore.WidgetFeedStore
import social.aloha.core.model.MentionSnippet
import social.aloha.core.model.WidgetFeed

/**
 * What the home screen widgets show, kept where they read it without the network, and the redraw that
 * follows a change. A redraw reaches every widget of the app on a home screen, whichever kind it is.
 * While notifications come as a digest, a change is stored but drawn only when the digest is, so the
 * widgets prompt no more often than the notifications do.
 */
@Singleton
public class WidgetUpdates @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: WidgetFeedStore,
    private val preferences: NotificationPreferences? = null,
) {
    public fun feed(accountId: String): Flow<WidgetFeed> = store.feed(accountId)

    public suspend fun setUnread(accountId: String, count: Int) {
        if (store.update(accountId) { it.copy(unread = count) }) redrawUnlessHeld()
    }

    public suspend fun setMentions(accountId: String, mentions: List<MentionSnippet>) {
        if (store.update(accountId) { it.copy(mentions = mentions) }) redrawUnlessHeld()
    }

    private suspend fun redrawUnlessHeld() {
        if (preferences?.digest?.first() == null) redraw()
    }

    public suspend fun forget(accountId: String) {
        store.forget(accountId)
        redraw()
    }

    /** Asks the app's widgets to draw again; nothing is sent while none is on a home screen. */
    public fun redraw() {
        val manager = AppWidgetManager.getInstance(context) ?: return
        manager.getInstalledProvidersForPackage(context.packageName, null).forEach { provider ->
            val ids = manager.getAppWidgetIds(provider.provider)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(provider.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }
}
